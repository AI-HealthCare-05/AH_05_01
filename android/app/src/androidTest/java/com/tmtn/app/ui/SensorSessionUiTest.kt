package com.tmtn.app.ui

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.sensor.SensorDataHolder
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SensorSessionUiTest {
    @get:Rule val compose = createComposeRule()
    @After fun reset() { SensorDataHolder.resetAll() }

    @Test fun preparingBlocksControlsThenMovementAndPauseFollowTheActualSession() {
        SensorDataHolder.resetAll()
        var pauses = 0; var resumes = 0
        val state = CardHomeState().apply {
            revealedCard.value = CardRevealResponse("offline-step", "SENSOR_STEPS_IN_PLACE", "제자리에서 100보 걷기", "편안한 속도로 움직여요.", "WOOD", "움직임", 100, "보", "ACTIVE", fortune_text = null, lucky_location = null, line_text = null)
        }
        compose.setContent { TMTNv1Theme {
            SensorMeasuringScreen(state, rememberCoroutineScope(), {},
                { pauses++; SensorDataHolder.setSensorPaused(true) },
                { resumes++; SensorDataHolder.setSensorPaused(false) })
        } }
        compose.onNodeWithText("측정을 준비하고 있어요").assertIsDisplayed()
        compose.onNodeWithText("일시정지").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            SensorDataHolder.setServiceRunning(true)
            SensorDataHolder.updateStepInPlaceCount(42)
            SensorDataHolder.updateStepDetectedNow(true)
        }
        compose.onNodeWithText("움직임을 확인했어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("42 걸음").assertIsDisplayed()
        compose.onNodeWithContentDescription("오늘 미션 진행").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.42f, 0f..1f)))
        compose.onNodeWithText("일시정지").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("일시정지됨").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이어서 측정").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, pauses); assertEquals(1, resumes); assertEquals(42, SensorDataHolder.stepInPlaceCount.value) }
        compose.onNodeWithText("완료하기").performScrollTo().assertIsNotEnabled()
    }
}
