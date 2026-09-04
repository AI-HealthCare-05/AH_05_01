package com.tmtn.app.network.model

// ===== A03~A04: 이메일 인증 =====
data class EmailVerificationRequestRequest(val email: String)

data class EmailVerificationConfirmRequest(
    val email: String,
    val code: String,
    val password: String
)

// ===== A06: 동의 =====
data class ConsentRequest(
    val purpose: String,       // "TERMS_OF_SERVICE" / "PRIVACY_POLICY" / "AGE_OVER_14" / "HEALTH_REFERENCE_ANALYSIS" / "NOTIFICATION"
    val document_version: String
)

data class ConsentResponse(
    val id: String,
    val purpose: String,
    val document_version: String,
    val status: String,
    val agreed_at: String,
    val withdrawn_at: String?
)

// ===== A07: 필수 프로필 =====
data class UserUpdateRequest(
    val name: String? = null,
    val nickname: String? = null,
    val gender: String? = null,      // "MALE" / "FEMALE"
    val birth_year: Int? = null,
    val birth_month: Int? = null,
    val is_pregnant: Boolean? = null // ⚠️ 여성만 해당. 틈튼지수 실모델 입력 계약(임신 여부 필요) - 남성/미응답이면 null
)

data class UserInfoResponse(
    val id: Long,
    val name: String?,
    val nickname: String?,
    val email: String,
    val phone_number: String?,
    val birth_year: Int?,
    val birth_month: Int?,
    val gender: String?,
    val is_pregnant: Boolean?,
    val created_at: String
)

// ===== A07: 키/몸무게 (health-input, 유연한 key-value 구조) =====
data class HealthInputRequest(
    val measured_at: String,               // ISO 8601
    val input_values: Map<String, Int>,     // {"height_cm": 168, "weight_kg": 58}
    val units: Map<String, String>,         // {"height_cm": "cm", "weight_kg": "kg"}
    val source: String = "MANUAL"
)

data class HealthInputResponse(
    val id: String,
    val measured_at: String,
    val input_values: Map<String, Any>,
    val units: Map<String, String>,
    val source: String,
    val created_at: String
)

// ===== A08: 운동습관 =====
data class ExerciseHabitsRequest(
    val strength_weekly_count: Int,      // 0(안 함)~5(주5회 이상)
    val strength_intensity: String?,     // "LIGHT" / "MODERATE" / "HARD", count=0이면 null
    val aerobic_low_minutes: Int,
    val aerobic_moderate_minutes: Int,
    val aerobic_high_minutes: Int
)

data class ExerciseHabitsResponse(
    val id: String,
    val strength_weekly_count: Int,
    val strength_intensity: String?,
    val aerobic_low_minutes: Int,
    val aerobic_moderate_minutes: Int,
    val aerobic_high_minutes: Int,
    val recorded_at: String
)

// ===== A09~A10: 생활시간 =====
data class OnboardingScheduleRequest(
    val wake_time: String,   // "HH:MM"
    val sleep_time: String
)

data class NotificationSettingResponse(
    val timezone: String,
    val slots: List<String>,
    val weekdays: List<String>,
    val quiet_hours: Map<String, String>?,
    val enabled: Boolean,
    val updated_at: String
)
