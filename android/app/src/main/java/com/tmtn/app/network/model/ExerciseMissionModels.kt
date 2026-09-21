package com.tmtn.app.network.model

import java.util.UUID

/** ⚠️ 2026-09-11 신규 - TMtn_UI_V17 §5 "틈새 운동". 백엔드 app/dtos/exercise_missions.py와
 * 1:1로 맞춤. */

data class ExerciseMissionOption(
    val catalog_entry_id: UUID,
    val title: String,
    val guide_text: String,
    val exec_type: String,
    val target_value: Int,
    val unit: String,
    val five_element: String,
    val material_name: String,
    val already_completed_today: Boolean,
)

data class ExerciseMissionsTodayResponse(
    val card_completed: Boolean,
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val options: List<ExerciseMissionOption>,
    // ⚠️ 2026-09-16 추가(QA) - ACTIVE/PAUSED 세션이 있으면 채워짐. 오늘의 카드 Challenge의
    // "미션 이어하기"와 같은 원칙 - 화면을 나갔다 와도 이 값으로 이어서 진행함.
    val active_session: ExerciseMissionSessionResponse? = null,
)

data class CreateExerciseMissionSessionRequest(
    val catalog_entry_id: UUID,
    val idempotency_key: String,
)

data class ExerciseMissionSessionResponse(
    val id: UUID,
    val state: String, // ACTIVE / PAUSED / COMPLETED / CANCELLED
    val exec_type: String,
    val title: String,
    val five_element: String,
    val material_name: String,
    val target_duration_seconds: Int?,
    val target_count: Int?,
    val accumulated_duration_seconds: Int,
    val accumulated_count: Int,
    val started_at: String?,
)

data class ExerciseMissionActionRequest(
    val action: String, // "pause" | "resume"
    val accumulated_count: Int? = null,
    // ⚠️ 2026-09-16 추가(QA F05) - 시간형(걷기/달리기) 센서 확정값.
    val accumulated_duration_seconds: Int? = null,
)

data class CompleteExerciseMissionSessionRequest(
    val idempotency_key: String,
    val manual_check: Boolean = false,
    val accumulated_count: Int? = null,
    // ⚠️ 2026-09-16 추가(QA F05) - 같은 이유.
    val accumulated_duration_seconds: Int? = null,
)

data class CompleteExerciseMissionSessionResponse(
    val session_id: UUID,
    val reward_slot: Int,
    val material_name: String,
    val used: Int,
    val remaining: Int,
)
