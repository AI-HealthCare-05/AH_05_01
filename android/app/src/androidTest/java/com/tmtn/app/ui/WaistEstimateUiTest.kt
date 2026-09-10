package com.tmtn.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.reference.*
import com.tmtn.app.ui.theme.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WaistEstimateUiTest {
    @get:Rule val compose = createComposeRule()
    private val estimate = WaistEstimateUi.Available("82.36".toBigDecimal(), Instant.parse("2026-09-09T00:00:00Z"))

    @Test fun estimateIsClearlyCmAndNotMeasuredOrRanked() {
        compose.setContent { TMTNv1Theme { ReferenceWaistScreen(ReferenceState(), estimate) {} } }
        compose.onNodeWithText("추정 허리둘레").assertIsDisplayed()
        compose.onNodeWithContentDescription("모델이 추정한 허리둘레 약 82.4 센티미터").assertIsDisplayed()
        compose.onNodeWithText("줄자로 잰 값과는 달라요").assertIsDisplayed()
        compose.onNodeWithText("82.4점").assertDoesNotExist()
        compose.onNodeWithText("82.4%").assertDoesNotExist()
    }

    @Test fun failedReadCanBeRetriedInPlace() {
        var retries = 0
        val result = mutableStateOf<WaistEstimateUi>(WaistEstimateUi.Failed)
        compose.setContent { TMTNv1Theme { ReferenceWaistScreen(ReferenceState(), result.value) { retries++; result.value = estimate } } }
        compose.onNodeWithText("추정값을 불러오지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithContentDescription("모델이 추정한 허리둘레 약 82.4 센티미터").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun unavailableResultDoesNotClaimMissingInputOrDisplayZero() {
        compose.setContent { TMTNv1Theme { ReferenceWaistScreen(ReferenceState(), WaistEstimateUi.Unavailable) {} } }
        compose.onNodeWithText("아직 확인할 추정값이 없어요").assertIsDisplayed()
        compose.onNodeWithText("0.0").assertDoesNotExist()
        compose.onNodeWithText("정보가 부족해요", substring = true).assertDoesNotExist()
    }

    @Test fun twoHundredPercentTextKeepsValueUnitAndInputNavigationAvailable() {
        val state = ReferenceState()
        compose.setContent { TMTNv1Theme { CompositionLocalProvider(LocalTmtnTextScale provides 2f) {
            Box(Modifier.width(320.dp)) { ReferenceWaistScreen(state, estimate) {} }
        } } }
        compose.onNodeWithContentDescription("모델이 추정한 허리둘레 약 82.4 센티미터").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("입력 정보 확인").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ReferenceStep.INPUTS, state.step.value); state.goBack(); assertEquals(ReferenceStep.LOADING, state.step.value) }
    }
}
