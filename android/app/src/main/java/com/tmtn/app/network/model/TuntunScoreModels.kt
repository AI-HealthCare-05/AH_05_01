package com.tmtn.app.network.model

// ===== 틈튼지수 (E그룹) =====
// ⚠️ 2026-09-02: 백엔드 app/dtos/tuntun_score.py와 필드명을 그대로 맞춤(snake_case).
// is_mock이 항상 true인 뼈대(스켈레톤) 단계 - 실제 예측 모델 연결 전.

data class ScoreBandRange(
    val range_label: String,
)

data class ScoreBands(
    val low: ScoreBandRange,   // 관심 0~39
    val mid: ScoreBandRange,   // 보통 40~69
    val high: ScoreBandRange,  // 양호 70~100
)

data class TmtnScoreFactor(
    val name: String,          // 예: "움직임·유산소", "근력"
    val weight_ratio: Double,  // 0.0~1.0, 화면엔 막대 길이로만 표시 (%는 노출 안 함)
)

data class TmtnScoreResponse(
    val value: Int,
    val band_label: String,           // "관심" / "보통" / "양호"
    val marker_left_px: Int?,
    val period_label: String,
    val change_reason: String?,
    val last_updated_label: String?,
    val bands: ScoreBands,
    val factors: List<TmtnScoreFactor>,
    val excluded_factors: List<String> = emptyList(),
    val model_version: String,
    val calibration_version: String?,
    val data_source: String,
    val is_mock: Boolean = true,
)

data class ScoreEligibilityResponse(
    val eligible: Boolean,
    val recorded_days: Int,
    val required_days: Int,
    val recorded_days_label: String,
    val required_days_label: String,
)

data class TuntunScoreOrEligibilityResponse(
    val eligible: Boolean,
    val score: TmtnScoreResponse?,
    val eligibility: ScoreEligibilityResponse?,
)

data class ScoreInputsResponse(
    val birth_month_label: String?,
    val sex_label: String?,
    val height_cm: Double?,
    val weight_kg: Double?,
    val strength_label: String?,
    val cardio_low_min: Int?,
    val cardio_moderate_min: Int?,
    val cardio_vigorous_min: Int?,
)
