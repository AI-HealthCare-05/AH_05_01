package com.tmtn.app.ui.onboarding

import com.tmtn.app.ui.common.failWithMessage
import com.tmtn.app.ui.common.userMessageOr
import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.TokenHolder
import com.tmtn.app.network.model.ConsentRequest
import com.tmtn.app.network.model.EmailVerificationConfirmRequest
import com.tmtn.app.network.model.EmailVerificationRequestRequest
import com.tmtn.app.network.model.ExerciseHabitsRequest
import com.tmtn.app.network.model.HealthInputRequest
import com.tmtn.app.network.model.LoginRequest
import com.tmtn.app.network.model.NotificationSettingResponse
import com.tmtn.app.network.model.OnboardingScheduleRequest
import com.tmtn.app.network.model.UserUpdateRequest
import com.tmtn.app.network.model.GoogleLoginRequest
import com.tmtn.app.auth.GoogleLoginOutcome
import com.tmtn.app.auth.googleLoginOutcome
import com.tmtn.app.auth.GoogleSignInHelper
import retrofit2.Response

enum class OnboardingStep {
    A01_SPLASH, A02_START, A03_SIGNUP, A04_VERIFY, A05_LOGIN,
    A06_CONSENT, A07_PROFILE, A08_EXERCISE, A09_SCHEDULE_INTRO, A10_SCHEDULE,
    A11_VERIFY_RETRY, A12_PASSWORD_RESET_REQUEST, A13_NEW_PASSWORD, A14_TERMS_DETAIL,
    A15_COMPLETE, A16_PERMISSIONS, SIGNUP_COMPLETE,
    AUTH_CHOICE, DONE
}

/** Shared HTTP copy; response bodies never appear directly in a UI error label. */
fun parseErrorMessage(response: Response<*>): String {
    android.util.Log.w("ApiError", "HTTP ${response.code()}")
    return com.tmtn.app.ui.common.readableHttpError(response.code(), response.errorBody()?.string())
}

/** 온보딩 흐름 전체에서 공유하는 입력값 + 진행 상태.
 * Navigation Compose 없이 간단한 상태 전환으로만 구현 (새 의존성 추가 안 함). */
class OnboardingState(private val authApiProvider: () -> com.tmtn.app.network.AuthApi = { ApiClient.authApi }) {
    var step = mutableStateOf(OnboardingStep.A01_SPLASH)
    var isLoading = mutableStateOf(false)
    private var registration = RegistrationProgress()
    val accountCreated get() = registration.accountCreated

    init {
        if (TokenHolder.accessToken != null) {
            OnboardingCheckpoint.pendingStep()?.let { pending ->
                registration = RegistrationProgress(accountAlreadyCreated = true)
                step.value = pending
            }
        }
    }
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
    // ⚠️ 법적 문제(2026-09-04 재확인): 필수 약관을 미리 체크된 상태로 시작하면 안 됨 -
    // 미리 체크된 동의는 법적으로 동의로 인정되지 않음. 반드시 false로 시작해서 사용자가
    // 직접 체크해야만 함(allMandatoryAgreed가 "다음" 버튼 활성화를 이미 막아줌).
    var agreeTermsOfService = mutableStateOf(false)
    var agreePrivacyPolicy = mutableStateOf(false)
    var agreeAgeOver14 = mutableStateOf(false)
    var agreeHealthDataUsage = mutableStateOf(false) // ⚠️ 2026-09-02 추가: 키·몸무게·운동습관 "수집·이용" 자체(필수) - 틈튼지수 "분석"과는 별개
    var agreeLocationUsage = mutableStateOf(false) // ⚠️ 2026-09-02 추가: 위치정보법 제15조 - 선택, GPS 안 쓰는 빌드면 이 줄 빼야 함
    var agreeHealthDataAnalysis = mutableStateOf(false) // "틈튼지수 산출을 위한 분석" - 선택
    var agreeMarketingPush = mutableStateOf(false)
    val allMandatoryAgreed get() = agreeTermsOfService.value && agreePrivacyPolicy.value && agreeAgeOver14.value &&
        agreeHealthDataUsage.value

    // A07
    var name = mutableStateOf("")
    var nickname = mutableStateOf("")
    // ⚠️ 2026-09-07 QA(N5) 반영: 기본값이 "FEMALE"로 박혀있어서 사용자가 직접 고르지 않아도
    // 여성으로 저장되고(허리둘레·틈튼지수 모델 입력값임), 몸 정보에서도 읽기 전용이라 되돌릴 수 없었음 —
    // 미선택(null)로 시작해서 A07의 "다음" 버튼 enabled 조건에서 직접 고르게 강제함(OnboardingScreensProfile.kt 참고).
    var gender = mutableStateOf<String?>(null)
    var birthDateConfirmed = mutableStateOf(false)
    var birthYear = mutableStateOf(1990)
    var birthMonth = mutableStateOf(3)
    // ⚠️ 2026-09-04 추가: 여성일 때만 물어보는 임신 여부. null=아직 안 물어봤거나 응답 안 함
    // (이 경우 서버에 아예 안 보냄 - "임신 아님"으로 넘겨짚지 않기 위함).
    var isPregnant = mutableStateOf<Boolean?>(null)
    var heightCm = mutableStateOf("")
    var weightKg = mutableStateOf("")

    // A08
    var strengthWeeklyCount = mutableStateOf(0)
    var strengthIntensity = mutableStateOf<String?>(null)
    var aerobicLowMinutes = mutableStateOf(0)
    var aerobicModerateMinutes = mutableStateOf(0)
    var aerobicHighMinutes = mutableStateOf(0)

    // A09~A10
    var wakeTime = mutableStateOf("07:00")
    // ⚠️ 2026-09-08 추가: 점심은 자동 계산(기상+N시간) 대신 사용자가 직접 입력.
    var lunchTime = mutableStateOf("12:00")
    var sleepTime = mutableStateOf("23:30")
    // ⚠️ 2026-09-08 추가: submitSchedule() 응답(서버가 계산한 실제 slots)을 저장 - 로컬
    // 알림 예약 시 클라이언트가 계산식을 따로 다시 계산하다 서버와 어긋나는 걸 방지하려고
    // 서버가 준 값을 그대로 씀.
    var scheduleNotificationSetting = mutableStateOf<NotificationSettingResponse?>(null)

    private fun clearError() {
        errorMessage.value = null
    }

    val googlePicking = mutableStateOf(false)
    val googleLinkEmail = mutableStateOf<String?>(null)
    val googleReauthRequired = mutableStateOf(false)
    private val pendingGoogleToken = mutableStateOf<String?>(null)
    private val googleSignupPending = mutableStateOf(false)
    val isGoogleSignup get() = googleSignupPending.value

    fun cancelGoogleSignup() {
        pendingGoogleToken.value = null
        googleSignupPending.value = false
        googleReauthRequired.value = false
        googleLinkEmail.value = null
    }

    fun leaveConsent() {
        if (accountCreated || isLoading.value) return
        if (isGoogleSignup) {
            cancelGoogleSignup()
            step.value = OnboardingStep.AUTH_CHOICE
        } else step.value = OnboardingStep.A04_VERIFY
    }

    suspend fun loginWithGoogle(context: android.content.Context, onExistingUser: () -> Unit) {
        if (isLoading.value || googlePicking.value) return
        clearError()
        googlePicking.value = true
        val result = try { GoogleSignInHelper.requestIdToken(context) } finally { googlePicking.value = false }
        when (result) {
            is GoogleSignInHelper.Result.Success -> exchangeGoogleToken(result.idToken, false, onExistingUser)
            GoogleSignInHelper.Result.NoGoogleAccount -> { errorMessage.value = "구글 계정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요." }
                is GoogleSignInHelper.Result.Failure -> errorMessage.value = result.message
            GoogleSignInHelper.Result.Cancelled -> Unit
        }
    }

    suspend fun confirmGoogleLink(onExistingUser: () -> Unit) {
        val token = pendingGoogleToken.value ?: return
        exchangeGoogleToken(token, true, onExistingUser)
    }

    internal suspend fun exchangeGoogleToken(idToken: String, linkConfirmed: Boolean, onExistingUser: () -> Unit) {
        if (isLoading.value) return
        var verifiedEmail: String? = null
        runStep(block = {
            runCatching {
                val outcome = googleLoginOutcome(authApiProvider().googleLogin(
                    GoogleLoginRequest(idToken, link_confirmed = linkConfirmed)))
                if (outcome is GoogleLoginOutcome.SignedIn) {
                    TokenHolder.accessToken = outcome.session.access_token
                    if (OnboardingCheckpoint.pendingStep() != null) {
                        val me = ApiClient.profileApi.getMe()
                        if (!me.isSuccessful) failWithMessage(parseErrorMessage(me))
                        verifiedEmail = me.body()?.email
                            ?: failWithMessage("계정 정보를 확인하지 못했어요. 다시 시도해 주세요.")
                    }
                }
                outcome
            }
        }, onSuccess = { outcome ->
            googleReauthRequired.value = false
            when (outcome) {
                is GoogleLoginOutcome.ConsentRequired -> {
                    pendingGoogleToken.value = idToken
                    googleSignupPending.value = true
                    googleLinkEmail.value = null
                    email.value = outcome.email
                    step.value = OnboardingStep.A06_CONSENT
                }
                is GoogleLoginOutcome.LinkRequired -> {
                    pendingGoogleToken.value = idToken
                    googleSignupPending.value = false
                    googleLinkEmail.value = outcome.email
                }
                is GoogleLoginOutcome.SignedIn -> {
                    cancelGoogleSignup()
                    if (outcome.session.is_new_user) {
                        registration = RegistrationProgress(accountAlreadyCreated = true)
                        OnboardingCheckpoint.save(OnboardingStep.A06_CONSENT, verifiedEmail.orEmpty())
                        step.value = OnboardingStep.A06_CONSENT
                    } else finishReturningLogin(verifiedEmail.orEmpty(), onExistingUser)
                }
            }
        })
    }

    private fun finishReturningLogin(accountEmail: String, onSuccess: () -> Unit) {
        val pending = OnboardingCheckpoint.pendingStep()
        if (pending != null && accountEmail.isNotBlank() && OnboardingCheckpoint.belongsTo(accountEmail)) {
            email.value = accountEmail
            registration = RegistrationProgress(accountAlreadyCreated = true)
            step.value = if (pending == OnboardingStep.A06_CONSENT) pending else OnboardingStep.A07_PROFILE
        } else {
            OnboardingCheckpoint.clear()
            onSuccess()
        }
    }

    private suspend fun createAccountWithGoogle(idToken: String) {
        val response = authApiProvider().googleLogin(GoogleLoginRequest(idToken, signup_confirmed = true))
        if (response.code() == 401) {
            googleReauthRequired.value = true
            failWithMessage("Google 인증이 만료됐어요. 계정을 다시 선택해 주세요.")
        }
        val result = googleLoginOutcome(response) as? GoogleLoginOutcome.SignedIn
            ?: failWithMessage("Google 계정을 다시 확인해 주세요.")
        OnboardingCheckpoint.save(OnboardingStep.A06_CONSENT, email.value)
        TokenHolder.accessToken = result.session.access_token
    }

    private suspend fun <T> runStep(
        block: suspend () -> Result<T>,
        onSuccess: (T) -> Unit,
        onFailureExtra: (() -> Unit)? = null,
    ) {
        if (isLoading.value) return
        clearError()
        isLoading.value = true
        val result = try { block() } finally { isLoading.value = false }
        result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        result.onSuccess(onSuccess).onFailure { e ->
            errorMessage.value = e.userMessageOr("요청을 마치지 못했어요. 다시 시도해 주세요.")
            onFailureExtra?.invoke()
        }
    }

    // ===== A05 (재방문자 로그인) =====
    suspend fun login(onSuccess: () -> Unit) {
        runStep(
            block = {
                runCatching {
                    val response = authApiProvider().login(LoginRequest(email = email.value, password = password.value))
                    if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
                    val token = response.body()?.access_token ?: failWithMessage("로그인 정보를 확인하지 못했어요. 다시 시도해 주세요.")
                    TokenHolder.accessToken = token
                }
            },
            onSuccess = {
                finishReturningLogin(email.value, onSuccess)
            }
        )
    }

    // ===== A03 =====
    suspend fun requestVerificationCode() {
        if (password.value != passwordConfirm.value) {
            errorMessage.value = "비밀번호가 서로 달라요. 다시 확인해주세요."
            return
        }
        // ⚠️ 2026-09-08 QA 반영: 화면에서는 trim해서 검증만 통과시키고, 실제 서버로 보내는
        // 값은 여전히 공백이 낀 원본이면 서버(EmailStr)에서 다시 튕길 수 있음 - 검증에 쓴
        // 값과 실제로 보내는 값을 일치시킴.
        email.value = email.value.trim()
        runStep(
            block = {
                runCatching {
                    val response = ApiClient.onboardingApi.requestEmailVerification(
                        EmailVerificationRequestRequest(email.value)
                    )
                    if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
                    // ⚠️ 개발용 — 운영 환경에서는 서버가 이 필드를 아예 안 보내므로 자동으로 null이 됨
                    devOnlyCode.value = response.body()?.get("dev_only_code") as? String
                }
            },
            onSuccess = { step.value = OnboardingStep.A04_VERIFY }
        )
    }

    // The confirmation endpoint creates an account, so call it only after explicit consent.
    var verifyAttemptCount = mutableStateOf(0)

    fun continueToConsent() {
        if (verificationCode.length == 6 && verificationCode.all(Char::isDigit)) {
            step.value = OnboardingStep.A06_CONSENT
        }
    }

    suspend fun submitConsents() {
        if (isLoading.value) return
        if (!allMandatoryAgreed) {
            errorMessage.value = "필수 약관을 확인해 주세요."
            return
        }
        val purposes = buildList {
            add("TERMS_OF_SERVICE"); add("PRIVACY_POLICY"); add("AGE_OVER_14"); add("HEALTH_DATA_USAGE")
            if (agreeLocationUsage.value) add("LOCATION_DATA_USAGE")
            if (agreeHealthDataAnalysis.value) add("HEALTH_REFERENCE_ANALYSIS")
            if (agreeMarketingPush.value) add("NOTIFICATION")
        }
        runStep(
            block = {
                runCatching {
                    registration.complete(
                        requiredAgreed = allMandatoryAgreed,
                        purposes = purposes,
                        createAccount = {
                            if (isGoogleSignup) {
                                createAccountWithGoogle(pendingGoogleToken.value
                                    ?: failWithMessage("Google 계정을 다시 선택해 주세요."))
                            } else {
                            val response = ApiClient.onboardingApi.confirmEmailVerification(
                                EmailVerificationConfirmRequest(email.value, verificationCode, password.value)
                            )
                            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
                            val token = response.body()?.access_token ?: failWithMessage("로그인 정보를 받지 못했어요. 다시 시도해 주세요.")
                            // Save the pending screen before persisting the returned login token.
                            OnboardingCheckpoint.save(OnboardingStep.A06_CONSENT, email.value)
                            TokenHolder.accessToken = token
                            }
                        },
                        saveConsent = { purpose ->
                            val response = ApiClient.onboardingApi.agreeConsent(ConsentRequest(purpose, "v1"))
                            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
                        },
                    )
                }
            },
            onSuccess = {
                cancelGoogleSignup()
                password.value = ""; passwordConfirm.value = ""
                codeDigits.value = List(6) { "" }; devOnlyCode.value = null
                OnboardingCheckpoint.save(OnboardingStep.SIGNUP_COMPLETE)
                step.value = OnboardingStep.SIGNUP_COMPLETE
            },
            onFailureExtra = {
                // A consent failure retains the account and retries only remaining requests.
                if (!accountCreated && !isGoogleSignup) {
                    verifyAttemptCount.value += 1
                    step.value = OnboardingStep.A11_VERIFY_RETRY
                }
            },
        )
    }

    /** Rehydrate already saved fields after reopening an unfinished signup. */
    suspend fun restoreProfileForResume() {
        if (!accountCreated || OnboardingCheckpoint.pendingStep() == null) return
        if (step.value in listOf(OnboardingStep.A06_CONSENT, OnboardingStep.SIGNUP_COMPLETE)) return
        runStep(block = {
            runCatching {
                val response = ApiClient.profileApi.getMe()
                if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
                response.body()?.let {
                    name.value = it.name.orEmpty(); nickname.value = it.nickname.orEmpty()
                    email.value = it.email; gender.value = it.gender
                    birthYear.value = it.birth_year ?: birthYear.value; birthMonth.value = it.birth_month ?: birthMonth.value
                    birthDateConfirmed.value = it.birth_year != null && it.birth_month != null
                    isPregnant.value = it.is_pregnant
                }
                val health = ApiClient.profileApi.getLatestHealthInput()
                if (health.isSuccessful) health.body()?.input_values?.let {
                    heightCm.value = (it["height_cm"] as? Number)?.toInt()?.toString().orEmpty()
                    weightKg.value = (it["weight_kg"] as? Number)?.toInt()?.toString().orEmpty()
                }
                val exercise = ApiClient.profileApi.getLatestExerciseHabits()
                if (exercise.isSuccessful) exercise.body()?.let {
                    strengthWeeklyCount.value = it.strength_weekly_count; strengthIntensity.value = it.strength_intensity
                    aerobicLowMinutes.value = it.aerobic_low_minutes; aerobicModerateMinutes.value = it.aerobic_moderate_minutes
                    aerobicHighMinutes.value = it.aerobic_high_minutes
                }
            }
        }, onSuccess = {}, onFailureExtra = {
            // Keep required fields visible if restoration failed, rather than bypassing them.
            step.value = OnboardingStep.A07_PROFILE
        })
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
                            birth_month = birthMonth.value,
                            // ⚠️ 남성이면 애초에 질문 자체를 안 보여줘서(UI) isPregnant가 항상
                            // null - 서버가 성별로 자동 판단(nonpregnant)하므로 그냥 안 보내도 됨.
                            is_pregnant = if (gender.value == "FEMALE") isPregnant.value else null,
                        )
                    )
                    if (!profileResponse.isSuccessful) failWithMessage(parseErrorMessage(profileResponse))

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
                        if (!healthResponse.isSuccessful) failWithMessage(parseErrorMessage(healthResponse))
                    }
                }
            },
            onSuccess = { OnboardingCheckpoint.save(OnboardingStep.A08_EXERCISE); step.value = OnboardingStep.A08_EXERCISE }
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
                    if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
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
                        OnboardingScheduleRequest(
                            wake_time = wakeTime.value, lunch_time = lunchTime.value, sleep_time = sleepTime.value
                        )
                    )
                    if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
                    scheduleNotificationSetting.value = response.body()
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
        OnboardingCheckpoint.save(OnboardingStep.A09_SCHEDULE_INTRO)
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
