package com.tmtn.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.CalendarDayItem
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.*
import org.junit.Rule
import org.junit.Test

class HomeHierarchyTest {
    @get:Rule val compose = createComposeRule()
    private fun fixture() = CardHomeState().apply {
        debugSimulatedToday.value = "2026-09-03"
        recentWeek.value = listOf(
            CalendarDayItem("2026-08-28", "BEFORE_SIGNUP"), CalendarDayItem("2026-08-29", "COMPLETED"),
            CalendarDayItem("2026-08-30", "REST"), CalendarDayItem("2026-08-31", "COMPLETED"),
            CalendarDayItem("2026-09-01", "COMPLETED"), CalendarDayItem("2026-09-02", "INCOMPLETE"),
            CalendarDayItem("2026-09-03", "COMPLETED"),
        )
        companionStage.value = 2
        companionMaterialsNeeded.value = 8
        companionNextStageLabel.value = "다음 단계"
    }

    @Test fun datesKeepMonthBoundaryAndRestDistinctFromCompletion() {
        compose.setContent { TMTNv1Theme { RecentSummaryListCard(fixture()) } }
        compose.onNodeWithText("4일 실천했어요").assertIsDisplayed()
        compose.onNodeWithContentDescription("8월 28일, 기록 없음").assertIsDisplayed()
        compose.onNodeWithContentDescription("8월 30일, 쉼").assertIsDisplayed()
        compose.onNodeWithContentDescription("9월 3일 오늘, 실천").assertIsDisplayed()
        compose.onNodeWithText("가입 전", substring = true).assertDoesNotExist()
        compose.onNodeWithText("실천").assertIsDisplayed()
        compose.onNodeWithText("미완료").assertIsDisplayed()
        compose.onNodeWithText("댐 2단계").assertIsDisplayed()
        compose.onNodeWithText("재료", substring = true).assertDoesNotExist()
    }

    @Test fun failedHistoryDoesNotSayZeroDaysOrReuseStaleDateStatus() {
        compose.setContent { TMTNv1Theme { RecentSummaryListCard(fixture().apply { recentWeekLoadFailed.value = true }) } }
        compose.onNodeWithText("기록을 불러오지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("4일 실천했어요").assertDoesNotExist()
        compose.onNodeWithContentDescription("9월 3일 오늘, 기록 확인 중").assertIsDisplayed()
    }

    @Test fun largeDatesCanBeScrolledAndScoreOpensFromHomeWithoutExampleBadge() {
        val state = fixture().apply { tuntunIndexValue.value = 68; tuntunIndexBand.value = "보통"; tuntunIndexIsMock.value = true }
        var opened = false
        compose.setContent {
            TMTNv1Theme { CompositionLocalProvider(LocalTmtnTextScale provides 2f) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                    TmtnIndexSummaryCard(state) { opened = true }
                    RecentSummaryListCard(state)
                }
            } }
        }
        compose.onNodeWithText("예시").assertDoesNotExist()
        compose.onNodeWithText("지수 보기 ›").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(opened) }
        compose.onNodeWithContentDescription("9월 3일 오늘, 실천").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("댐 2단계").performScrollTo().assertIsDisplayed()
    }
}
