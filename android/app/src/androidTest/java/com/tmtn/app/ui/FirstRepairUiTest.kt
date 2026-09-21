package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.CompanionResponse
import com.tmtn.app.network.model.FirstRepairResponse
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.File

class FirstRepairUiTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "first-repair-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun tapThroughGiftPlacementCompletionAndCardEntry() {
        var checkpoint = "ELIGIBLE"
        var cardEntry = false
        fun result() = Response.success(FirstRepairResponse(checkpoint, "WOOD", if (checkpoint == "ELIGIBLE") 0 else 1,
            CompanionResponse(if (checkpoint == "COMPLETED") 1 else 0, if (checkpoint == "COMPLETED") 1 else 0,
                15, 14, emptyList(), emptyList())))
        val state = FirstRepairState(fetch = { result() }, receive = { checkpoint = "GIFT_RECEIVED"; result() },
            complete = { checkpoint = "COMPLETED"; result() })
        compose.setContent {
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { state.refresh() }
            TMTNv1Theme { FirstRepairScreen(state.phase, state.busy, onAction = {
                scope.launch { when (state.phase) {
                    FirstRepairPhase.Welcome -> state.receiveGift()
                    FirstRepairPhase.Gift -> state.fillGap(740)
                    FirstRepairPhase.Complete -> cardEntry = true
                    else -> Unit
                } }
            }) }
        }
        compose.onNodeWithText("첫 재료 받기").assertIsDisplayed()
        capture("01-welcome")
        compose.onNodeWithText("첫 재료 받기").performClick()
        compose.onNodeWithText("첫 재료를 받았어요").assertIsDisplayed()
        capture("02-gift")
        compose.onNodeWithText("이 재료로 틈 메우기").performClick()
        compose.waitUntil(5000) { state.phase == FirstRepairPhase.Complete }
        compose.onNodeWithText("첫 틈을 메웠어요!\n이제 1단계예요.").assertIsDisplayed()
        compose.onNodeWithText("댐에 더한 재료 1개").assertIsDisplayed()
        capture("04-complete")
        compose.onNodeWithText("오늘의 카드 고르기").performClick()
        compose.runOnIdle { assertTrue(cardEntry) }
    }

    @Test fun savingPreventsRepeatTapsAndKeepsTheSameLayout() {
        var calls = 0
        compose.setContent { TMTNv1Theme { FirstRepairScreen(FirstRepairPhase.Placing, true, onAction = { calls++ }) } }
        compose.onNodeWithText("틈을 메우는 중").assertIsNotEnabled()
        capture("03-placing")
        assertEquals(0, calls)
    }

    @Test fun failureKeepsGiftVisibleAndRetryAvailable() {
        var retried = false
        compose.setContent { TMTNv1Theme { FirstRepairScreen(FirstRepairPhase.Error, false,
            failedOperation = FirstRepairOperation.Complete, onAction = { retried = true }) } }
        compose.onNodeWithText("나뭇가지 1개").assertIsDisplayed()
        capture("05-retry")
        compose.onNodeWithText("다시 틈 메우기").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun largeTextOnNarrowScreenCanReadAndReachPrimaryAction() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                TMTNv1Theme { Box(Modifier.width(320.dp).fillMaxHeight()) {
                    FirstRepairScreen(FirstRepairPhase.Gift, false, onAction = {})
                } }
            }
        }
        compose.onNodeWithText("나뭇가지 1개").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 재료로 틈 메우기").assertIsDisplayed().assertIsEnabled()
        capture("06-large-type-320")
        compose.onNodeWithText("준비됐지? 저 빈틈에 함께 놓아보자.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 재료로 틈 메우기").assertIsDisplayed()
        capture("06-large-type-320-guide")
    }

    @Test fun reducedMotionRetainsGiftAndClearCompletion() {
        var phase by mutableStateOf(FirstRepairPhase.Gift)
        compose.setContent { TMTNv1Theme { FirstRepairScreen(phase, phase == FirstRepairPhase.Placing,
            reducedMotion = true, onAction = {}) } }
        compose.runOnIdle { phase = FirstRepairPhase.Placing }
        compose.onNodeWithText("첫 빈틈에 더하는 중").assertIsDisplayed()
        compose.runOnIdle { phase = FirstRepairPhase.Complete }
        compose.onNodeWithText("오늘의 카드 고르기").assertIsEnabled()
        capture("07-reduced-motion")
    }
}
