package com.tmtn.app.ui.onboarding

import androidx.compose.runtime.mutableStateOf
import com.google.gson.JsonParser
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.TokenHolder
import com.tmtn.app.network.model.ConsentRequest
import com.tmtn.app.network.model.EmailVerificationConfirmRequest
import com.tmtn.app.network.model.EmailVerificationRequestRequest
import com.tmtn.app.network.model.ExerciseHabitsRequest
import com.tmtn.app.network.model.HealthInputRequest
import com.tmtn.app.network.model.LoginRequest
import com.tmtn.app.network.model.OnboardingScheduleRequest
import com.tmtn.app.network.model.UserUpdateRequest
import retrofit2.Response

enum class OnboardingStep {
    A01_SPLASH, A02_START, A03_SIGNUP, A04_VERIFY, A05_LOGIN,
    A06_CONSENT, A07_PROFILE, A08_EXERCISE, A09_SCHEDULE_INTRO, A10_SCHEDULE,
    A11_VERIFY_RETRY, A12_PASSWORD_RESET_REQUEST, A13_NEW_PASSWORD, A14_TERMS_DETAIL,
    A15_COMPLETE, A16_PERMISSIONS,
    DONE
}

/**
 * 서버 에러 응답(response.errorBody())에서 "실제 이유"를 최대한 사람이 읽을 수 있게 뽑아냄.
 *
 * ⚠️ 예전엔 항상 "인증번호가 올바르지 않아요 (422)"처럼 고정 문구 + 상태코드만 보여줘서,
 * 실제로는 비밀번호 조건 미달 같은 다른 이유로 실패해도 사용자가 원인을 알 방법이 없었음
 * (팀원이 이것 때문에 계속 인증번호만 재요청하던 실제 사례 있었음).
 *
 * FastAPI 에러 응답은 두 가지 형태임:
 *  - 일반 에러(400/404/409 등): {"detail": "문자열 메시지"}
 *  - 필드 검증 실패(422): {"detail": [{"msg": "Value error, 문구", ...}, ...]} (리스트)
 * 두 형태 다 처리해서 사람이 읽을 문구만 뽑아냄.
 */
// public으로 열어둠 — 카드/댐 화면(다른 패키지)에서도 같은 함수를 재사용하기 위함.
fun parseErrorMessage(response: Response<*>): String {
    val fallback = "요청이 실패했어요 (${response.code()})"
    val bodyText = response.errorBody()?.string()
    if (bodyText.isNullOrBlank()) return fallback

    return try {
        val json = JsonParser.parseString(bodyText).asJsonObject
        val detail = json.get("detail") ?: return fallback
        when {
            detail.isJsonArray -> {
                detail.asJsonArray
                    .mapNotNull { it.asJsonObject.get("msg")?.asString?.removePrefix("Value error, ") }
                    .distinct()
                    .joinToString("\n")
                    .ifBlank { fallback }
            }
            detail.isJsonPrimitive -> detail.asString
            else -> fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

/** 온보딩 흐름 전체에서 공유하는 입력값 + 진행 상태.
 * Navigation Compose 없이 간단한 상태 전환으로만 구현 (새 의존성 추가 안 함). */
class OnboardingState {
    var step = mutableStateOf(OnboardingStep.A01_SPLASH)
    var isLoading = mutableStateOf(false)
    var errorMessage = mutableStateOf<String?>(null)

    // A03
    var email = mutableStateOf("")
    var password = mutableStateOf("")
    var passwordConfirm = mutableStateOf("")

    // A04 - 인증번호 6자리를 한 칸씩 따로 관리 (Figma 화면 그대로)
    var codeDigits = mutableStateOf(listOf("", "", "", "", "", ""))
    val verificationCode get() = codeDigits.value.joinToString("")

    // ⚠️ 개발용: 실제 이메일 발송이 아직 안 붙었을 때만 서버가 응답에 실어주는 코드를
    // 화면에 그대로 보여줌. Gmail SMTP 정상 동작하면 이 값 자체가 서버에서 안 옴.
    var devOnlyCode = mutableStateOf<String?>(null)

    // A06 - 개별 동의 항목 (Figma data-bind 이름 기준)
    var agreeTermsOfService = mutableStateOf(true)
    var agreePrivacyPolicy = mutableStateOf(true)
    var agreeAgeOver14 = mutableStateOf(true)
    var agreeHealthDataUsage = mutableStateOf(true) // ⚠️ 2026-09-02 추가: 키·몸무게·운동습관 "수집·이용" 자체(필수) - 틈튼지수 "분석"과는 별개
    var agreeLocationUsage = mutableStateOf(false) // ⚠️ 2026-09-02 추가: 위치정보법 제15조 - 선택, GPS 안 쓰는 빌드면 이 줄 빼야 함
    var agreeHealthDataAnalysis = mutableStateOf(false) // "틈튼지수 산출을 위한 분석" - 선택
    var agreeMarketingPush = mutableStateOf(false)
    val allMandatoryAgreed get() = agreeTermsOfService.value && agreePrivacyPolicy.value && agreeAgeOver14.value &&
        agreeHealthDataUsage.value

    // A07
    var name = mutableStateOf("")
    var nickname = mutableStateOf("")
    var gender = mutableStateOf("FEMALE")
    var birthYear = mutableStateOf(1990)
    var birthMonth = mutableStateOf(3)
    var heightCm = mutableStateOf("")
    var weightKg = mutableStateOf("")

    // A08
    var strengthWeeklyCount = mutableStateOf(2)
    var strengthIntensity = mutableStateOf<String?>("MODERATE")
    var aerobicLowMinutes = mutableStateOf(60)
    var aerobicModerateMinutes = mutableStateOf(90)
    var aerobicHighMinutes = mutableStateOf(0)

    // A09~A10
    var wakeTime = mutableStateOf("07:00")
    var sleepTime = mutableStateOf("23:30")

    private fun clearError() {
        errorMessage.value = null
    }

    private suspend fun <T> runStep(
        block: suspend () -> Result<T>,
        onSuccess: (T) -> Unit,
        onFailureExtra: (() -> Unit)? = null,
    ) {
        clearError()
        isLoading.value = true
        val result = block()
        isLoading.value = false
        result.onSuccess(onSuccess).onFailure { e ->
            errorMessage.value = e.message ?: "알 수 없는 오류가 발생했어요. 다시 시도해주세요."
            onFailureExtra?.invoke()
        }
    }

    // ===== A05 (재방문자 로그인) =====
    suspend fun login(onSuccess: () -> Unit) {
        runStep(
            block = {
                runCatching {
                    val response = ApiClient.authApi.login(LoginRequest(email = email.value, password = password.value))
                    if (!response.isSuccessful) error(parseErrorMessage(response))
                    val token = response.body()?.access_token ?: error("토큰을 받지 못했어요")
                    TokenHolder.accessToken = token
                }
            },
            onSuccess = { onSuccess() }
        )
    }

    // ===== A03 =====
    suspend fun requestVerificationCode() {
        if (password.value != passwordConfirm.value) {
            errorMessage.value = "비밀번호가 서로 달라요. 다시 확인해주세요."
            return
        }
        runStep(
            block = {
                runCatching {
                    val response = ApiClient.onboardingApi.requestEmailVerification(
                        EmailVerificationRequestRequest(email.value)
                    )
                    if (!response.isSuccessful) error(parseErrorMessage(response))
                    // ⚠️ 개발용 — 운영 환경에서는 서버가 이 필드를 아예 안 보내므로 자동으로 null이 됨
                    devOnlyCode.value = response.body()?.get("dev_only_code") as? String
                }
            },
            onSuccess = { step.value = OnboardingStep.A04_VERIFY }
        )
    }

    // ===== A04 =====
    // A11(오류 화면) 표시용 — 몇 번 틀렸는지, 만료됐는지
    var verifyAttemptCount = mutableStateOf(0)

    suspend fun confirmVerificationCode() {
        runStep(
            block = {
                runCatching {
                    val response = ApiClient.onboardingApi.confirmEmailVerification(
                        EmailVerificationConfirmRequest(
                            email = email.value, code = verificationCode, password = password.value
                        )
                    )
                    // ⚠️ 이 요청이 422로 실패하는 이유는 "인증번호"가 아니라 "비밀번호 조건 미달"인
                    // 경우가 실제로 많았음 (요청 시점의 비밀번호를 여기서 같이 다시 검증하기 때문).
                    // 그래서 고정 문구 대신 parseErrorMessage로 서버가 알려준 진짜 이유를 그대로 보여줌.
                    if (!response.isSuccessful) error(parseErrorMessage(response))
                    val token = response.body()?.access_token ?: error("토큰을 받지 못했어요")
                    TokenHolder.accessToken = token
                }
            },
            onSuccess = { step.value = OnboardingStep.A06_CONSENT },
            onFailureExtra = {
                // A11(인증번호 오류 · 재발송) 화면으로 보냄 — 비밀번호 문제일 수도 있지만
                // 사용자 입장에선 "인증번호 다시 받기" 화면에서 원인 메시지를 같이 보여주면 됨.
                verifyAttemptCount.value += 1
                step.value = OnboardingStep.A11_VERIFY_RETRY
            }
        )
    }

    // ===== A06 =====
    suspend fun submitConsents() {
        runStep(
            block = {
                runCatching {
                    val purposes = buildList {
                        add("TERMS_OF_SERVICE")
                        add("PRIVACY_POLICY")
                        add("AGE_OVER_14")
                        add("HEALTH_DATA_USAGE") // 필수 - 키·몸무게·운동습관 수집·이용 자체
                        if (agreeLocationUsage.value) add("LOCATION_DATA_USAGE")
                        if (agreeHealthDataAnalysis.value) add("HEALTH_REFERENCE_ANALYSIS")
                        if (agreeMarketingPush.value) add("NOTIFICATION")
                    }
                    for (purpose in purposes) {
                        val response = ApiClient.onboardingApi.agreeConsent(ConsentRequest(purpose, "v1"))
                        if (!response.isSuccessful) error(parseErrorMessage(response))
                    }
                }
            },
            onSuccess = { step.value = OnboardingStep.A07_PROFILE }
        )
    }

    // ===== A07 — 프로필 + 키/몸무게(health-input) 같이 처리 =====
    suspend fun submitProfile() {
        runStep(
            block = {
                runCatching {
                    val profileResponse = ApiClient.onboardingApi.updateProfile(
                        UserUpdateRequest(
                            name = name.value,
                            nickname = nickname.value.ifBlank { null },
                            gender = gender.value,
                            birth_year = birthYear.value,
                            birth_month = birthMonth.value
                        )
                    )
                    if (!profileResponse.isSuccessful) error(parseErrorMessage(profileResponse))

                    val height = heightCm.value.toIntOrNull()
                    val weight = weightKg.value.toIntOrNull()
                    if (height != null && weight != null) {
                        val healthResponse = ApiClient.onboardingApi.submitHealthInput(
                            HealthInputRequest(
                                measured_at = java.time.Instant.now().toString(),
                                input_values = mapOf("height_cm" to height, "weight_kg" to weight),
                                units = mapOf("height_cm" to "cm", "weight_kg" to "kg"),
                                source = "MANUAL"
                            )
                        )
                        if (!healthResponse.isSuccessful) error(parseErrorMessage(healthResponse))
                    }
                }
            },
            onSuccess = { step.value = OnboardingStep.A08_EXERCISE }
        )
    }

    // ===== A08 =====
    suspend fun submitExerciseHabits(hasSensorPermissions: Boolean) {
        runStep(
            block = {
                runCatching {
                    val response = ApiClient.onboardingApi.submitExerciseHabits(
                        ExerciseHabitsRequest(
                            strength_weekly_count = strengthWeeklyCount.value,
                            strength_intensity = if (strengthWeeklyCount.value == 0) null else strengthIntensity.value,
                            aerobic_low_minutes = aerobicLowMinutes.value,
                            aerobic_moderate_minutes = aerobicModerateMinutes.value,
                            aerobic_high_minutes = aerobicHighMinutes.value
                        )
                    )
                    if (!response.isSuccessful) error(parseErrorMessage(response))
                }
            },
            // ⚠️ FLOWS.md: A16(권한 요청)이 A08 뒤에 새로 생겼는데, 프로토타입엔 "A08→A16" 연결선
            // 자체가 안 그려져 있어서(문서에 A16 진입 경로가 없음), 센서 권한이 이미 있으면
            // 굳이 또 물어보지 말고 A09로 바로 가고, 없을 때만 A16을 보여주는 걸로 해석함.
            onSuccess = { goToPermissionsOrSchedule(hasSensorPermissions) }
        )
    }

    // ===== A09~A10 =====
    suspend fun submitSchedule() {
        runStep(
            block = {
                runCatching {
                    val response = ApiClient.onboardingApi.submitSchedule(
                        OnboardingScheduleRequest(wake_time = wakeTime.value, sleep_time = sleepTime.value)
                    )
                    if (!response.isSuccessful) error(parseErrorMessage(response))
                }
            },
            // ⚠️ FLOWS.md 갱신: A10 "이 시간으로 맞추기"는 이제 바로 끝나는 게 아니라
            // A15(온보딩 완료 요약)를 거침.
            onSuccess = { step.value = OnboardingStep.A15_COMPLETE }
        )
    }

    fun skipSchedule() {
        step.value = OnboardingStep.A15_COMPLETE
    }

    // ===== A16 =====
    fun goToPermissionsOrSchedule(hasSensorPermissions: Boolean) {
        step.value = if (hasSensorPermissions) OnboardingStep.A09_SCHEDULE_INTRO else OnboardingStep.A16_PERMISSIONS
    }

    // ===== A11 (인증번호 오류 · 재발송) =====
    suspend fun resendFromRetryScreen() {
        codeDigits.value = listOf("", "", "", "", "", "")
        requestVerificationCode()
        // requestVerificationCode 성공 시 자동으로 A04로 보내줌 (기존 로직 그대로 재사용)
    }

    fun editEmailFromRetryScreen() {
        step.value = OnboardingStep.A03_SIGNUP
    }
}
