package com.tmtn.app.ui.reference

import com.tmtn.app.network.model.ScoreInputsResponse
import com.tmtn.app.network.model.WeeklyReportResponse
import org.junit.Assert.*
import org.junit.Test

class ScoreEditorialTest {
    private fun week(count: Int, time: String?) = WeeklyReportResponse("2026-09-04", "2026-09-10", emptyList(), count, 7, time, emptyList())

    @Test fun recordsNeedEnoughEvidenceBeforeClaimingATimePattern() {
        assertFalse(recordNews(week(2, "저녁"))!!.headline.contains("저녁"))
        assertTrue(recordNews(week(3, "저녁"))!!.evening)
        assertFalse(recordNews(week(4, "알 수 없음"))!!.headline.contains("알 수 없음"))
        assertFalse(recordNews(week(4, "오전"))!!.evening)
    }

    @Test fun emptyAndInvalidRecordsCannotBecomeActivity() {
        val empty = recordNews(week(0, "저녁"))!!
        assertTrue(empty.empty)
        assertFalse(empty.evening)
        assertNull(recordNews(week(8, "저녁")))
        assertNull(recordNews(week(-1, null)))
    }

    @Test fun inputGroupingPreservesMissingZeroAndDecimalValuesWithoutDuplication() {
        val inputs = ScoreInputsResponse(null, "여성", 164.0, 58.5, "주 0회", 0, null, 15)
        val body = scoreInputFacts(inputs, "physical")
        assertEquals("58.5 kg", body.first { it.label == "몸무게" }.value)
        assertEquals("미입력", body.first { it.label == "생년월" }.value)
        val exercise = scoreInputFacts(inputs, "lifestyle")
        assertEquals("0분 / 주", exercise.first { it.label == "가벼운 유산소" }.value)
        assertEquals("미입력", exercise.first { it.label == "적당한 유산소" }.value)
        val all = scoreInputFacts(inputs, "diabetes")
        assertEquals(8, all.size)
        assertEquals(8, all.distinctBy { it.label }.size)
        assertFalse(all.any { it.label.contains("허리") })
    }
}
