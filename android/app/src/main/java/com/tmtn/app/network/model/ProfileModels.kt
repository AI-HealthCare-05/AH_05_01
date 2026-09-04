package com.tmtn.app.network.model

// ⚠️ UserInfoResponse, UserUpdateRequest, HealthInputRequest/Response,
// ExerciseHabitsRequest/Response, ConsentRequest/Response, NotificationSettingResponse는
// 전부 OnboardingModels.kt에 이미 있어서(같은 패키지) 여기서 다시 안 만듦.

// ===== F12: 접근성 =====
data class AccessibilityResponse(
    val large_controls: Boolean,
    val reduced_motion: Boolean,
    val preferred_text_scale_hint: String?,
    val senior_mode: Boolean,
    val updated_at: String
)

data class AccessibilityUpdateRequest(
    val large_controls: Boolean? = null,
    val reduced_motion: Boolean? = null,
    val preferred_text_scale_hint: String? = null,
    val senior_mode: Boolean? = null
)

// ===== F02/F23: 알림 설정 수정 =====
data class NotificationSettingUpdateRequest(
    val enabled: Boolean? = null,
    val slots: List<String>? = null,
    val weekdays: List<String>? = null,
    val quiet_hours: Map<String, String>? = null
)

// ===== F16: 비밀번호 변경 =====
data class PasswordChangeRequest(
    val current_password: String,
    val new_password: String
)

// ===== F15: 이메일 변경 =====
data class EmailChangeRequest(
    val new_email: String,
    val code: String
)

// ===== F17: 계정 삭제 =====
data class AccountDeleteRequest(
    val password: String
)

// ===== F21: 문의 남기기 =====
data class InquiryCreateRequest(
    val topic: String,
    val content: String,
    val device_info: String? = null
)

data class InquiryResponse(
    val id: String,
    val topic: String,
    val content: String,
    val device_info: String?,
    val created_at: String
)
