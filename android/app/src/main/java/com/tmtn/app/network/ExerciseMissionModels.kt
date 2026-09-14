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
)

data class CompleteExerciseMissionSessionRequest(
    val idempotency_key: String,
    val manual_check: Boolean = false,
    val accumulated_count: Int? = null,
)

data class CompleteExerciseMissionSessionResponse(
    val session_id: UUID,
    val reward_slot: Int,
    val material_name: String,
    val used: Int,
    val remaining: Int,
)
