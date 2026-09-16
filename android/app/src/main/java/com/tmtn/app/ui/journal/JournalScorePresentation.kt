package com.tmtn.app.ui.journal

import com.tmtn.app.network.model.TuntunScorePeerV2Response

/** Composite remains a score. It is never converted to a position out of 100. */
internal fun journalCompositeValue(score: TuntunScorePeerV2Response?): Double? =
    score?.takeIf { it.scoreAvailable && !it.isMock && it.availableComponentCount >= 2 }
        ?.peerCompositeScore?.takeIf { it.isFinite() && it in 0.0..100.0 }

/** Match by key, never array order; the server owns rank rounding and comparison text. */
internal fun journalRankText(score: TuntunScorePeerV2Response?, key: String): String? {
    if (score == null || score.isMock) return null
    val component = score.components.firstOrNull { it.componentKey == key } ?: return null
    if (!component.available) return null
    val rank = component.rankDisplay.rankApprox ?: return null
    if (rank !in 1..100) return null
    return component.rankDisplay.text.takeIf { it.isNotBlank() } ?: "또래 100명 중 약 ${rank}등"
}
