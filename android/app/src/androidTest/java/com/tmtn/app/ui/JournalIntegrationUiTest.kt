package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.journal.*
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** Offline fixtures. No account, model, mission or preference requests. */
class JournalIntegrationUiTest {
    @get:Rule val compose = createComposeRule()
    private val savedScale = AccessibilitySettingsHolder.textScaleHint.value
    @After fun restore() { AccessibilitySettingsHolder.textScaleHint.value = savedScale }
    private val weekly = WeeklyReportResponse("2026-09-08", "2026-09-14", (8..14).map {
        CalendarDayItem(java.time.LocalDate.of(2026, 9, it).toString(), if (it in listOf(8,10,11,13,14)) "COMPLETED" else "REST")
    }, 5, 7, null, listOf(WeeklyMaterialItem("WOOD", "나뭇가지", 3), WeeklyMaterialItem("FIRE", "받침돌", 2)))
    private val cards = listOf(CardHistoryItem("가볍게 걷기", "WOOD", "나뭇가지", "유산소", "2026-09-14T03:00:00Z"),
        CardHistoryItem("벽 짚고 밀기", "FIRE", "받침돌", "근력", "2026-09-13T10:00:00Z"))
    private val selectedDay = JournalToday(
        CardWindowResponse("SELECTED", "2026-09-14", "offline-journal", emptyList(), "option-1", "walk-1", "READY"),
        CardRevealResponse("walk-1", "SENSOR_STEPS", "가볍게 걷기", "주변의 길을 편안하게 걸어요.", "WOOD", "유산소", 1000, "걸음", "READY",
            fortune_text = null, lucky_location = "동네", line_text = "오늘 익숙한 길에서 1,000걸음 걸어보기"))

    @Composable private fun Stage(onGo: () -> Unit = {}, waist: WaistEstimateUi = WaistEstimateUi.Available(BigDecimal("79.2"), null), failed: Boolean = false,
        today: JournalLoad<JournalToday> = JournalLoad.Ready(selectedDay),
        exercises: JournalLoad<List<ExerciseMissionRecordItem>>? = null, onRefresh: () -> Unit = {}) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val resolver = remember(context) {
            val normalType = android.content.res.Configuration(context.resources.configuration).apply { fontWeightAdjustment = 0 }
            androidx.compose.ui.text.font.createFontFamilyResolver(context.createConfigurationContext(normalType))
        }
        CompositionLocalProvider(androidx.compose.ui.platform.LocalFontFamilyResolver provides resolver) {
        TMTNv1Theme {
            Column(Modifier.fillMaxSize().testTag("journal-stage")) {
                Box(Modifier.weight(1f)) {
                    JournalScreen(if (failed) JournalLoad.Failed else JournalLoad.Ready(weekly), JournalLoad.Ready(cards),
                        today, null, waist, false, onRefresh, onGo, {}, {}, exercises = exercises)
                }
                com.tmtn.app.ui.nav.BottomNavBar(com.tmtn.app.ui.nav.MainTab.REFERENCE, {})
            }
        }
        }
    }

    @Test fun weeklyArticleUsesRecordsAndDailyNavigationWorks() {
        compose.setContent { Stage() }
        compose.onNodeWithText("5일의 실천,\n이번 주에 남았어요.").assertIsDisplayed()
        capture("weekly-top")
        compose.onNodeWithText("실천한 발자국").performScrollTo()
        compose.onAllNodesWithText("가볍게 걷기", useUnmergedTree = true).onFirst().assertExists()
        capture("weekly-records")
        compose.onNode(hasText("일간면") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("내가 고른 한 장이\n오늘을 기다려요.").assertIsDisplayed()
        capture("daily-top")
        compose.onNodeWithText("오늘 고른 카드").performScrollTo()
        capture("daily-card")
        compose.onNodeWithText("오늘의 한 문장").performScrollTo()
        capture("daily-sentence")
    }

    @Test fun waistRemainsASeparateTabAndNavigationIsReachable() {
        var homeClicks = 0
        compose.setContent { Stage(onGo = { homeClicks++ }) }
        compose.onNodeWithText("이번 호에 끼워둔 내 기록").performScrollTo()
        capture("personal-record")
        compose.onNodeWithText("허리둘레", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("79.2").performScrollTo().assertIsDisplayed()
        capture("waist")
        compose.onNodeWithText("오늘의 카드로 가기", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, homeClicks) }
    }

    @Test fun failedReportDoesNotBlockArticlesOrInventResults() {
        compose.setContent { Stage(failed = true, waist = WaistEstimateUi.Unavailable) }
        compose.onNodeWithText("생활 읽을거리").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("소금통보다,\n첫 한입을 먼저.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("75.6").assertCountEquals(0)
        compose.onAllNodesWithText("약 21등").assertCountEquals(0)
    }

    @Test fun chosenStoryAndWaistTabSurviveEditionSwitching() {
        compose.setContent { Stage() }
        compose.onNodeWithText("당뇨", useUnmergedTree = true).performScrollTo().performClick()
        capture("diabetes")
        compose.onNodeWithText("허리둘레", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("네 가지 이야기", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNode(hasText("당뇨") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("허리둘레", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNode(hasText("일간면") and hasClickAction()).performClick()
        compose.onNode(hasText("주간면") and hasClickAction()).performClick()
        compose.onNode(hasText("허리둘레") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("79.2").performScrollTo().assertIsDisplayed()
    }

    @Test fun largeTextWeeklyDatesRemainReadableAndSelectable() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.8f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { Stage() }
            }
        }
        compose.onNodeWithText("날짜를 눌러 읽기").performScrollTo()
        compose.onNodeWithContentDescription("9월 14일, 실천").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText("14").assertIsDisplayed()
        capture("weekly-large-320")
    }

    @Test fun completedDayInvitesReflectionInsteadOfStartingAgain() {
        compose.setContent { Stage(today = JournalLoad.Ready(selectedDay.copy(
            window = selectedDay.window.copy(challenge_state = "COMPLETED"), card = selectedDay.card?.copy(state = "COMPLETED")))) }
        compose.onNode(hasText("일간면") and hasClickAction()).performClick()
        compose.onNodeWithText("오늘의 한 장,\n내 기록에 남았어요.").assertIsDisplayed()
        capture("daily-completed")
        compose.onNodeWithText("오늘 잘 맞았던 순간을\n기억해둘까요?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("오늘 고른 실천은,\n언제 해보면 좋을까요?").assertDoesNotExist()
    }

    @Test fun pausedCardKeepsTheReturnPathAndDoesNotInviteNewSelection() {
        var returned = false
        compose.setContent { Stage(onGo = { returned = true }, today = JournalLoad.Ready(selectedDay.copy(
            window = selectedDay.window.copy(challenge_state = "PAUSED"), card = selectedDay.card?.copy(state = "PAUSED")))) }
        compose.onNode(hasText("일간면") and hasClickAction()).performClick()
        compose.onNodeWithText("잠깐 멈춰둔 한 장,\n다시 펼쳐도 괜찮아요.").assertIsDisplayed()
        compose.onAllNodesWithText("홈에서 이어서 보기").onFirst().performScrollTo().performClick()
        compose.runOnIdle { assertTrue(returned) }
        compose.onNodeWithText("시작할 때만 정해볼까?", substring = true).assertDoesNotExist()
    }

    @Test fun extraExercisesStaySeparateAndFollowTheSelectedDay() {
        val extras = listOf(
            ExerciseMissionRecordItem("2026-09-13", "의자에서 일어서기", "FIRE", "받침돌", 1, "2026-09-13T03:00:00Z"),
            ExerciseMissionRecordItem("2026-09-14", "옆으로 한 걸음씩", "WOOD", "나뭇가지", 1, "2026-09-14T03:00:00Z"))
        compose.setContent { Stage(exercises = JournalLoad.Ready(extras), today = JournalLoad.Ready(selectedDay.copy(
            window = selectedDay.window.copy(challenge_state = "COMPLETED"), card = selectedDay.card?.copy(state = "COMPLETED")))) }
        compose.onNodeWithText("5일의 실천,\n이번 주에 남았어요.").assertIsDisplayed()
        compose.onNodeWithText("틈새 운동 · 2회").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("journal-extra-exercises").performScrollTo()
        capture("weekly-extra-exercises")
        compose.onNodeWithContentDescription("9월 14일, 실천").performScrollTo().performClick()
        compose.onNodeWithText("틈새 운동 · 1회").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("의자에서 일어서기").assertDoesNotExist()
        compose.onNode(hasText("일간면") and hasClickAction()).performClick()
        compose.onNodeWithText("틈새 운동 · 1회").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("옆으로 한 걸음씩").assertExists()
        compose.onNodeWithText("의자에서 일어서기").assertDoesNotExist()
        compose.onNodeWithTag("journal-extra-exercises").performScrollTo()
        capture("daily-extra-exercises")
    }

    @Test fun allSevenDatesFitTheNormal320WidthWithoutHorizontalScrolling() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { Stage() }
            }
        }
        compose.onNodeWithText("날짜를 눌러 읽기").performScrollTo()
        val first = compose.onNodeWithContentDescription("9월 8일, 실천").getUnclippedBoundsInRoot()
        val last = compose.onNodeWithContentDescription("9월 14일, 실천").getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag("journal-week-dates").getUnclippedBoundsInRoot()
        assertTrue(first.left >= row.left)
        assertTrue(last.right <= row.right + 1.dp)
        compose.onNodeWithText("14").assertIsDisplayed()
    }

    @Test fun extraExerciseFailureCanRetryWithoutBlockingTheNewspaper() {
        var retries = 0
        compose.setContent { Stage(exercises = JournalLoad.Failed, onRefresh = { retries++ }) }
        compose.onNodeWithText("틈새 운동 기록을 불러오지 못했어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다시 불러오기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("생활 읽을거리").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(350)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "journal-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onNodeWithTag("journal-stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
