package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.TokenHolder
import com.tmtn.app.network.model.CompanionResponse
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FirstDamUiTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "first-dam-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun dam(stage: Int = 0, total: Int = 0) = CompanionResponse(stage, total, if (stage == 0) 5 else 70,
        if (stage == 0) 5 - total else 70 - total, emptyList(), emptyList())

    @Test fun firstRevealExplainsActualStageAndContinuesToCards() {
        var continued = 0
        compose.setContent { TMTNv1Theme { FirstDamScreen("지우", dam(), false, false, {}, {}, { continued++ }) } }
        compose.onNodeWithText("모은 재료 0개").assertIsDisplayed()
        compose.onNodeWithText("첫 복구 단계까지 재료 5개가 더 필요해요.").assertIsDisplayed()
        compose.onNodeWithTag("first-dam-art-0").assertIsDisplayed()
        capture("first-dam-zero")
        compose.onNodeWithText("오늘 카드 만나기").performClick()
        compose.runOnIdle { assertEquals(1, continued) }
    }

    @Test fun existingMaterialsArePreservedWhenReturningToTheIntroduction() {
        compose.setContent { TMTNv1Theme { FirstDamScreen("지우", dam(3, 41), false, false, {}, {}, {}) } }
        compose.onNodeWithText("모은 재료 41개").assertIsDisplayed()
        compose.onNodeWithTag("first-dam-art-3").assertIsDisplayed()
        compose.onNodeWithText("다음 복구 단계까지 재료 29개가 더 필요해요.").assertIsDisplayed()
        capture("first-dam-existing")
    }

    @Test fun failedReadHasRetryAndDoesNotBlockFirstCardSelection() {
        var retries = 0
        var continues = 0
        compose.setContent { TMTNv1Theme { FirstDamScreen("", null, false, true, { retries++ }, null, { continues++ }) } }
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
        compose.onNodeWithTag("first-dam-art-0").assertDoesNotExist()
        compose.onNodeWithText("모은 재료 0개").assertDoesNotExist()
        compose.onNodeWithText("다시 불러오기").performClick()
        capture("first-dam-error")
        compose.onNodeWithText("오늘 카드 만나기").performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, continues) }
    }

    @Test fun largeTypeKeepsTheExplanationScrollableAndTheActionAccessible() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1.8f)) {
                TMTNv1Theme { Box(Modifier.width(320.dp).fillMaxHeight()) { FirstDamScreen("지우", dam(), false, false, {}, {}, {}) } }
            }
        }
        compose.onNodeWithText("첫 복구 단계까지 재료 5개가 더 필요해요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("오늘 카드 만나기").assertIsDisplayed().assertIsEnabled()
        capture("first-dam-large-320")
    }

    @Test fun inputSummaryLeadsToPersistedFirstRevealAndCanResumeAfterRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OnboardingCheckpoint.init(context)
        val oldStep = OnboardingCheckpoint.pendingStep()
        val oldToken = TokenHolder.accessToken
        try {
            TokenHolder.accessToken = null
            val state = OnboardingState().apply {
                name.value = "지우"; nickname.value = "지우"; gender.value = "MALE"
                heightCm.value = "172"; weightKg.value = "73"; strengthWeeklyCount.value = 3
                returnToInputSummary()
            }
            compose.setContent { TMTNv1Theme { A15CompleteScreen(state, state::openFirstDam) } }
            compose.onNodeWithText("근력 주 3일 · 유산소 0분").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                state.strengthWeeklyCount.value = 5
                state.strengthWeekdays.value = (0..6).toSet()
            }
            compose.onNodeWithText("근력 주 7일 · 유산소 0분").assertIsDisplayed()
            compose.runOnIdle { state.strengthWeekdays.value = null }
            compose.onNodeWithText("근력 주 5일 이상 · 유산소 0분").assertIsDisplayed()
            compose.onNodeWithText("첫 댐 만나기").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(OnboardingStep.FIRST_DAM, state.step.value)
                assertTrue(state.canReturnToInputSummary)
                assertEquals(OnboardingStep.FIRST_DAM, OnboardingCheckpoint.pendingStep())
                TokenHolder.accessToken = "offline-fixture"
                val resumed = OnboardingState()
                assertEquals(OnboardingStep.FIRST_DAM, resumed.step.value)
                assertFalse(resumed.canReturnToInputSummary)
                state.returnToInputSummary()
                assertEquals(OnboardingStep.A15_COMPLETE, OnboardingCheckpoint.pendingStep())
            }
        } finally {
            TokenHolder.accessToken = oldToken
            if (oldStep == null) OnboardingCheckpoint.clear() else OnboardingCheckpoint.save(oldStep)
        }
    }
}
