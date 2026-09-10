package com.tmtn.app.ui.profile

import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.AccessibilityResponse
import com.tmtn.app.network.model.AccessibilityUpdateRequest
import com.tmtn.app.network.model.ConsentRequest
import com.tmtn.app.network.model.ConsentResponse
import com.tmtn.app.network.model.ExerciseHabitsRequest
import com.tmtn.app.network.model.ExerciseHabitsResponse
import com.tmtn.app.network.model.HealthInputRequest
import com.tmtn.app.network.model.HealthInputResponse
import com.tmtn.app.network.model.NotificationSettingResponse
import com.tmtn.app.network.model.NotificationSettingUpdateRequest
import com.tmtn.app.network.model.OnboardingScheduleRequest
import com.tmtn.app.network.model.UserInfoResponse
import com.tmtn.app.network.model.UserUpdateRequest
import com.tmtn.app.ui.onboarding.parseErrorMessage
import com.tmtn.app.ui.theme.AccessibilitySettingsHolder

enum class ProfileScreenKey {
    // ⚠️ 2026-09-08 QA 반영: WAKE_SLEEP 추가. "자고 일어나는 시각" 행이 MainActivity에서 빈
    // 람다({})로 연결돼 있어서 눌러도 아무 반응이 없었음(온보딩 A10 안에서만 동작하는 화면이라
    // 미연결로 남겨뒀던 자리). 내 정보 탭 안에 같은 기능의 화면을 만들어서 연결함.
    HOME, NOTIFICATION, NOTIFICATION_TIME, WAKE_SLEEP, PERMISSIONS, ACCOUNT, HEALTH, EXERCISE, CONSENT, ACCESSIBILITY,
    EMAIL_CHANGE, PASSWORD_CHANGE, DELETE_REAUTH, DELETE_DONE,
    PRIVACY_DATA, EXPORT_DATA, APP_INFO, HELP_DETAIL, INQUIRY,
}

/** F01~F12(중 API 있는 것) "내 정보" 탭 전체 상태. */
class ProfileState {
    var screen = mutableStateOf(ProfileScreenKey.HOME)
    var isLoading = mutableStateOf(false)
    var errorMessage = mutableStateOf<String?>(null)

    var userInfo = mutableStateOf<UserInfoResponse?>(null)
    var companionMaterials = mutableStateOf<Int?>(null)
    var healthInput = mutableStateOf<HealthInputResponse?>(null)
    var exerciseHabits = mutableStateOf<ExerciseHabitsResponse?>(null)
    var consents = mutableStateOf<List<ConsentResponse>>(emptyList())
    var accessibility = mutableStateOf<AccessibilityResponse?>(null)
    var notificationSetting = mutableStateOf<NotificationSettingResponse?>(null)

    // F23: 어떤 알림 슬롯(0=아침준비/1=점심뒤/2=자기전)을 편집 중인지
    var editingSlotIndex = mutableStateOf(0)

    suspend fun loadAll() {
        isLoading.value = true
        runCatching { ApiClient.profileApi.getMe() }.getOrNull()?.let { if (it.isSuccessful) userInfo.value = it.body() }
        runCatching { ApiClient.cardHomeApi.getCompanionStatus() }.getOrNull()?.let { if (it.isSuccessful) companionMaterials.value = it.body()?.total_materials }
        runCatching { ApiClient.profileApi.getLatestHealthInput() }.getOrNull()?.let { if (it.isSuccessful) healthInput.value = it.body() }
        runCatching { ApiClient.profileApi.getLatestExerciseHabits() }.getOrNull()?.let { if (it.isSuccessful) exerciseHabits.value = it.body() }
        runCatching { ApiClient.profileApi.listConsents() }.getOrNull()?.let { if (it.isSuccessful) consents.value = it.body() ?: emptyList() }
        runCatching { ApiClient.profileApi.getAccessibility() }.getOrNull()?.let {
            if (it.isSuccessful) {
                accessibility.value = it.body()
                it.body()?.let { body ->
                    AccessibilitySettingsHolder.apply(body.large_controls, body.senior_mode, body.preferred_text_scale_hint)
                }
            }
        }
        runCatching { ApiClient.profileApi.getNotificationSettings() }.getOrNull()?.let { if (it.isSuccessful) notificationSetting.value = it.body() }
        isLoading.value = false
    }

    // F08: 성별 저장 - ⚠️ 2026-09-07 QA(N5) 반영: 온보딩 기본값(FEMALE) 문제 때문에 잘못
    // 저장된 성별을 되돌릴 방법이 없었음. users/me PATCH는 부분 수정을 허용하므로 gender만 보냄.
    //
    // ⚠️ 2026-09-08 QA 반영: 성별만 바꾸면 is_pregnant가 예전 값(또는 null)으로 남아서,
    // 남성 -> 여성으로 바꾼 사용자는 틈튼지수 건강 영역이 계속 미산출됐음. 임신 여부를 같이
    // 보내는 함수로 바꿈(남성이면 null을 보내서 "해당 없음"으로 정리됨 - 서버가 성별로 자동
    // 판단하므로 null이 맞음).
    suspend fun saveGenderAndPregnancy(gender: String, isPregnant: Boolean?) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.updateMe(
                UserUpdateRequest(gender = gender, is_pregnant = isPregnant)
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { userInfo.value = it }
            .onFailure { e -> errorMessage.value = e.message ?: "성별 저장에 실패했어요." }
        isLoading.value = false
    }

    // ⚠️ 2026-09-08 QA 반영: "자고 일어나는 시각" 저장. 온보딩 A10과 같은 엔드포인트를 그대로
    // 재사용함 - 서버가 기상/취침 시각을 따로 저장하지는 않고 알림 슬롯 3개(기상+2시간 /
    // 점심(직접입력) / 취침-2시간)로 환산해서 보관하므로(notification_setting_service.py),
    // 응답으로 돌아온 슬롯을 그대로 반영하면 화면이 최신 상태가 됨.
    // ⚠️ 실제 로컬 알림 재예약(NotificationScheduler)은 호출부(WakeSleepEditScreen)에서
    // 이 함수가 끝난 뒤 최신 slots로 함 - "알람이 안 온다" QA의 원인이 이 함수가 서버
    // 저장만 하고 기기 예약을 안 했던 것이었음.
    suspend fun saveWakeSleep(wakeTime: String, lunchTime: String, sleepTime: String) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.onboardingApi.submitSchedule(
                OnboardingScheduleRequest(wake_time = wakeTime, lunch_time = lunchTime, sleep_time = sleepTime)
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            notificationSetting.value = it
            screen.value = ProfileScreenKey.NOTIFICATION
        }.onFailure { e -> errorMessage.value = e.message ?: "시간 저장에 실패했어요." }
        isLoading.value = false
    }

    // F08: 몸 정보 저장
    suspend fun saveHealthInput(heightCm: Int, weightKg: Int) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.submitHealthInput(
                HealthInputRequest(
                    measured_at = java.time.Instant.now().toString(),
                    input_values = mapOf("height_cm" to heightCm, "weight_kg" to weightKg),
                    units = mapOf("height_cm" to "cm", "weight_kg" to "kg"),
                )
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            healthInput.value = it
            screen.value = ProfileScreenKey.HOME
        }.onFailure { e -> errorMessage.value = e.message ?: "저장에 실패했어요." }
        isLoading.value = false
    }

    // F09: 운동 정보 저장
    suspend fun saveExerciseHabits(
        strengthCount: Int, strengthIntensity: String?,
        aerobicLow: Int, aerobicModerate: Int, aerobicHigh: Int,
    ) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.submitExerciseHabits(
                ExerciseHabitsRequest(
                    strength_weekly_count = strengthCount,
                    strength_intensity = if (strengthCount == 0) null else strengthIntensity,
                    aerobic_low_minutes = aerobicLow,
                    aerobic_moderate_minutes = aerobicModerate,
                    aerobic_high_minutes = aerobicHigh,
                )
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            exerciseHabits.value = it
            screen.value = ProfileScreenKey.HOME
        }.onFailure { e -> errorMessage.value = e.message ?: "저장에 실패했어요." }
        isLoading.value = false
    }

    // F11: 선택 동의 철회
    suspend fun withdrawConsent(purpose: String) {
        isLoading.value = true
        runCatching { ApiClient.profileApi.withdrawConsent(purpose) }
        runCatching { ApiClient.profileApi.listConsents() }.getOrNull()?.let { if (it.isSuccessful) consents.value = it.body() ?: emptyList() }
        isLoading.value = false
    }

    // F10: 선택 동의 켜기
    suspend fun agreeOptionalConsent(purpose: String) {
        isLoading.value = true
        runCatching { ApiClient.profileApi.agreeConsent(ConsentRequest(purpose = purpose, document_version = "v1")) }
        runCatching { ApiClient.profileApi.listConsents() }.getOrNull()?.let { if (it.isSuccessful) consents.value = it.body() ?: emptyList() }
        isLoading.value = false
    }

    // F12: 접근성 저장 (부분 수정 - 보낸 필드만 반영)
    suspend fun updateAccessibility(update: AccessibilityUpdateRequest) {
        runCatching {
            val response = ApiClient.profileApi.updateAccessibility(update)
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let {
            accessibility.value = it
            // ⚠️ 2026-09-04 QA(P0-6) 반영: 여기서 서버에 저장만 하고 끝나서, 설정 화면을
            // 나가야만(또는 앱을 재시작해야만) 반영되는 것처럼 느껴졌음. 바로 전역 홀더에
            // 반영해서 이 화면의 "미리보기"부터 다른 화면까지 즉시 바뀌게 함.
            AccessibilitySettingsHolder.apply(it.large_controls, it.senior_mode, it.preferred_text_scale_hint)
        }
    }

    // F02: 알림 전체 켜기/끄기
    suspend fun toggleNotificationsEnabled(enabled: Boolean) {
        runCatching {
            val response = ApiClient.profileApi.updateNotificationSettings(NotificationSettingUpdateRequest(enabled = enabled))
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { notificationSetting.value = it }
    }

    // F23: 슬롯 하나(아침준비/점심뒤/자기전)의 시각 수정
    suspend fun updateSlotTime(index: Int, time: String) {
        val current = notificationSetting.value?.slots?.toMutableList() ?: return
        if (index !in current.indices) return
        current[index] = time
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.updateNotificationSettings(NotificationSettingUpdateRequest(slots = current))
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            notificationSetting.value = it
            screen.value = ProfileScreenKey.NOTIFICATION
        }.onFailure { e -> errorMessage.value = e.message ?: "저장에 실패했어요." }
        isLoading.value = false
    }

    fun logout() {
        // ⚠️ 2026-09-08: refresh_token 쿠키까지 같이 비움(ApiClient.clearSession 주석 참고).
        ApiClient.clearSession()
    }

    // ===== F16: 비밀번호 변경 =====
    var passwordChangeDone = mutableStateOf(false)

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        isLoading.value = true
        errorMessage.value = null
        passwordChangeDone.value = false
        runCatching {
            val response = ApiClient.profileApi.changePassword(
                com.tmtn.app.network.model.PasswordChangeRequest(currentPassword, newPassword)
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
        }.onSuccess {
            passwordChangeDone.value = true
            screen.value = ProfileScreenKey.ACCOUNT
        }.onFailure { e -> errorMessage.value = e.message ?: "비밀번호를 바꾸지 못했어요." }
        isLoading.value = false
    }

    // ===== F15: 이메일 변경 =====
    var emailChangeCodeSent = mutableStateOf(false)
    var pendingNewEmail = mutableStateOf("")

    suspend fun requestEmailChangeCode(newEmail: String) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.onboardingApi.requestEmailVerification(
                com.tmtn.app.network.model.EmailVerificationRequestRequest(newEmail)
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
        }.onSuccess {
            pendingNewEmail.value = newEmail
            emailChangeCodeSent.value = true
        }.onFailure { e -> errorMessage.value = e.message ?: "인증번호를 보내지 못했어요." }
        isLoading.value = false
    }

    suspend fun confirmEmailChange(code: String) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.changeEmail(
                com.tmtn.app.network.model.EmailChangeRequest(pendingNewEmail.value, code)
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            userInfo.value = it
            emailChangeCodeSent.value = false
            screen.value = ProfileScreenKey.ACCOUNT
        }.onFailure { e -> errorMessage.value = e.message ?: "이메일을 바꾸지 못했어요." }
        isLoading.value = false
    }

    // ===== F17/F18: 계정 삭제 =====
    suspend fun deleteAccount(password: String) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.deleteAccount(
                com.tmtn.app.network.model.AccountDeleteRequest(password)
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
        }.onSuccess {
            // ⚠️ 2026-09-08: 계정이 사라졌으므로 refresh_token 쿠키도 같이 비워야 함 - 안 그러면
            // 삭제된 계정의 쿠키가 남아서 재가입 직후 엉뚱한 갱신이 돌 수 있음.
            ApiClient.clearSession()
            screen.value = ProfileScreenKey.DELETE_DONE
        }.onFailure { e -> errorMessage.value = e.message ?: "계정을 삭제하지 못했어요." }
        isLoading.value = false
    }

    // ===== F13: 기록만 삭제 =====
    var recordsDeletedDone = mutableStateOf(false)

    suspend fun deleteRecordsOnly() {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.deleteRecordsOnly()
            if (!response.isSuccessful) error(parseErrorMessage(response))
        }.onSuccess {
            recordsDeletedDone.value = true
            screen.value = ProfileScreenKey.PRIVACY_DATA
            loadAll() // 화면에 남아있는 몸정보/운동정보 등도 다시 불러와서 지워진 걸 반영
        }.onFailure { e -> errorMessage.value = e.message ?: "기록을 지우지 못했어요." }
        isLoading.value = false
    }

    // ===== F14: 내 데이터 내보내기 =====
    // 실제 CSV 저장은 Context가 필요해서(MediaStore) 콜백으로 MainActivity에 위임함.
    suspend fun exportMyData(onSaveCsv: (fileName: String, content: String) -> Unit) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.exportMyData()
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!.string()
        }.onSuccess { csvText ->
            onSaveCsv("tmtn_my_data.csv", csvText)
        }.onFailure { e -> errorMessage.value = e.message ?: "내보내기에 실패했어요." }
        isLoading.value = false
    }

    // ===== F21: 문의 남기기 =====
    var inquirySubmitted = mutableStateOf(false)

    suspend fun submitInquiry(topic: String, content: String, includeDeviceInfo: Boolean, deviceInfo: String) {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.profileApi.createInquiry(
                com.tmtn.app.network.model.InquiryCreateRequest(
                    topic = topic, content = content,
                    device_info = if (includeDeviceInfo) deviceInfo else null,
                )
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
        }.onSuccess {
            inquirySubmitted.value = true
        }.onFailure { e -> errorMessage.value = e.message ?: "문의를 보내지 못했어요." }
        isLoading.value = false
    }
}
