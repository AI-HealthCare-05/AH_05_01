package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.reference.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScoreNewspaperUiTest {
    @get:Rule val compose = createComposeRule()
    private fun fixture() = ReferenceState().apply {
        score.value = TuntunScoreV2Response(
            80.0, 76.0, 54.0, 62.0, 91.0, 84.0, 98.0, 2, "questionnaire",
            listOf("physical" to "신체", "diabetes" to "당뇨", "hypertension" to "고혈압", "lifestyle" to "생활습관").mapIndexed { i, (key, label) ->
                TuntunComponentScoreV2(key, label, listOf(76.0,54.0,62.0,91.0)[i], true, "보통", "$label 영역의 참고 안내예요.", "test")
            }, 4, listOf("physical","diabetes","hypertension","lifestyle"), emptyList(), false, true,
            "2026-09-04", "2026-09-10", 4, "pending_evidence", "test", "test", null, "비진단용 참고 정보", null, true,
        )
        editorial.inputs.value = EditorialLoad.Ready(ScoreInputsResponse("1993년 4월", "남성", 172.0, 68.5, "주 2회 · 적당히", 0, 60, 15))
        editorial.weekly.value = EditorialLoad.Ready(WeeklyReportResponse("2026-09-04", "2026-09-10",
            (4..10).map { day -> CalendarDayItem("2026-09-${day.toString().padStart(2,'0')}", if (day in listOf(4,6,8,10)) "COMPLETED" else "REST") },
            4, 7, "저녁", emptyList()))
    }

    private fun tab(name: String) = compose.onNode(hasText(name) and isSelectable()).performScrollTo().performClick()
    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "score-native-qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun articlesUseDifferentSupportingMaterialAndDoNotRepeatTheLifestyleStory() {
        val state = fixture()
        compose.setContent { TMTNv1Theme { ReferenceDetailScreen(state) } }
        compose.onNodeWithTag("news-life").assertExists()
        compose.onNodeWithTag("news-inputs-overall").assertExists()
        tab("신체")
        compose.onNodeWithText("68.5 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("news-life").assertDoesNotExist()
        tab("당뇨")
        compose.onNode(hasText("당뇨 기사에 보낸 자료") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("0분 / 주").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("news-life").assertDoesNotExist()
        tab("고혈압")
        compose.onNode(hasText("혈압 기사에 보낸 자료") and hasClickAction()).performScrollTo().assertIsDisplayed()
        tab("생활습관")
        compose.onNodeWithTag("news-practice").performScrollTo()
        compose.onNodeWithContentDescription("9월 10일, 실천 완료").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("news-life").assertDoesNotExist()
        compose.runOnIdle { assertEquals(80.0, state.score.value!!.tuntunIndex!!, 0.0) }
    }

    @Test fun failedSupportingDataNeverErasesTheScoreOrPretendsToBeEmptyRecords() {
        val state = fixture().apply {
            editorial.inputs.value = EditorialLoad.Failed
            editorial.weekly.value = EditorialLoad.Failed
        }
        compose.setContent { TMTNv1Theme { ReferenceDetailScreen(state) } }
        compose.onNodeWithContentDescription("틈튼지수 80점").assertExists()
        compose.onNodeWithText("생활면을 불러오지 못했어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이번 호엔 아직\n실천 기록이 없어요.").assertDoesNotExist()
        compose.onNodeWithText("추정 허리둘레").assertDoesNotExist()
        compose.onNode(hasText("내가 보낸 정보") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("입력 정보를 불러오지 못했어요.").performScrollTo().assertIsDisplayed()
    }

    @Test fun captureTheNativeSummaryNewspaperAndAreaPages() {
        val state = fixture()
        var summary by mutableStateOf(true)
        compose.setContent { TMTNv1Theme {
            if (summary) ReferenceSummaryScreen(state) else ReferenceDetailScreen(state)
        } }
        capture("01-summary")
        compose.runOnIdle { summary = false }
        capture("02-newspaper")
        compose.onNodeWithTag("news-life").performScrollTo()
        capture("03-life")
        tab("신체")
        capture("04-physical")
        compose.onNode(hasText("기본 정보 더 읽기") and hasClickAction()).performScrollTo().performClick()
        capture("05-inputs")
        tab("생활습관")
        compose.onNodeWithTag("news-practice").performScrollTo()
        capture("06-practice")
    }

    @Test fun freshAreaLinksOpenTheRequestedArticleButEditorReturnsKeepTheReadingPlace() {
        val state = fixture().apply { openFactors() }
        compose.setContent { TMTNv1Theme {
            ReferencePageTransition(state.step.value) { page ->
                when (page) {
                    ReferenceStep.FACTORS -> ReferenceFactorsScreen(state, "physical")
                    ReferenceStep.INPUTS -> androidx.compose.material3.TextButton({ state.goBack() }) {
                        androidx.compose.material3.Text("기사로 돌아가기")
                    }
                    else -> ReferenceSummaryScreen(state, onOpenArea = { state.openFactors() })
                }
            }
        } }
        tab("생활습관")
        compose.onNode(hasText("강도별 운동 정보") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("운동 정보 확인·수정") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("기사로 돌아가기").performClick()
        compose.onNode(hasText("생활습관") and isSelectable()).assertIsSelected()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("영역별 소식").assertDoesNotExist()
        compose.runOnIdle { state.openFactors() }
        compose.onNode(hasText("신체") and isSelectable()).assertIsSelected()
    }
}
