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
    val is_rest_day: Boolean = false,
    // ⚠️ 2026-09-07 반영: 상태전이 정책(G3) - 카드를 아직 안 뽑아 challenge가 없는 날의
    // "포기" 표시. is_rest_day와 대칭 필드 (서버 CardWindowResponse와 동일 이름).
    val is_given_up: Boolean = false
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
    // ⚠️ 2026-09-04 반영: 서버가 계산한 실제 경과 시간(초). ACTIVE면 지금까지 쌓인 값 +
    // 이번 구간에서 흐른 시간까지 포함해서 내려줌 - CardHomeState.stepForRevealedCard()가
    // 타이머 화면 진입 시 이 값으로 timerElapsedSeconds를 맞춤.
    val elapsed_seconds: Int = 0,
    // ⚠️ 2026-09-06 추가: COUNT형(걸음수·계단·거리) 전용 - 서버에 마지막으로 보고된
    // 누적 측정치. 재진입 시 이 값부터 로컬 센서 매니저가 이어서 세게 함.
    val accumulated_count: Int = 0,
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
    val occurred_at: String? = null,
    // ⚠️ 2026-09-08 반영: SENSOR형 미션을 "직접 체크로 할래요"로 완료할 때 실측값 검증을
    // 건너뛰라고 서버에 알리는 플래그.
    val manual_check: Boolean = false
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
