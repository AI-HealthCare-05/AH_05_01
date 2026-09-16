package com.tmtn.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

class SheetFeedbackUiTest {
    @get:Rule val compose = createComposeRule()
    private fun state(expected: String? = null, onRequest: () -> Unit = {}): CardHomeState {
        val api = java.lang.reflect.Proxy.newProxyInstance(CardHomeApi::class.java.classLoader, arrayOf(CardHomeApi::class.java)) { _, method, _ ->
            check(method.name == expected); onRequest()
            Response.error<Any>(503, "".toResponseBody())
        } as CardHomeApi
        return CardHomeState(serviceDateProvider = { "2026-09-15" }, missionApiProvider = { api }).apply {
            setId.value = "offline"; step.value = CardHomeStep.DECK_PICK
            optionIds.value = listOf("one", "two", "three")
        }
    }
    @Composable private fun Flow(state: CardHomeState) {
        TMTNv1Theme { Box(Modifier.fillMaxSize()) {
            CardHomeFlow(state = state, hasSensorPermissions = { true }, onStartSensorTracking = { _, _, _, _ -> }, onStopSensorTracking = {}, onOpenSettings = {})
        } }
    }

    @Test fun savingRestHasOneWindowAndCannotBeDismissedUntilFinished() {
        val state = state().apply { showRestDaySheet.value = true; isLoading.value = true }
        compose.setContent { Flow(state) }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("쉼으로 저장 중…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("닫기").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.closeRestDaySheet(); assertTrue(state.showRestDaySheet.value); state.isLoading.value = false }
        compose.onNodeWithText("닫기").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun eachTransitionDisablesItsActionsInsideTheOriginalSheet() {
        val state = state().apply { showRestCancelSheet.value = true; isLoading.value = true }
        compose.setContent { Flow(state) }
        val variants = listOf(
            "그대로 쉬기" to { state.showRestCancelSheet.value = true },
            "포기로 바꾸기" to { state.showRestToGiveUpSheet.value = true },
            "그래도 포기하기" to { state.showGiveUpConfirmSheet.value = true },
        )
        for ((label, open) in variants) {
            compose.runOnIdle {
                state.showRestCancelSheet.value = false; state.showRestToGiveUpSheet.value = false; state.showGiveUpConfirmSheet.value = false
                open()
            }
            compose.onAllNodes(isDialog()).assertCountEquals(1)
            compose.onNodeWithText("변경 내용을 저장하고 있어요.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(label).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test fun failedRestCancellationKeepsTheAllowanceAndDisplaysOneInlineError() {
        var requests = 0
        val state = state("cancelRestDay") { requests++ }.apply {
            isTodayRestDay.value = true; restDaysRemainingThisWeek.value = 1; showRestCancelSheet.value = true
        }
        compose.setContent { Flow(state) }
        compose.onNodeWithText("쉬어가기 취소하고 도전하기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onAllNodesWithText(state.errorMessage.value!!).assertCountEquals(1)
        compose.runOnIdle { assertEquals(1, requests); assertTrue(state.isTodayRestDay.value); assertEquals(1, state.restDaysRemainingThisWeek.value) }
        compose.onNodeWithText("그대로 쉬기").performScrollTo().performClick()
        compose.runOnIdle { assertNull(state.errorMessage.value) }
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun failedGiveUpKeepsTheConfirmationAndTheCurrentMission() {
        val card = CardRevealResponse("offline-card", "TIMER", "쉬운 정리", "", "EARTH", "생활습관", 10, "분", "ACTIVE",
            fortune_text = "", lucky_location = "집", line_text = "")
        val state = state("skipChallenge").apply { revealedCard.value = card; showGiveUpConfirmSheet.value = true }
        compose.setContent { Flow(state) }
        compose.onNodeWithText("그래도 포기하기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onAllNodesWithText(state.errorMessage.value!!).assertCountEquals(1)
        compose.runOnIdle { assertEquals(card, state.revealedCard.value); assertTrue(state.showGiveUpConfirmSheet.value) }
    }
}
