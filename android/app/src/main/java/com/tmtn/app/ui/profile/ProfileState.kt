package com.tmtn.app.ui.profile

import com.tmtn.app.ui.common.failWithMessage
import com.tmtn.app.ui.common.userMessageOr
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
import com.tmtn.app.network.model.UserInfoResponse
import com.tmtn.app.network.model.UserUpdateRequest
import com.tmtn.app.ui.onboarding.parseErrorMessage
import com.tmtn.app.ui.theme.AccessibilitySettingsHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class ProfileScreenKey {
    // ⚠️ 2026-09-08 QA 반영: WAKE_SLEEP 추가. "자고 일어나는 시각" 행이 MainActivity에서 빈
    // 람다({})로 연결돼 있어서 눌러도 아무 반응이 없었음(온보딩 A10 안에서만 동작하는 화면이라
    // 미연결로 남겨뒀던 자리). 내 정보 탭 안에 같은 기능의 화면을 만들어서 연결함.
    HOME, NOTIFICATION, NOTIFICATION_TIME, WAKE_SLEEP, PERMISSIONS, ACCOUNT, HEALTH, EXERCISE, CONSENT, ACCESSIBILITY,
    EMAIL_CHANGE, PASSWORD_CHANGE, DELETE_REAUTH, DELETE_DONE,
    PRIVACY_DATA, EXPORT_DATA, APP_INFO, HELP_DETAIL, INQUIRY, SOUND, BASIC, STORY, TERMS,
}

/** F01~F12(중 API 있는 것) "내 정보" 탭 전체 상태. */
class ProfileState(private val profileApiProvider: () -> com.tmtn.app.network.ProfileApi = { ApiClient.profileApi }) {
    var screen = mutableStateOf(ProfileScreenKey.HOME)
    var isLoading = mutableStateOf(false)
    var errorMessage = mutableStateOf<String?>(null)
    var termsOrigin = ProfileScreenKey.HOME
    var loadFailed = mutableStateOf(false)

    var userInfo = mutableStateOf<UserInfoResponse?>(null)
    var companionMaterials = mutableStateOf<Int?>(null)
    var companionStage = mutableStateOf<Int?>(null)
    var healthInput = mutableStateOf<HealthInputResponse?>(null)
    var exerciseHabits = mutableStateOf<ExerciseHabitsResponse?>(null)
    var consents = mutableStateOf<List<ConsentResponse>>(emptyList())
    var accessibility = mutableStateOf<AccessibilityResponse?>(null)
    var notificationSetting = mutableStateOf<NotificationSettingResponse?>(null)

    // F23: 어떤 알림 슬롯(0=아침준비/1=점심뒤/2=자기전)을 편집 중인지
    var editingSlotIndex = mutableStateOf(0)

    suspend fun loadAll() {
        if (isLoading.value) return
        isLoading.value = true
        errorMessage.value = null
        loadFailed.value = false
        suspend fun <T> read(request: suspend () -> retrofit2.Response<T>, missingIsEmpty: Boolean = false, apply: (T?) -> Unit) {
            try {
                val response = request()
                if (response.isSuccessful) apply(response.body())
                else if (response.code() == 404 && missingIsEmpty) apply(null)
                else loadFailed.value = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { loadFailed.value = true }
        }
        try {
            coroutineScope {
                launch { read({ profileApiProvider().getMe() }) { userInfo.value = it } }
                launch { read({ ApiClient.cardHomeApi.getCompanionStatus() }) {
                    companionMaterials.value = it?.total_materials; companionStage.value = it?.current_stage
                } }
                launch { read({ profileApiProvider().getLatestHealthInput() }, missingIsEmpty = true) { healthInput.value = it } }
                launch { read({ profileApiProvider().getLatestExerciseHabits() }, missingIsEmpty = true) { exerciseHabits.value = it } }
                launch { read({ profileApiProvider().listConsents() }) { consents.value = it.orEmpty() } }
                launch { read({ profileApiProvider().getAccessibility() }) {
                    accessibility.value = it
                    it?.let { body ->
                        AccessibilitySettingsHolder.apply(body.large_controls, body.senior_mode, body.preferred_text_scale_hint)
                        AccessibilitySettingsHolder.reducedMotion.value = body.reduced_motion
                    }
                } }
                launch { read({ profileApiProvider().getNotificationSettings() }) { notificationSetting.value = it } }
            }
            if (userInfo.value == null) loadFailed.value = true
            if (loadFailed.value) errorMessage.value = "일부 정보를 불러오지 못했어요. 연결을 확인한 뒤 다시 불러올 수 있어요."
        } finally {
            isLoading.value = false
        }
    }

    // F08: 성별 저장 - ⚠️ 2026-09-07 QA(N5) 반영: 온보딩 기본값(FEMALE) 문제 때문에 잘못
    // 저장된 성별을 되돌릴 방법이 없었음. users/me PATCH는 부분 수정을 허용하므로 gender만 보냄.
    //
    // ⚠️ 2026-09-08 QA 반영: 성별만 바꾸면 is_pregnant가 예전 값(또는 null)으로 남아서,
    // 남성 -> 여성으로 바꾼 사용자는 틈튼지수 건강 영역이 계속 미산출됐음. 임신 여부를 같이
    // 보내는 함수로 바꿈(남성이면 null을 보내서 "해당 없음"으로 정리됨 - 서버가 성별로 자동
    // 판단하므로 null이 맞음).
    suspend fun saveBasicInfo(name: String, nickname: String, year: Int, month: Int) {
        if (isLoading.value) return
        isLoading.value = true
        errorMessage.value = null
        try {
            val response = profileApiProvider().updateMe(UserUpdateRequest(name = name.trim(), nickname = nickname.trim(), birth_year = year, birth_month = month))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            userInfo.value = response.body() ?: failWithMessage("저장한 정보를 확인하지 못했어요. 다시 시도해 주세요.")
            screen.value = ProfileScreenKey.HOME
        } catch (c: kotlinx.coroutines.CancellationException) { throw c }
        catch (e: Exception) { errorMessage.value = e.userMessageOr("저장하지 못했어요. 입력한 내용은 그대로예요.") }
        finally { isLoading.value = false }
    }

    suspend fun saveGenderAndPregnancy(gender: String, isPregnant: Boolean?) {
        updatePreference("성별을 저장하지 못했어요. 다시 시도해 주세요.") {
            val response = profileApiProvider().updateMe(
                UserUpdateRequest(gender = gender, is_pregnant = isPregnant)
            )
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            userInfo.value = response.body() ?: failWithMessage("저장한 정보를 확인하지 못했어요.")
        }
    }

    // Existing notification PATCH preserves enabled. The onboarding schedule POST always enables it.
    // Server mapping: wake / lunch + 1 hour / sleep - 2 hours. Local scheduling follows a successful save.
    suspend fun saveWakeSleep(wakeTime: String, lunchTime: String, sleepTime: String) {
        updatePreference("시간을 저장하지 못했어요. 입력한 내용은 그대로예요.") {
            val response = profileApiProvider().updateNotificationSettings(
                NotificationSettingUpdateRequest(slots = listOf(
                    wakeTime, java.time.LocalTime.parse(lunchTime).plusHours(1).toString(),
                    java.time.LocalTime.parse(sleepTime).minusHours(2).toString(),
                ))
            )
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            notificationSetting.value = response.body() ?: failWithMessage("저장한 알림을 확인하지 못했어요.")
            screen.value = ProfileScreenKey.NOTIFICATION
        }
    }

    // F08: 몸 정보 저장
    suspend fun saveHealthInput(heightCm: Int, weightKg: Int) {
        updatePreference("몸 정보를 저장하지 못했어요. 입력한 내용은 그대로예요.") {
            val response = profileApiProvider().submitHealthInput(
                HealthInputRequest(
                    measured_at = java.time.Instant.now().toString(),
                    input_values = mapOf("height_cm" to heightCm, "weight_kg" to weightKg),
                    units = mapOf("height_cm" to "cm", "weight_kg" to "kg"),
                )
            )
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            healthInput.value = response.body() ?: failWithMessage("저장한 몸 정보를 확인하지 못했어요.")
            screen.value = ProfileScreenKey.HOME
        }
    }

    // F09: 운동 정보 저장
    suspend fun saveExerciseHabits(
        strengthCount: Int, strengthIntensity: String?,
        aerobicLow: Int, aerobicModerate: Int, aerobicHigh: Int,
    ) {
        updatePreference("운동 정보를 저장하지 못했어요. 입력한 내용은 그대로예요.") {
            val response = profileApiProvider().submitExerciseHabits(
                ExerciseHabitsRequest(
                    strength_weekly_count = strengthCount,
                    strength_intensity = if (strengthCount == 0) null else strengthIntensity,
                    aerobic_low_minutes = aerobicLow,
                    aerobic_moderate_minutes = aerobicModerate,
                    aerobic_high_minutes = aerobicHigh,
                )
            )
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            exerciseHabits.value = response.body() ?: failWithMessage("저장한 운동 정보를 확인하지 못했어요.")
            screen.value = ProfileScreenKey.HOME
        }
    }

    // F11: 선택 동의 철회
    val pendingWithdrawal = mutableStateOf<String?>(null)
    val confirmRecordDeletion = mutableStateOf(false)
    val confirmationOwnsFeedback get() = pendingWithdrawal.value != null || confirmRecordDeletion.value

    suspend fun withdrawConsent(purpose: String): Boolean =
        updatePreference("동의를 변경하지 못했어요. 다시 시도해 주세요.") {
            val response = profileApiProvider().withdrawConsent(purpose)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val updated = response.body() ?: failWithMessage("변경한 동의를 확인하지 못했어요.")
            consents.value = consents.value.filterNot { it.purpose == purpose } + updated
        }

    // F10: 선택 동의 켜기
    suspend fun agreeOptionalConsent(purpose: String) {
        updatePreference("동의를 변경하지 못했어요. 다시 시도해 주세요.") {
            val response = profileApiProvider().agreeConsent(ConsentRequest(purpose = purpose, document_version = "v1"))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val updated = response.body() ?: failWithMessage("변경한 동의를 확인하지 못했어요.")
            consents.value = consents.value.filterNot { it.purpose == purpose } + updated
        }
    }

    // F12: 접근성 저장 (부분 수정 - 보낸 필드만 반영)
    suspend fun updateAccessibility(update: AccessibilityUpdateRequest) {
        updatePreference("화면 설정을 저장하지 못했어요. 다시 시도해 주세요.") {
            val response = profileApiProvider().updateAccessibility(update)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val body = response.body() ?: failWithMessage("저장한 설정을 확인하지 못했어요.")
            accessibility.value = body
            // ⚠️ 2026-09-04 QA(P0-6) 반영: 여기서 서버에 저장만 하고 끝나서, 설정 화면을
            // 나가야만(또는 앱을 재시작해야만) 반영되는 것처럼 느껴졌음. 바로 전역 홀더에
            // 반영해서 이 화면의 "미리보기"부터 다른 화면까지 즉시 바뀌게 함.
            AccessibilitySettingsHolder.apply(body.large_controls, body.senior_mode, body.preferred_text_scale_hint)
            AccessibilitySettingsHolder.reducedMotion.value = body.reduced_motion
        }
    }

    // F02: 알림 전체 켜기/끄기
    suspend fun toggleNotificationsEnabled(enabled: Boolean) {
        updatePreference("알림 설정을 저장하지 못했어요. 다시 시도해 주세요.") {
            val response = profileApiProvider().updateNotificationSettings(NotificationSettingUpdateRequest(enabled = enabled))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            notificationSetting.value = response.body() ?: failWithMessage("저장한 알림을 확인하지 못했어요.")
        }
    }

    private suspend fun updatePreference(fallback: String, action: suspend () -> Unit): Boolean {
        if (isLoading.value) return false
        isLoading.value = true
        errorMessage.value = null
        return try { action(); true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { errorMessage.value = error.userMessageOr(fallback); false }
        finally { isLoading.value = false }
    }

    // F23: 슬롯 하나(아침준비/점심뒤/자기전)의 시각 수정
    suspend fun updateSlotTime(index: Int, time: String) {
        val current = notificationSetting.value?.slots?.toMutableList() ?: return
        if (index !in current.indices) return
        current[index] = time
        updatePreference("시간을 저장하지 못했어요. 입력한 내용은 그대로예요.") {
            val response = profileApiProvider().updateNotificationSettings(NotificationSettingUpdateRequest(slots = current))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            notificationSetting.value = response.body() ?: failWithMessage("저장한 알림을 확인하지 못했어요.")
            screen.value = ProfileScreenKey.NOTIFICATION
        }
    }

    fun logout() {
        // ⚠️ 2026-09-08: refresh_token 쿠키까지 같이 비움(ApiClient.clearSession 주석 참고).
        ApiClient.clearSession()
    }

    var passwordChangeDone = mutableStateOf(false)
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        updatePreference("비밀번호를 바꾸지 못했어요.") {
            passwordChangeDone.value = false
            val response = profileApiProvider().changePassword(
                com.tmtn.app.network.model.PasswordChangeRequest(currentPassword, newPassword))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            passwordChangeDone.value = true
            screen.value = ProfileScreenKey.ACCOUNT
        }
    }

    var emailChangeCodeSent = mutableStateOf(false)
    var pendingNewEmail = mutableStateOf("")
    suspend fun requestEmailChangeCode(newEmail: String) {
        updatePreference("인증번호를 보내지 못했어요.") {
            val response = ApiClient.onboardingApi.requestEmailVerification(
                com.tmtn.app.network.model.EmailVerificationRequestRequest(newEmail.trim()))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            pendingNewEmail.value = newEmail.trim()
            emailChangeCodeSent.value = true
        }
    }

    suspend fun confirmEmailChange(code: String) {
        updatePreference("이메일을 바꾸지 못했어요.") {
            val response = profileApiProvider().changeEmail(
                com.tmtn.app.network.model.EmailChangeRequest(pendingNewEmail.value, code))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            userInfo.value = response.body() ?: failWithMessage("변경한 계정을 확인하지 못했어요.")
            emailChangeCodeSent.value = false
            screen.value = ProfileScreenKey.ACCOUNT
        }
    }

    suspend fun deleteAccount(password: String) {
        updatePreference("계정을 삭제하지 못했어요.") {
            val response = profileApiProvider().deleteAccount(com.tmtn.app.network.model.AccountDeleteRequest(password))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            ApiClient.clearSession()
            screen.value = ProfileScreenKey.DELETE_DONE
        }
    }

    var recordsDeletedDone = mutableStateOf(false)
    suspend fun deleteRecordsOnly(): Boolean {
        val saved = updatePreference("기록을 지우지 못했어요.") {
            val response = profileApiProvider().deleteRecordsOnly()
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            recordsDeletedDone.value = true
            healthInput.value = null
            exerciseHabits.value = null
            companionMaterials.value = null
            companionStage.value = null
            screen.value = ProfileScreenKey.PRIVACY_DATA
        }
        if (saved) loadAll()
        return saved
    }

    // The callback must finish writing the existing CSV before the UI reports success.
    suspend fun exportMyData(onSaveCsv: (fileName: String, content: String) -> Unit) {
        updatePreference("내보내지 못했어요. 저장 공간과 연결을 확인해 주세요.") {
            val response = profileApiProvider().exportMyData()
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val body = response.body() ?: failWithMessage("받아 온 파일이 없어요. 다시 시도해 주세요.")
            onSaveCsv("tmtn_my_data.csv", body.use { it.string() })
        }
    }

    var inquirySubmitted = mutableStateOf(false)
    suspend fun submitInquiry(topic: String, content: String, includeDeviceInfo: Boolean, deviceInfo: String) {
        updatePreference("문의를 보내지 못했어요. 적어 둔 내용은 그대로예요.") {
            val response = profileApiProvider().createInquiry(com.tmtn.app.network.model.InquiryCreateRequest(
                topic = topic, content = content.trim(), device_info = if (includeDeviceInfo) deviceInfo else null))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            inquirySubmitted.value = true
        }
    }
}
