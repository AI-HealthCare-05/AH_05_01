package com.tmtn.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.MonthlyCalendarResponse
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.record.MonthlyCalendarScreen
import com.tmtn.app.ui.record.RecordState
import com.tmtn.app.ui.theme.LocalTmtnTextScale
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class RecordCalendarTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(month: YearMonth) = RecordState().apply {
        year.value = month.year
        this.month.value = month.monthValue
        monthlyCalendar.value = MonthlyCalendarResponse(month.year, month.monthValue, emptyList(), 0, 0)
    }

    @Test fun emptyCalendarKeepsNavigationAcrossYearBoundaryAndTodayReturns() {
        val state = fixture(YearMonth.of(2026, 12))
        var requested: YearMonth? = null
        compose.setContent {
            TMTNv1Theme {
                MonthlyCalendarScreen(state, rememberCoroutineScope(), {}, onMonthChange = {
                    requested = it
                    state.year.value = it.year; state.month.value = it.monthValue
                    state.monthlyCalendar.value = MonthlyCalendarResponse(it.year, it.monthValue, emptyList(), 0, 0)
                })
            }
        }
        compose.onNodeWithContentDescription("다음 달").performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2027, 1), requested) }
        compose.onNodeWithContentDescription("이전 달").performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 12), requested) }
        compose.onNode(hasText("오늘") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(YearMonth.now(), requested) }
        compose.onNodeWithContentDescription("이전 달").assertIsDisplayed()
    }

    @Test fun leapDayOpensTheExactDate() {
        val state = fixture(YearMonth.of(2024, 2))
        var selected: String? = null
        compose.setContent {
            TMTNv1Theme {
                MonthlyCalendarScreen(state, rememberCoroutineScope(), {}, onDateSelected = { selected = it })
            }
        }
        compose.onNodeWithContentDescription("29일, 기록 없음").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("2024-02-29", selected) }
        compose.onNodeWithContentDescription("30일, 기록 없음").assertDoesNotExist()
    }

    @Test fun calendarFits320dpWithLargeTextAndExposesSelectedTab() {
        val state = fixture(YearMonth.of(2026, 9))
        compose.setContent {
            TMTNv1Theme {
                CompositionLocalProvider(LocalTmtnTextScale provides 1.3f) {
                    Box(Modifier.width(320.dp).testTag("calendar")) {
                        MonthlyCalendarScreen(state, rememberCoroutineScope(), {}, onPeriodSelected = { state.tab.value = it })
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("calendar").fetchSemanticsNode().boundsInRoot
        for (day in 7..13) {
            val date = compose.onNode(hasContentDescription("${day}일, 기록 없음") or hasContentDescription("${day}일, 기록 없음, 오늘"))
            date.performScrollTo()
            val cell = date.fetchSemanticsNode().boundsInRoot
            assertTrue("Date $day outside calendar", cell.left >= bounds.left && cell.right <= bounds.right)
        }
        compose.onNodeWithText("월간").performScrollTo().assertIsSelected()
        compose.onNodeWithText("주간").performClick().assertIsSelected()
    }

    @Test fun draggingOffAnActionCancelsItAndNextTapWorks() {
        var count = 0
        compose.setContent {
            TMTNv1Theme { Box(Modifier.width(220.dp)) { TmtnPrimaryButton("시작하기", { count++ }) } }
        }
        compose.onNodeWithText("시작하기").performTouchInput {
            down(center)
            moveTo(Offset(center.x, height.toFloat() + 120f))
            up()
        }
        compose.runOnIdle { assertEquals(0, count) }
        compose.onNodeWithText("시작하기").performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }
}
