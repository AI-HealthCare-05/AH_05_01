package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 완료 경로의 옛 카드 재등장과 삭제 자산의 APK 재혼입을 검사한다. 회원 기록을 변경하지 않는다. */
class CardDesignRegressionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun state() = CardHomeState(serviceDateProvider = { "2026-09-20" }).apply {
        cardServiceDate.value = "2026-09-20"
        step.value = CardHomeStep.COMPLETED
        revealedCard.value = CardRevealResponse("design-regression", "CHECK", "해낸 일 표시하기", "",
            "METAL", "기록", 1, "가지", "COMPLETED", fortune_text = "해낸 것을 보면 자신감의 운이 커져요.",
            lucky_location = "집", line_text = "오늘 해낸 일 1가지 표시하기!")
        todayChallengeState.value = "COMPLETED"
        hasMemoToday.value = true
        homeExerciseProgress.value = HomeExerciseProgress("2026-09-20", 1, 1)
        exerciseMissionsToday.value = ExerciseMissionsTodayResponse(true, 1, 2, 1, emptyList())
    }

    @Test fun completedCardUsesCurrentPaperAndKeepsTheServerText() {
        val state = state()
        compose.setContent { TestScreen { CompletedScreen(state, loadSummary = {}) } }
        assertCurrentCard()
        capture("card-design-01-completed")
        compose.onNodeWithTag("mission-card-completed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("오늘 해낸 일 1가지 표시하기!").assertIsDisplayed()
        compose.onNodeWithText("이 행동 시작하기").assertDoesNotExist()
        compose.onNodeWithText("오늘 카드 다시 보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CardHomeStep.REVEALED, state.step.value) }
    }

    @Test fun exerciseListBothBackActionsReturnToTheCurrentCompletedCard() {
        val state = state().apply { openExerciseMissionList() }
        compose.setContent { TestScreen {
            if (state.step.value == CardHomeStep.EXTRA_LIST) {
                BackHandler { state.step.value = previousStepFor(state.step.value, state.extraListOrigin.value)!! }
                ExerciseMissionListScreen(state, rememberCoroutineScope(), loadToday = {})
            } else CompletedScreen(state, loadSummary = {})
        } }
        compose.onNodeWithContentDescription("뒤로").performClick()
        assertCurrentCard()
        compose.onNodeWithText("틈새 운동 둘러보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CardHomeStep.EXTRA_LIST, state.step.value) }
        Espresso.pressBack()
        assertCurrentCard()
        capture("card-design-02-back")
    }

    @Test fun cardReviewKeepsCompletedAndPausedActionsDistinct() {
        val state = state().apply { step.value = CardHomeStep.REVEALED }
        var starts = 0
        compose.setContent { TestScreen { RevealScreen(state, rememberCoroutineScope(), onStartAction = { starts++ }) } }
        assertCurrentCard()
        compose.onNodeWithTag("mission-card-completed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 행동 시작하기").assertDoesNotExist()
        compose.onNodeWithText("진행 중인 미션 확인").assertDoesNotExist()
        capture("card-design-03-review")
        compose.runOnIdle { state.revealedCard.value = state.revealedCard.value!!.copy(state = "PAUSED") }
        compose.onNodeWithTag("mission-card-completed").assertDoesNotExist()
        compose.onNodeWithText("진행 중인 미션 확인").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, starts); assertEquals("PAUSED", state.revealedCard.value!!.state) }
    }

    @Test fun obsoleteCardResourcesAreAbsentFromTheInstalledApk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((name, type) in listOf("tarot_card_front" to "drawable", "tarot_card_back" to "drawable", "noto_sans_kr_vf" to "font")) {
            assertEquals("폐기 자산 재혼입: $name", 0, context.resources.getIdentifier(name, type, context.packageName))
        }
    }

    /** 실제 앱 상위 컨테이너처럼 시스템 상태·탐색 표시줄 공간을 확보한다. */
    @Composable private fun TestScreen(content: @Composable () -> Unit) {
        TMTNv1Theme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() } }
    }

    private fun assertCurrentCard() {
        compose.onNodeWithTag("mission-card").assertExists()
        compose.onNodeWithText("행운의 장소").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("오늘의 목표").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("행운의 위치").assertDoesNotExist()
        compose.onNodeWithText("행운의 숫자").assertDoesNotExist()
        compose.onNodeWithText("해낸 것을 보면 자신감의 운이 커져요.").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val root = File(instrumentation.targetContext.getExternalFilesDir(null), "final-handoff").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(root, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
