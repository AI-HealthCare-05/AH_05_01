package com.tmtn.app.ui.reference

import com.tmtn.app.network.model.PeerComponent
import com.tmtn.app.network.model.TuntunScorePeerV2Response
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

/** Display contract only. A score is not statistically converted into a percentile. */
internal enum class ScoreValueMeaning { LEGACY_SCORE_PREVIEW, MODEL_PERCENTILE }

/**
 * ⚠️ 2026-09-14 버그 수정(외부 APK 검토 지적, REVIEW.md) — 두 가지 실제 문제를 고침:
 *
 * 1) 종합점수(peerCompositeScore)를 영역 순위와 같은 방식(MODEL_PERCENTILE)으로 표시하고
 *    있었음. 종합은 "영역 백분위들의 평균"이지 그 자체가 백분위가 아닌데, position 계산을
 *    타면서 75.6점이 "25번째쯤"처럼 순위인 것처럼 보였음. 종합은 overridePosition 없이
 *    isPreview=true(점수 표시)로 감.
 * 2) 영역 순위는 Android가 ceil(100 - HALF_UP(value,1))로 직접 재계산하고 있었는데, 서버의
 *    round_half_up(100-value) 방식과 연산 순서가 달라 경계값에서 다른 등수가 나올 수 있음
 *    (P=65.776...일 때 Android 35 vs 서버 34). 서버가 이미 정확히 계산한
 *    rankDisplay.rankApprox/text가 있으므로, Android는 재계산하지 않고 그 값을 그대로 씀
 *    (overridePosition/overrideReadingText).
 */
internal data class ScorePercentileUi(
    val value: Double,
    val isPreview: Boolean,
    private val overridePosition: Int? = null,
    private val overrideReadingText: String? = null,
) {
    init { require(value.isFinite() && value in 0.0..100.0) }
    private val displayedValue: BigDecimal get() = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)
    val label: String get() = displayedValue.stripTrailingZeros().toPlainString() + "백분위"
    val scoreNumber: String get() = displayedValue.stripTrailingZeros().toPlainString()
    val primaryLabel: String get() = if (isPreview) "${scoreNumber}점" else "${position}번째쯤"
    val reading: String get() = if (isPreview) "틈튼지수 ${scoreNumber}점" else (overrideReadingText ?: "100명 중 약 ${position}번째")
    // Approximate position among 100. overridePosition이 있으면(서버 rankApprox) 그걸 그대로
    // 쓰고, 없을 때만(예: 종합점수처럼 순위 대상이 아닌 값) 로컬 계산으로 보조함.
    val position: Int get() = overridePosition ?: ceil(100.0 - displayedValue.toDouble()).toInt().coerceIn(1, 100)
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

    @Deprecated(
        "종합점수엔 fromCompositeScore(), 영역엔 fromComponent()를 쓸 것 - " +
            "이 함수를 그대로 쓰면 종합점수가 순위로 잘못 표시되거나 영역 순위가 서버와 다르게 계산될 수 있음",
    )
    fun fromCurrent(value: Double?, available: Boolean = true): ScorePercentileUi? =
        fromValue(value, available, currentMeaning)

    fun fromValue(value: Double?, available: Boolean, meaning: ScoreValueMeaning): ScorePercentileUi? {
        if (!available || value == null || !value.isFinite() || value !in 0.0..100.0) return null
        return ScorePercentileUi(value, meaning == ScoreValueMeaning.LEGACY_SCORE_PREVIEW)
    }

    /** 종합점수(score.peerCompositeScore) 전용 - "점"으로 표시하고 순위 변환을 절대 안 함. */
    fun fromCompositeScore(score: TuntunScorePeerV2Response): ScorePercentileUi? {
        val value = score.peerCompositeScore
        if (!score.canShowOverall || value == null || !value.isFinite() || value !in 0.0..100.0) return null
        return ScorePercentileUi(value, isPreview = true)
    }

    /** fromCompositeScore(score)와 같은 규칙(항상 "점"으로 표시, 순위 변환 안 함)이지만,
     * 전체 응답 객체 대신 이미 꺼내둔 값만 있는 화면(홈 요약 카드 등)에서 씀. */
    fun fromCompositeScoreValue(value: Double?, available: Boolean): ScorePercentileUi? {
        if (!available || value == null || !value.isFinite() || value !in 0.0..100.0) return null
        return ScorePercentileUi(value, isPreview = true)
    }

    /** 영역 순위(component.peerPercentile) 전용 - 서버가 이미 계산한 rankDisplay를 그대로 씀
     * (Android가 반올림 규칙이 다른 방식으로 재계산하지 않음). */
    fun fromComponent(component: PeerComponent?): ScorePercentileUi? {
        val value = component?.peerPercentile
        if (component?.hasResult != true || value == null || !value.isFinite() || value !in 0.0..100.0) return null
        return ScorePercentileUi(
            value, isPreview = false,
            overridePosition = component.rankDisplay.rankApprox,
            overrideReadingText = component.rankDisplay.text,
        )
    }
}
