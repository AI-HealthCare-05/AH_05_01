package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.journal.*
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Offline UI regression checks at the boundaries changed by this integration. */
class VnextIntegrationUiTest {
    @get:Rule val compose = createComposeRule()
    private fun card() = CardRevealResponse("offline-vnext", "SENSOR_STEPS", "100보 걷기", "편한 속도로 움직여 주세요.",
        "WOOD", "움직임", 100, "보", "ACTIVE", fortune_text = null, lucky_location = null, line_text = null)
    private fun capture(name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "vnext-integration-qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun hardwareFallbackOffersManualCheckWithoutUselessSettingsButton() {
        val state = CardHomeState().apply { revealedCard.value = card(); sensorFallbackReason.value = "HARDWARE" }
        compose.setContent { TMTNv1Theme { SensorPermissionFallbackScreen(state, {}) } }
        compose.onNodeWithText("이 기기에서는 자동 측정을 할 수 없어요").assertIsDisplayed()
        compose.onNodeWithText("설정 열기").assertDoesNotExist()
        capture("hardware-fallback")
        compose.onNodeWithText("직접 체크로 진행하기").performClick()
        compose.runOnIdle { assertEquals(CardHomeStep.CHALLENGE_CHECK, state.step.value) }
    }
    @Test fun permissionFallbackKeepsSettingsAndManualOptions() {
        val state = CardHomeState().apply { revealedCard.value = card(); sensorFallbackReason.value = "PERMISSION" }
        var opened = false
        compose.setContent { TMTNv1Theme { SensorPermissionFallbackScreen(state, { opened = true }) } }
        compose.onNodeWithText("설정 열기").performClick()
        compose.runOnIdle { assertTrue(opened) }
        compose.onNodeWithText("직접 체크로 진행하기").assertIsDisplayed()
    }
    @Test fun activeSensorResumePassesTargetAlongsideResumeCount() {
        val state = CardHomeState().apply { revealedCard.value = card().copy(accumulated_count = 32) }
        var resumed: List<Any>? = null
        compose.setContent { TMTNv1Theme {
            SensorIntroScreen(state, rememberCoroutineScope(), { true }, { id, type, count, target -> resumed = listOf(id, type, count, target) })
        } }
        compose.onNodeWithText("움직임 측정 시작").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("offline-vnext", "SENSOR_STEPS", 32, 100), resumed)
            assertEquals(CardHomeStep.SENSOR_MEASURING, state.step.value)
        }
    }
    @Test fun homeExplainsWhenExtraExerciseUnlocks() {
        val state = CardHomeState().apply { todayChallengeState.value = "READY" }
        compose.setContent { TMTNv1Theme { ExtraExerciseHomeEntry(state) } }
        compose.onNodeWithText("오늘의 카드 다음, 하루 5가지 운동을 만나요.").assertIsDisplayed()
        compose.onNodeWithText("틈새 운동 둘러보기").assertDoesNotExist()
    }
    @Test fun nativeJournalShowsCompositePointsAndTheServersExactDomainRank() {
        compose.setContent { TMTNv1Theme {
            JournalScreen(JournalLoad.Ready(com.tmtn.app.network.model.WeeklyReportResponse("2026-09-07", "2026-09-13", emptyList(), 0, 7, "", emptyList())),
                JournalLoad.Ready(emptyList()), JournalLoad.Failed, peerFixture(75.6), WaistEstimateUi.Unavailable,
                false, {}, {}, {}, {})
        } }
        compose.onNodeWithText("75.6").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("25번째쯤").assertDoesNotExist()
        compose.onNodeWithText("또래 100명 중 약 34등").performScrollTo().assertIsDisplayed()
        capture("journal-latest-peer")
    }
}
