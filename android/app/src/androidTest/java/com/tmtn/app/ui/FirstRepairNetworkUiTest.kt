package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

/** 로컬 전용 QA 서버를 명시한 경우에만 실행한다. 실제 서비스 계정에는 접속하지 않는다. */
class FirstRepairNetworkUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realHttpGiftRepairAndServerCheckpointResume() {
        val base = InstrumentationRegistry.getArguments().getString("firstRepairQaUrl")
        assumeTrue("로컬 QA 서버 인자가 필요해요", base == "http://127.0.0.1:8792/api/v1/")
        val api = Retrofit.Builder().baseUrl(base!!).addConverterFactory(GsonConverterFactory.create()).build().create(CardHomeApi::class.java)
        fun freshState() = FirstRepairState(api::getFirstRepair, api::receiveFirstGift, api::completeFirstRepair)
        var state by mutableStateOf(freshState())
        var continued = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            LaunchedEffect(state) { state.refresh() }
            TMTNv1Theme { FirstRepairScreen(state.phase, state.busy, state.failedOperation,
                completedStage = state.data?.companion?.current_stage ?: 1, onAction = { scope.launch {
                    when (state.phase) {
                        FirstRepairPhase.Welcome -> state.receiveGift()
                        FirstRepairPhase.Gift -> state.fillGap(740)
                        FirstRepairPhase.Error -> state.retry(740)
                        FirstRepairPhase.Complete -> continued = true
                        else -> Unit
                    }
                } }) }
        }
        compose.waitUntil(15000) { state.phase != FirstRepairPhase.Loading }
        compose.onNodeWithText("첫 재료 받기").performClick()
        compose.waitUntil(15000) { state.phase == FirstRepairPhase.Gift }
        // 로컬 화면 상태를 버려도 서버가 선물 수령부터 이어준다.
        compose.runOnIdle { state = freshState() }
        compose.waitUntil(15000) { state.phase == FirstRepairPhase.Gift }
        compose.onNodeWithText("이 재료로 틈 메우기").performClick()
        compose.waitUntil(15000) { state.phase == FirstRepairPhase.Complete }
        compose.runOnIdle { state = freshState() }
        compose.waitUntil(15000) { state.phase == FirstRepairPhase.Complete }
        compose.onNodeWithText("댐에 더한 재료 1개").assertIsDisplayed()
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "first-repair-review/08-real-http-complete.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("오늘의 카드 고르기").performClick()
        compose.runOnIdle {
            assertEquals(1, state.data?.companion?.current_stage)
            assertEquals(1, state.data?.companion?.total_materials)
            assertTrue(continued)
        }
    }
}
