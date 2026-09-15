package com.tmtn.app.ui

import com.tmtn.app.network.model.*

internal fun peerFixture(value: Double = 68.0) = TuntunScorePeerV2Response(
    isMock = false, releaseStatus = "TEST", scoreAvailable = true, peerCompositeScore = value,
    compositeDisplay = PeerCompositeDisplay(value, "${value}점", "점", null),
    components = listOf("physical" to "신체", "diabetes" to "당뇨", "hypertension" to "고혈압", "lifestyle" to "생활습관").mapIndexed { index, (key, name) ->
        PeerComponent(key, name, true, 60.0 + index, 65.776,
            PeerRankDisplay(34, "또래 100명 중 약 34등", null), null)
    }, availableComponentCount = 4, isPartialScore = false,
    referenceCaution = "비진단용 참고 정보", notice = "", modelVersion = "test", formulaVersion = "test",
)
