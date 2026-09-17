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
        capture("04-proposed-home-top")
        compose.onNodeWithText("허리둘레 자세히 보기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("약 32.4 인치").assertIsDisplayed()
        capture("05-proposed-home-bottom")
        compose.onNodeWithText("허리둘레 자세히 보기").performClick()
        compose.onNodeWithText("설명 접기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("바지 고를 때 참고해 주세요").assertIsDisplayed()
        capture("06-proposed-home-expanded")
        compose.onNodeWithText("설명 접기").performClick()
        compose.onNodeWithText("허리둘레 자세히 보기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("약 32.4 인치").assertIsDisplayed()
    }
    @Test fun proposedLargeTypeRemainsReadable() {
        showHome(compact = true)
        compose.onNodeWithText("허리둘레 자세히 보기").performScrollTo()
        compose.onNodeWithText("약 32.4 인치").assertIsDisplayed()
        capture("07-proposed-320-large-summary")
        compose.onNodeWithText("허리둘레 자세히 보기").performClick()
        compose.onNodeWithContentDescription("모델이 추정한 허리둘레 약 82.4 센티미터, 약 32.4 인치").performScrollTo().assertIsDisplayed()
        capture("08-proposed-320-large-value")
        compose.onNodeWithText("설명 접기").performScrollTo().assertIsDisplayed()
        capture("09-proposed-320-large-explanation")
    }
    @Test fun unselectedHomeKeepsItsOwnHero() {
        showHome(selected = false)
        capture("10-proposed-unselected-top")
        compose.onNodeWithText("허리둘레 자세히 보기").performScrollTo()
        capture("11-proposed-unselected-bottom")
    }

    @Test fun extraExerciseIsAnAvailableActionAfterCompletion() {
        val state = state()
        var used by mutableIntStateOf(0)
        compose.setContent { Page {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExtraExerciseCard(true, used, 2) { state.openExerciseMissionList() }
                TmtnIndexSummaryCard(state)
                HomeWaistCard(estimate, false, {}, {})
            }
        } }
        compose.onNodeWithText("0 / 2회").assertIsDisplayed()
        capture("12-extra-unlocked-home-bottom")
        compose.onNodeWithTag("extra-exercise-entry").performClick()
        compose.runOnIdle { assertEquals(CardHomeStep.EXTRA_LIST, state.step.value); used = 2 }
        compose.onNodeWithText("오늘 받을 수 있는 추가 재료를 모두 모았어요.").assertIsDisplayed()
        compose.onNodeWithTag("extra-exercise-entry").assertHasClickAction()
        capture("13-extra-limit-home-bottom")
    }

    @Test fun lockedExtraDoesNotOfferADisabledTap() {
        showHome()
        compose.onNodeWithTag("extra-exercise-entry").performScrollTo().assertHasNoClickAction()
        compose.onNodeWithContentDescription("오늘 카드 완료 후 이용 가능").assertIsDisplayed()
    }

    @Test fun completedCardKeepsItsRewardAndReadableContent() {
        val card = state().revealedCard.value!!.copy(state = "COMPLETED")
        compose.setContent { Page {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                NoteCard(card, java.time.LocalDate.of(2026, 9, 17))
            }
        } }
        compose.onNodeWithText("오늘의 틈").assertIsDisplayed()
        capture("14-completed-card-top")
        compose.onNodeWithText("실천 완료, 나뭇가지 1개를 받았어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("밝은 곳까지 걷기").assertExists()
    }
}
