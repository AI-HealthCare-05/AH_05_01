package com.tmtn.app.ui.journal

import com.tmtn.app.network.model.*
import org.junit.Assert.*
import org.junit.Test

class JournalWeeklyHistoryTest {
    private val previous = WeeklyReferencePoint("2026-09-07", "2026-09-13", "2026-09-12", "recorded", 43.5, 55.5, "policy-a")
    private val current = WeeklyReferencePoint("2026-09-14", "2026-09-20", "2026-09-19", "recorded", 42.0, 54.0, "policy-a")
    private fun response(vararg points: WeeklyReferencePoint) = WeeklyXaiHistoryResponse("tmtn-weekly-xai-v1", "ready", points = points.toList())

    @Test fun onlyAdjacentWeeksWithSameBasisCanConnect() {
        assertTrue(comparableWeeks(previous, current))
        assertFalse(comparableWeeks(previous.copy(comparison_key = "policy-b"), current))
        assertFalse(comparableWeeks(previous.copy(week_start = "2026-08-31"), current))
        assertFalse(comparableWeeks(previous.copy(status = "missing"), current))
    }

    @Test fun missingAndInvalidScoresStayMissingNeverZero() {
        val points = verifiedWeeklyPoints(response(previous.copy(status = "missing", diabetes = null, hypertension = null), current))
        assertNull(points[0].diabetes)
        assertEquals(42.0, points[1].diabetes!!, 0.0)
        assertNull(verifiedWeeklyPoints(response(current.copy(diabetes = Double.NaN))).first().diabetes)
        assertNull(verifiedWeeklyPoints(response(current.copy(hypertension = 101.0))).first().hypertension)
    }

    @Test fun datesAndContractsMustMatch() {
        assertTrue(verifiedWeeklyPoints(response(current, current)).isEmpty())
        assertTrue(verifiedWeeklyPoints(response(current.copy(week_start = "2026-09-13"))).isEmpty())
        assertNull(verifiedWeeklyPoints(response(current.copy(observed_on = "2026-09-01"))).first().diabetes)
        assertTrue(verifiedWeeklyPoints(response(current).copy(schema_version = "other-version")).isEmpty())
    }
}
