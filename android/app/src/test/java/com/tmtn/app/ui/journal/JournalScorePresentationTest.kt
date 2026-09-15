package com.tmtn.app.ui.journal

import com.tmtn.app.network.model.*
import org.junit.Assert.*
import org.junit.Test

class JournalScorePresentationTest {
    private fun fixture() = TuntunScorePeerV2Response(false, "TEST", true, 75.6,
        PeerCompositeDisplay(75.6, "75.6점", "점", null),
        listOf(PeerComponent("lifestyle", "생활습관", true, 70.0, 65.776,
            PeerRankDisplay(34, "또래 100명 중 약 34등", null), null),
            PeerComponent("physical", "신체", false, null, null, PeerRankDisplay(null, "", null), "미입력")),
        2, true, "", "", "test", "test")

    @Test fun compositeIsPreservedAsPointsAndNeverTurnedIntoRank() {
        assertEquals(75.6, journalCompositeValue(fixture())!!, 0.0)
    }
    @Test fun serverRankAndTextWinOverLocalPercentileRounding() {
        assertEquals("또래 100명 중 약 34등", journalRankText(fixture(), "lifestyle"))
    }
    @Test fun keyMatchingDoesNotDependOnComponentOrder() {
        val score = fixture()
        assertEquals(journalRankText(score, "lifestyle"), journalRankText(score.copy(components = score.components.reversed()), "lifestyle"))
        assertNull(journalRankText(score, "physical"))
        assertNull(journalRankText(score, "diabetes"))
    }
    @Test fun missingMockAndInvalidCompositeAreNotPersonalizedResults() {
        assertNull(journalCompositeValue(null))
        assertNull(journalCompositeValue(fixture().copy(scoreAvailable = false)))
        assertNull(journalCompositeValue(fixture().copy(availableComponentCount = 1)))
        assertNull(journalCompositeValue(fixture().copy(isMock = true)))
        listOf(Double.NaN, -1.0, 101.0).forEach { assertNull(journalCompositeValue(fixture().copy(peerCompositeScore = it))) }
        assertNull(journalRankText(fixture().copy(isMock = true), "lifestyle"))
    }
    @Test fun invalidRankIsNotInventedFromPercentile() {
        val score = fixture()
        val c = score.components.first()
        listOf(null, 0, 101).forEach {
            assertNull(journalRankText(score.copy(components = listOf(c.copy(rankDisplay = PeerRankDisplay(it, "", null)))), "lifestyle"))
        }
    }
}
