package com.tmtn.app.network.model

/**
 * 서버 app/dtos/sensor.py의 SensorMeasurementItem/Request/Response와 1:1 대응.
 * JSON 키가 snake_case라서 Kotlin 프로퍼티명도 그대로 snake_case로 맞춤
 * (LoginResponse.access_token 때와 같은 방식 — 별도 컨버터 설정 없이 Gson이 그대로 매핑함).
 *
 * v3 변경사항:
 * - challengeId는 body에서 빠지고 URL 경로로 이동 (MissionApi.kt 참고)
 * - measurementType → measurement_type (STEP/STAIR/RUNNING)
 * - event_timestamp_ns 추가 (RUNNING 전용, 서버가 유효 속도 구간을 판정하는 데 필요)
 */
data class SensorMeasurementItem(
    val measurement_type: String,          // "STEP" / "STAIR" / "RUNNING"
    val value: Int? = null,                // STEP/STAIR 전용 (이번 배치에서 새로 감지된 증가분)
    val speed_kmh: Double? = null,          // RUNNING 전용
    val event_timestamp_ns: Long? = null,   // RUNNING 전용
    val recorded_at: String                 // ISO-8601, 예: "2026-08-26T07:20:00Z"
)

data class SensorMeasurementBatchRequest(
    val records: List<SensorMeasurementItem>
)

data class ChallengeProgressResponse(
    val id: String,
    val exec_type: String,
    val state: String,
    val target_duration_seconds: Int?,
    val accumulated_duration_seconds: Int,
    val target_count: Int?,
    val accumulated_count: Int,
    val version: Int
)

data class SensorMeasurementBatchResponse(
    val challenge: ChallengeProgressResponse,
    val accepted_count: Int,
    val rejected_count: Int,
    val rejected_reasons: List<String> = emptyList()
)
