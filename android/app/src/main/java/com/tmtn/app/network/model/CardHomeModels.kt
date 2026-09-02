package com.tmtn.app.network.model

// ===== 오늘의 카드 (B01, B03, B04, B05) =====
data class CardWindowResponse(
    val draw_state: String,           // "AWAITING_SELECTION" / "SELECTED"
    val service_date: String,
    val set_id: String,
    val option_back_ids: List<String>, // 항상 3개, 선택 전엔 내용 안 보임
    val selected_option_id: String?,
    val challenge_id: String?,
    val challenge_state: String?,
    val is_rest_day: Boolean = false
)

// ===== 카드 확정 -> 공개 (B06) =====
data class CardRevealResponse(
    val challenge_id: String,
    val exec_type: String,
    val title: String,
    val guide_text: String,
    val five_element: String,
    val domain: String?,
    val target_value: Int,
    val unit: String,
    val state: String,
    val fortune_text: String?,     // 오늘의 운세
    val lucky_location: String?,   // 행운의 위치
    val line_text: String?,        // 오늘의 한 줄
)

// ===== 챌린지 진행/완료 =====
// ⚠️ ChallengeProgressResponse는 MissionModels.kt에 이미 정의되어 있어서(같은 패키지)
// 여기서 또 안 만듦. 이 파일 아래쪽 CompleteChallengeResponse가 그걸 그대로 참조함.
data class CompleteChallengeResponse(
    val challenge: ChallengeProgressResponse,
    val points_awarded: Int,
    val five_element: String
)

data class CompleteChallengeRequestBody(
    val occurred_at: String? = null
)

// ===== 댐(재료·단계) =====
data class MaterialItem(
    val element: String,
    val material_name: String,
    val domain_label: String,
    val count: Int
)

data class StageItem(
    val stage_number: Int,
    val label: String,
    val threshold: Int,
    val completed: Boolean
)

data class CompanionResponse(
    val current_stage: Int,
    val total_materials: Int,
    val next_stage_threshold: Int?,
    val materials_needed_for_next: Int,
    val materials: List<MaterialItem>,
    val stages: List<StageItem>
)

// ===== 기록 · 연속기록 · 쉼 (B16/B17) =====
data class RestDayRequest(
    val service_date: String // "YYYY-MM-DD"
)

data class StreakResponse(
    val current_streak: Int,
    val longest_streak: Int,
    val rest_days_used_this_week: Int,
    val rest_days_remaining_this_week: Int
)

data class MemoUpdateRequest(
    val memo: String?
)

data class SkipChallengeRequest(
    val reason: String? = null,
    val occurred_at: String? = null
)
