package com.tmtn.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.sensor.SensorDataHolder
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

class MissionExitRecoveryUiTest {
    @get:Rule val compose = createComposeRule()
    @After fun reset() { SensorDataHolder.resetAll() }
    private fun state(type: String, step: CardHomeStep, expected: String): CardHomeState {
        val api = java.lang.reflect.Proxy.newProxyInstance(CardHomeApi::class.java.classLoader, arrayOf(CardHomeApi::class.java)) { _, method, _ ->
            check(method.name == expected)
            Response.error<Any>(503, "".toResponseBody())
        } as CardHomeApi
        return CardHomeState(missionApiProvider = { api }).apply {
            setId.value = "offline"; this.step.value = step
            revealedCard.value = CardRevealResponse("offline-card", type, "오늘의 작은 실천", "", "WOOD", "움직임", 100, "보", "ACTIVE",
                fortune_text = "", lucky_location = "집", line_text = "")
            timerElapsedSeconds.value = 84
        }
    }
    @Composable private fun Flow(state: CardHomeState, onStop: () -> Unit = {}) {
        TMTNv1Theme { Box(Modifier.fillMaxSize()) {
            CardHomeFlow(state = state, hasSensorPermissions = { true }, onStartSensorTracking = { _, _, _, _ -> }, onStopSensorTracking = onStop, onOpenSettings = {})
        } }
    }

    @Test fun checkGiveUpFailureStaysInTheSameDialogAndCanBeCancelled() {
        val state = state("CHECK", CardHomeStep.CHALLENGE_CHECK, "skipChallenge").apply { showCheckGiveUpDialog.value = true }
        compose.setContent { Flow(state) }
        compose.onNodeWithText("오늘 미션 포기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onAllNodesWithText(state.errorMessage.value!!).assertCountEquals(1)
        compose.runOnIdle { assertTrue(state.showCheckGiveUpDialog.value); assertEquals("ACTIVE", state.revealedCard.value?.state) }
        compose.onNodeWithText("계속하기").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertNull(state.errorMessage.value) }
    }

    @Test fun failedTimerPauseKeepsTheClockAndRetryInPlace() {
        val state = state("TIMER", CardHomeStep.CHALLENGE_TIMER_RUNNING, "pauseChallenge").apply { showQuitDialog.value = true }
        compose.setContent { TMTNv1Theme { MissionExitDialog(state, rememberCoroutineScope(), checkOnly = false) } }
        compose.onNodeWithText("나중에 이어서 하기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText(state.errorMessage.value!!).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.showQuitDialog.value); assertEquals(84, state.timerElapsedSeconds.value); assertEquals("ACTIVE", state.revealedCard.value?.state) }
    }

    @Test fun failedSensorGiveUpDoesNotStopOrDiscardThePausedMeasurement() {
        SensorDataHolder.resetAll(); SensorDataHolder.setServiceRunning(true); SensorDataHolder.setSensorPaused(true)
        SensorDataHolder.updateStepInPlaceCount(42)
        var stops = 0
        val state = state("SENSOR_STEPS_IN_PLACE", CardHomeStep.SENSOR_MEASURING, "skipChallenge").apply { showQuitDialog.value = true }
        compose.setContent { Flow(state) { stops++ } }
        compose.onNodeWithText("오늘 미션 포기").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onAllNodesWithText(state.errorMessage.value!!).assertCountEquals(1)
        compose.runOnIdle { assertEquals(0, stops); assertEquals(42, SensorDataHolder.stepInPlaceCount.value); assertTrue(state.showQuitDialog.value) }
        compose.onNodeWithText("계속하기").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun aPendingExitHasOneWindowAndNoEnabledActions() {
        val state = state("CHECK", CardHomeStep.CHALLENGE_CHECK, "unused").apply { showCheckGiveUpDialog.value = true; isLoading.value = true }
        compose.setContent { Flow(state) }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        listOf("나중에 이어서 하기", "오늘 미션 포기", "계속하기").forEach { label ->
            val action = compose.onNodeWithText(label)
            if (label != "계속하기") action.performScrollTo()
            action.assertIsNotEnabled()
        }
    }
}
