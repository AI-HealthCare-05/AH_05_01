package com.tmtn.app.ui.reference

import org.junit.Assert.*
import org.junit.Test

class ScorePercentileTest {
    @Test fun currentScoreUsesActualPointsWithoutClaimingRank() {
        val result = ScorePercentilePresentation.fromCurrent(68.0)!!
        assertTrue(result.isPreview)
        assertEquals("68점", result.primaryLabel)
        assertEquals("틈튼지수 68점", result.reading)
        assertFalse(result.disclosure.contains("새 모델"))
    }
    @Test fun confirmedModelPercentileUsesHigherIndexDirectionAndApproximateRank() {
        val result = ScorePercentilePresentation.fromValue(80.0, true, ScoreValueMeaning.MODEL_PERCENTILE)!!
        assertFalse(result.isPreview)
        assertEquals(20, result.position)
        assertEquals("20번째쯤", result.primaryLabel)
        assertEquals("100명 중 약 20번째", result.reading)
        assertEquals("상위 약 20%", result.topLabel)
    }
    @Test fun endpointsNeverProduceZerothOrOneHundredFirstPlace() {
        assertEquals(100, ScorePercentilePresentation.fromCurrent(0.0)!!.position)
        assertEquals(1, ScorePercentilePresentation.fromCurrent(100.0)!!.position)
        assertEquals(1, ScorePercentilePresentation.fromCurrent(99.8)!!.position)
        assertEquals("상위 1% 이내", ScorePercentilePresentation.fromCurrent(100.0)!!.topLabel)
        assertEquals(21, ScorePercentilePresentation.fromCurrent(79.6)!!.position)
    }
    @Test fun invalidOrUnavailableResultsNeverBecomeAComparison() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 101.0).forEach {
            assertNull(ScorePercentilePresentation.fromCurrent(it))
        }
        assertNull(ScorePercentilePresentation.fromCurrent(99.0, available = false))
    }
    @Test fun displayRoundingDoesNotContradictTheApproximatePosition() {
        val result = ScorePercentilePresentation.fromCurrent(79.96)!!
        assertEquals("80백분위", result.label)
        assertEquals(20, result.position)
        assertEquals(79.96, result.value, 0.0)
    }
}
