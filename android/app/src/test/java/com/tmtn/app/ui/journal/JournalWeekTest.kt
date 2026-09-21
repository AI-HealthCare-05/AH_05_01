package com.tmtn.app.ui.journal

import com.tmtn.app.network.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.DayOfWeek

class JournalWeekTest {
    private fun rolling(today: String): WeeklyReportResponse {
        val end = LocalDate.parse(today)
        return WeeklyReportResponse(end.minusDays(6).toString(), today,
            (6L downTo 0L).map { CalendarDayItem(end.minusDays(it).toString(), "COMPLETED") }, 7, 7, "MORNING",
            listOf(WeeklyMaterialItem("WOOD", "나뭇가지", 7)))
    }

    @Test fun everyWeekdayKeepsMondaySundayAndOnlyElapsedDaysCount() {
        (0L..6L).forEach { offset ->
            val original = rolling(LocalDate.of(2026, 9, 14).plusDays(offset).toString())
            val week = mondayEdition(original)!!
            assertEquals("2026-09-14", week.start_date)
            assertEquals("2026-09-20", week.end_date)
            assertEquals(offset.toInt() + 1, week.completed_count)
            assertEquals(6 - offset.toInt(), week.days.count { it.status == "FUTURE" })
            assertEquals(7, week.days.size)
            assertTrue(week.materials_this_week.isEmpty()) // never reuse last week's rewards
            assertNull(week.best_time_slot)
            assertEquals(7, original.completed_count) // home response remains untouched
        }
    }

    @Test fun monthAndYearBoundariesFollowTheServerDate() {
        listOf("2027-01-01" to "2026-12-28", "2026-10-01" to "2026-09-28").forEach { (today, start) ->
            val week = mondayEdition(rolling(today))!!
            assertEquals(start, week.start_date)
            assertEquals(DayOfWeek.SUNDAY, LocalDate.parse(week.end_date).dayOfWeek)
        }
    }

    @Test fun unavailableOrPreSignupDaysAreNeverInventedAsMissedDays() {
        val week = mondayEdition(rolling("2026-09-16").copy(days = listOf(
            CalendarDayItem("2026-09-14", "BEFORE_SIGNUP"), CalendarDayItem("2026-09-16", "REST"))))!!
        assertEquals(listOf("BEFORE_SIGNUP", "UNKNOWN", "REST", "FUTURE", "FUTURE", "FUTURE", "FUTURE"), week.days.map { it.status })
        assertEquals(0, week.completed_count)
        assertNull(mondayEdition(rolling("2026-09-16").copy(end_date = "bad-date")))
    }

    @Test fun serviceDayOwnsTheCardEvenWhenTimestampOrDeviceDateDiffers() {
        val day = DayDetailResponse("2026-09-16", "COMPLETED", "내일 기대 적기", "CHECK",
            "2026-09-15T01:00:00Z", null, null, "WOOD", "나뭇가지", null)
        val card = weekCard(day)!!
        assertEquals("2026-09-16", card.completed_at)
        assertNull(weekCard(day.copy(status = "REST")))
        assertNull(weekCard(day.copy(mission_title = null)))
        assertEquals(listOf(WeeklyMaterialItem("WOOD", "나뭇가지", 1)), weekMaterials(listOf(card)))
    }

    @Test fun futureExerciseRecordsDoNotBecomeThisWeeksAchievements() {
        val report = mondayEdition(rolling("2026-09-16"))!!
        val exercise = ExerciseMissionRecordItem("2026-09-17", "벽 짚고 밀기", "FIRE", "받침돌", 1, "2026-09-17T03:00:00Z")
        assertTrue(issueExerciseRecords(listOf(exercise), report).isEmpty())
        assertEquals(1, issueExerciseRecords(listOf(exercise.copy(service_date = "2026-09-16")), report).size)
    }
}
