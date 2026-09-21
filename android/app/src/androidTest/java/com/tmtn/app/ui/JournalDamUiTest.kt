package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.journal.*
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.TMTNv1Theme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 주간 기록과 별개인 현재 댐의 단계·오류·갱신을 실제 일보 화면에서 확인한다. */
class JournalDamUiTest {
    @get:Rule val compose = createComposeRule()
    private fun dam(stage: Int) = JournalLoad.Ready(CompanionResponse(stage, 30, null, 0, emptyList(), emptyList()))

    @Composable private fun Page(companion: JournalLoad<CompanionResponse>, onRetry: () -> Unit = {}) {
        TMTNv1Theme {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                JournalScreen(
                    weekly = JournalLoad.Ready(WeeklyReportResponse("2026-09-14", "2026-09-20", emptyList(), 1, 7, null, emptyList())),
                    collection = JournalLoad.Ready(emptyList()), today = JournalLoad.Loading,
                    score = null, waist = WaistEstimateUi.Unavailable, refreshing = false,
                    onRefresh = {}, onGoPickCard = {}, onEditInformation = {}, onRetryWaist = {},
                    companion = companion, onRetryCompanion = onRetry,
                )
            }
        }
    }

    @Test fun stageZeroTwoAndFiveUpdateTheImageWithoutChangingWeeklyHistory() {
        var current by mutableStateOf<JournalLoad<CompanionResponse>>(dam(0))
        compose.setContent { Page(current) }
        for (stage in listOf(0, 2, 5)) {
            compose.runOnIdle { current = dam(stage) }
            compose.onNodeWithTag("journal-dam-scene").performScrollTo()
            compose.onNodeWithContentDescription("현재 내 댐 ${stage}단계").assertIsDisplayed()
            compose.onNodeWithText("지금의 내 댐").assertExists()
            compose.onNodeWithText("지금까지 모은 재료 30개").assertExists()
            compose.onNodeWithText("1일의 실천,\n이번 주에 남았어요.").assertExists()
            capture("journal-dam-stage-$stage")
        }
        compose.onNodeWithContentDescription("완성된 댐 앞에서 응원하는 틈튼이").assertExists()
    }

    @Test fun loadingAndFailureRemoveOldArtworkAndRetryRecoversInPlace() {
        var current by mutableStateOf<JournalLoad<CompanionResponse>>(dam(2))
        var retries = 0
        compose.setContent { Page(current) { retries++; current = dam(4) } }
        compose.runOnIdle { current = JournalLoad.Loading }
        compose.onNodeWithTag("journal-dam-art").assertDoesNotExist()
        compose.onNodeWithText("내 댐을 불러오고 있어요.").assertExists()
        compose.runOnIdle { current = JournalLoad.Failed }
        compose.onNodeWithTag("journal-dam-scene").performScrollTo()
        compose.onNodeWithTag("journal-dam-art").assertDoesNotExist()
        capture("journal-dam-unavailable")
        compose.onNodeWithText("댐 다시 불러오기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithContentDescription("현재 내 댐 4단계").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("생활 읽을거리").performScrollTo().assertIsDisplayed()
    }

    @Test fun unknownStageShowsNoInventedScene() {
        compose.setContent { Page(dam(7)) }
        compose.onNodeWithText("내 댐을 불러오지 못했어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("journal-dam-art").assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "final-handoff").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
