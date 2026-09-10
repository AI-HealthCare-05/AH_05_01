package com.tmtn.app.ui.reference

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

/** Display contract only. A score is not statistically converted into a percentile. */
internal enum class ScoreValueMeaning { LEGACY_SCORE_PREVIEW, MODEL_PERCENTILE }

internal data class ScorePercentileUi(val value: Double, val isPreview: Boolean) {
    init { require(value.isFinite() && value in 0.0..100.0) }
    private val displayedValue: BigDecimal get() = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)
    val label: String get() = displayedValue.stripTrailingZeros().toPlainString() + "백분위"
    val scoreNumber: String get() = displayedValue.stripTrailingZeros().toPlainString()
    val primaryLabel: String get() = if (isPreview) "${scoreNumber}점" else "${position}번째쯤"
    val reading: String get() = if (isPreview) "틈튼지수 ${scoreNumber}점" else "100명 중 약 ${position}번째"
    // Approximate position among 100, not an exact rank in a real cohort. Both endpoints are valid.
    val position: Int get() = ceil(100.0 - displayedValue.toDouble()).toInt().coerceIn(1, 100)
    val topLabel: String get() = if (displayedValue.toDouble() > 99.0) "상위 1% 이내" else "상위 약 ${position}%"
    val disclosure: String get() = if (isPreview)
        "입력한 신체 정보와 운동 정보를 바탕으로 계산한 참고 지수예요."
        else "비교 집단에서 지수가 높은 쪽부터 본 대략적인 위치예요."
}

internal object ScorePercentilePresentation {
    // ⚠️ 2026-09-10 전환: 서버가 실제로 0..100 백분위(높을수록 건강한 쪽)를 반환하는 걸
    // 확인함 - tuntun-score/peer/v2(LOCAL_REVIEW_CANDIDATE), peerPercentile/peerCompositeScore
    // 필드가 정확히 이 의미. 실기기 검증(튼튼지수 35.7점, 신체 70등 등)으로 확인 완료.
    private val currentMeaning = ScoreValueMeaning.MODEL_PERCENTILE

    fun fromCurrent(value: Double?, available: Boolean = true): ScorePercentileUi? =
        fromValue(value, available, currentMeaning)

    fun fromValue(value: Double?, available: Boolean, meaning: ScoreValueMeaning): ScorePercentileUi? {
        if (!available || value == null || !value.isFinite() || value !in 0.0..100.0) return null
        return ScorePercentileUi(value, meaning == ScoreValueMeaning.LEGACY_SCORE_PREVIEW)
    }
}
