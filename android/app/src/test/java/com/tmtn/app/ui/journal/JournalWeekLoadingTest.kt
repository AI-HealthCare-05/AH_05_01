package com.tmtn.app.ui.journal

import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.RecordApi
import com.tmtn.app.network.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class JournalWeekLoadingTest {
    private fun unavailable() = Response.error<Any>(503, "".toResponseBody())
    private fun source() = WeeklyReportResponse("2026-09-10", "2026-09-16", (10..16).map {
        CalendarDayItem("2026-09-$it", if (it in listOf(10, 14, 16)) "COMPLETED" else "REST")
    }, 3, 7, "MORNING", listOf(WeeklyMaterialItem("WOOD", "나뭇가지", 3)))
    private fun state(failDay: Boolean = false, failReport: Boolean = false, calls: MutableList<String> = mutableListOf()): JournalState {
        val record = Proxy.newProxyInstance(RecordApi::class.java.classLoader, arrayOf(RecordApi::class.java)) { _, method, args ->
            calls.add(method.name + ":" + args.dropLast(1).joinToString(","))
            when (method.name) {
                "getWeeklyReport" -> if (failReport) unavailable() else Response.success(source())
                "getDayDetail" -> if (failDay && args[0] == "2026-09-14") unavailable() else Response.success(
                    DayDetailResponse(args[0] as String, "COMPLETED", "한 장 실천", "CHECK", "2026-09-10T12:00:00Z", null, null, "WOOD", "나뭇가지", null))
                "getExerciseMissionRecords" -> Response.error<Any>(404, "".toResponseBody())
                else -> error("Unexpected API ${method.name}")
            }
        } as RecordApi
        val cards = Proxy.newProxyInstance(CardHomeApi::class.java.classLoader, arrayOf(CardHomeApi::class.java)) { _, method, _ ->
            check(method.name == "getTodayCards") { "Journal must not fetch a mismatched rolling card collection" }
            unavailable()
        } as CardHomeApi
        return JournalState({ record }, { cards }, editorialRequest = { Response.error(404, "".toResponseBody()) })
    }

    @Test fun requestsOnlyCompletedServiceDaysAndKeepsMondayRewardsConsistent() = runBlocking {
        val calls = mutableListOf<String>()
        val state = state(calls = calls)
        state.refresh()
        val week = (state.weekly as JournalLoad.Ready).value
        assertEquals(2, week.completed_count)
        assertEquals(2, week.materials_this_week.single().count)
        assertEquals(listOf("2026-09-16", "2026-09-14"), (state.collection as JournalLoad.Ready).value.map { it.completed_at })
        assertEquals(setOf("getDayDetail:2026-09-14", "getDayDetail:2026-09-16"), calls.filter { it.startsWith("getDayDetail:") }.toSet())
        assertTrue(calls.contains("getExerciseMissionRecords:2026-09-14,2026-09-20"))
        assertNull(state.exercises) // older backend still supports the rest of the newspaper
        assertFalse(state.refreshing)
    }

    @Test fun aFailedDayDoesNotPublishPartialMaterialTotals() = runBlocking {
        val state = state(failDay = true)
        state.refresh()
        assertTrue(state.collection is JournalLoad.Failed)
        assertEquals(2, (state.weekly as JournalLoad.Ready).value.completed_count)
        assertTrue((state.weekly as JournalLoad.Ready).value.materials_this_week.isEmpty())
    }

    @Test fun missingReportNeverFallsBackToPhoneDateOrLastWeeksData() = runBlocking {
        val calls = mutableListOf<String>()
        val state = state(failReport = true, calls = calls)
        state.refresh()
        assertTrue(state.weekly is JournalLoad.Failed)
        assertTrue(state.collection is JournalLoad.Failed)
        assertNull(state.exercises)
        assertEquals(listOf("getWeeklyReport:"), calls)
    }
}
