package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.network.model.PredictionResultResponse
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.nav.*
import com.tmtn.app.ui.reference.*
import com.tmtn.app.ui.theme.*
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import retrofit2.Response

/** 제품 컴포넌트의 펼침·잠금·기기 글자 확대를 가상 데이터로 검증한다. */
class ForestHomeUiTest {
    @get:Rule val compose = createComposeRule()
    private val estimate = WaistEstimateUi.Available(BigDecimal("82.36"), Instant.parse("2026-09-17T00:00:00Z"))
    private fun state(selected: Boolean = true) = CardHomeState(waist = WaistEstimateState {
        Response.success(listOf(PredictionResultResponse("WAIST_CM_ESTIMATE", BigDecimal("82.36"), "COMPUTED", "2026-09-17T00:00:00Z")))
    }).apply {
        cardServiceDate.value = "2026-09-17"
        drawState.value = if (selected) "SELECTED" else "NONE"
        todayChallengeState.value = if (selected) "READY" else "NONE"
        todayChallengeId.value = if (selected) "preview-card" else null
        if (selected) revealedCard.value = CardRevealResponse("preview-card", "SENSOR_WALK_TIME", "밝은 곳까지 걷기", "집 주변에서 편안하게 걸어요.",
            "WOOD", "유산소", 5, "min", "READY", fortune_text = "오늘 할 수 있는 작은 행동부터 시작해요.", lucky_location = "집 주변", line_text = "오늘 가능한 만큼 시작해요.")
    }
    @Composable private fun Page(compact: Boolean = false, content: @Composable () -> Unit) {
        TMTNv1Theme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (compact) 1.5f else 1f)) {
                Column((if (compact) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize())
                    .background(LocalTmtnColors.current.background).testTag("forest-stage")) {
                    Box(Modifier.weight(1f)) { content() }
                    BottomNavBar(MainTab.HOME, {})
                }
            }
        }
    }
    private fun capture(name: String) {
        if ((!name.startsWith("12-") && !name.startsWith("13-") && name.contains("bottom")) || name.contains("expanded") || name.contains("large-summary") || name.contains("large-explanation")) {
            compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        }
        compose.waitForIdle()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "forest-ui-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onNodeWithTag("forest-stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun showHome(compact: Boolean = false, selected: Boolean = true) {
        val state = state(selected)
        compose.setContent { Page(compact) { CardHomeScreen(state, rememberCoroutineScope(), showDebugTools = false) } }
        compose.waitUntil(10_000) { state.waist.ui.value is WaistEstimateUi.Available }
    }
    @Test fun proposedHomeKeepsRealContextAndExpandCollapseFlow() {
        showHome()
        compose.onNodeWithTag("home-waist").performScrollTo().performClick()
        compose.onNodeWithText("설명 접기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(estimate.readingDescription).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("설명 접기").performScrollTo().performClick()
        compose.onNodeWithTag("home-waist").performScrollTo().assertHasClickAction()
    }
    @Test fun proposedLargeTypeRemainsReadable() {
        showHome(compact = true)
        compose.onNodeWithTag("home-waist").performScrollTo().performClick()
        compose.onNodeWithContentDescription(estimate.readingDescription).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("설명 접기").performScrollTo().assertIsDisplayed()
    }
    @Test fun unselectedHomeKeepsItsOwnHero() {
        showHome(selected = false)
        compose.onNodeWithText("오늘도\n한 틈씩.").assertIsDisplayed()
        compose.onNodeWithTag("home-extra").performScrollTo().assertHasNoClickAction()
    }
    @Test fun extraExerciseIsAnAvailableActionAfterCompletion() {
        val state = state().apply { todayChallengeState.value = "COMPLETED" }
        compose.setContent { Page {
            Column(Modifier.fillMaxWidth().padding(20.dp)) { ExtraExerciseHomeEntry(state) }
        } }
        compose.onNodeWithText("오늘의 운동 5가지 보기").assertIsDisplayed()
        compose.onNodeWithTag("home-extra").performClick()
        compose.runOnIdle {
            assertEquals(CardHomeStep.EXTRA_LIST, state.step.value)
            state.homeExerciseProgress.value = HomeExerciseProgress("2026-09-17", 2, 0)
        }
        compose.onNodeWithText("오늘 두 번 완료 · 운동 목록 보기").assertIsDisplayed()
        compose.onNodeWithTag("home-extra").assertHasClickAction()
    }
    @Test fun lockedExtraDoesNotOfferADisabledTap() {
        showHome()
        compose.onNodeWithTag("home-extra").performScrollTo().assertHasNoClickAction()
        compose.onNodeWithText("잠김", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun completedCardKeepsItsRewardAndReadableContent() {
        val state = state().apply { todayChallengeState.value = "COMPLETED" }
        var viewed = false
        compose.setContent { Page {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                HomeCompletionPanel(state, { viewed = true }, {})
            }
        } }
        compose.onNodeWithText("완료한 카드 보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, viewed) }
        compose.onNodeWithText("오늘의 틈").assertDoesNotExist()
    }
}
