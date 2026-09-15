package com.tmtn.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.onboarding.TmtnTonalButton
import com.tmtn.app.ui.record.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecordReturnUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readingTheJournalKeepsTheRecordPeriodMonthAndScrollOnReturn() {
        var inJournal by mutableStateOf(false)
        val loaded = mutableListOf<RecordTab>()
        val state = RecordState().apply {
            year.value = 2026; month.value = 8
            monthlyCalendar.value = MonthlyCalendarResponse(2026, 8,
                listOf(CalendarDayItem("2026-08-27", "COMPLETED")), 1, 0)
            weeklyReport.value = WeeklyReportResponse("2026-09-08", "2026-09-14",
                (8..14).map { CalendarDayItem("2026-09-${it.toString().padStart(2, '0')}", "COMPLETED") },
                7, 7, null, listOf(WeeklyMaterialItem("WOOD", "나뭇가지", 3), WeeklyMaterialItem("EARTH", "다짐흙", 4)))
        }
        compose.setContent {
            val pages = rememberSaveableStateHolder()
            TMTNv1Theme { Box(Modifier.fillMaxWidth().height(560.dp)) {
                if (inJournal) TmtnTonalButton("기록으로 돌아가기", { inJournal = false })
                else pages.SaveableStateProvider("record") {
                    RecordFlow({}, { inJournal = true }, state = state, onLoad = { loaded += state.tab.value })
                }
            } }
        }
        compose.onNodeWithText("2026년 8월").assertExists()
        compose.onNodeWithText("최근 7일 돌아보기").performScrollTo().performClick()
        compose.onNodeWithText("주간면 읽기").performScrollTo().performClick()
        compose.onNodeWithText("기록으로 돌아가기").performClick()
        compose.onNodeWithText("주간면 읽기").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(RecordTab.WEEKLY, state.tab.value)
            assertEquals(listOf(RecordTab.MONTHLY, RecordTab.WEEKLY, RecordTab.WEEKLY), loaded)
        }
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("최근 7일 돌아보기").assertIsDisplayed()
        compose.runOnIdle { assertEquals(8, state.month.value); assertEquals(2026, state.year.value) }
    }
}
