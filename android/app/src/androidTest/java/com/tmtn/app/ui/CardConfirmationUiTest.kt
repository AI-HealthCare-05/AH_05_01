package com.tmtn.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.network.model.CardWindowResponse
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

/** All selection and recovery requests are local fakes. No daily card is actually selected. */
class CardConfirmationUiTest {
    @get:Rule val compose = createComposeRule()
    private val card = CardRevealResponse("selected-card", "CHECK", "잠자리 준비하기", "가볍게 정리해요.", "EARTH", "생활습관", 10, "분", "READY",
        fortune_text = "편안한 밤을 준비해요.", lucky_location = "집", line_text = "10분 일찍 잠자리 준비하기")

    private fun state(block: (String, Array<out Any?>?) -> Any): CardHomeState {
        val api = java.lang.reflect.Proxy.newProxyInstance(CardHomeApi::class.java.classLoader, arrayOf(CardHomeApi::class.java)) { _, method, args -> block(method.name, args) } as CardHomeApi
        return CardHomeState(missionApiProvider = { api }).apply {
            setId.value = "today-set"; optionIds.value = listOf("first", "second", "third")
            step.value = CardHomeStep.DECK_PICK; pickedIndex.value = 1; showConfirmDialog.value = true
        }
    }
    @Composable private fun Flow(state: CardHomeState) {
        TMTNv1Theme { Box(Modifier.fillMaxSize()) {
            CardHomeFlow(state = state, hasSensorPermissions = { true }, onStartSensorTracking = { _, _, _, _ -> }, onStopSensorTracking = {}, onOpenSettings = {})
        } }
    }

    @Test fun aLostSelectionReplyRetriesIntoTheAlreadySelectedCard() {
        var selections = 0
        val state = state { name, args -> when (name) {
            "selectCard" -> {
                assertEquals("today-set", args!![0]); assertEquals("second", args[1]); selections++
                if (selections == 1) throw java.io.IOException("reply lost")
                Response.error<CardRevealResponse>(409, "".toResponseBody())
            }
            "getTodayCards" -> Response.success(CardWindowResponse("SELECTED", "2026-09-15", "today-set", emptyList(), "second", card.challenge_id, "READY"))
            "revealChallenge" -> Response.success(card)
            else -> error("Unexpected request: $name")
        } }
        compose.setContent { Flow(state) }
        compose.onNodeWithText("확정하기").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onAllNodesWithText(state.errorMessage.value!!).assertCountEquals(1)
        compose.onNodeWithText("확정하기").performClick()
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, selections)
            assertEquals(CardHomeStep.REVEALED, state.step.value)
            assertEquals(card, state.revealedCard.value)
            assertEquals(card.challenge_id, state.todayChallengeId.value)
            assertEquals("SELECTED", state.drawState.value)
            assertFalse(state.isLoading.value)
        }
    }

    @Test fun openingTheCardUsesOneBusyWindowAndDisablesBothActions() {
        val state = state { _, _ -> error("No request") }.apply { isLoading.value = true }
        compose.setContent { Flow(state) }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("선택한 카드를 확인하고 있어요.").assertIsDisplayed()
        compose.onNodeWithText("카드 여는 중…").assertIsNotEnabled()
        compose.onNode(hasText("다시 고르기") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
    }

    @Test fun chooseAgainClearsThePickAndMakesAllThreeCardsAvailable() {
        val state = state { _, _ -> error("No request") }
        compose.setContent { Flow(state) }
        compose.onNode(hasText("다시 고르기") and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithContentDescription("1번째 카드").assertIsEnabled()
        compose.onNodeWithContentDescription("3번째 카드").assertIsEnabled()
        compose.runOnIdle { assertNull(state.pickedIndex.value); assertNull(state.errorMessage.value) }
    }

    @Test fun recoveryNeverRevealsACardFromADifferentDailySet() {
        var reveals = 0
        val state = state { name, _ -> when (name) {
            "selectCard" -> Response.error<CardRevealResponse>(409, "".toResponseBody())
            "getTodayCards" -> Response.success(CardWindowResponse("SELECTED", "2026-09-16", "tomorrow-set", emptyList(), "first", "other-card", "READY"))
            "revealChallenge" -> { reveals++; Response.success(card) }
            else -> error("Unexpected request: $name")
        } }
        compose.setContent { Flow(state) }
        compose.onNodeWithText("확정하기").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.runOnIdle { assertEquals(0, reveals); assertNull(state.revealedCard.value); assertEquals(CardHomeStep.DECK_PICK, state.step.value) }
    }
}
