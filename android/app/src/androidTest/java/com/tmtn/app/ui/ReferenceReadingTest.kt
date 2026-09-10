package com.tmtn.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.reference.*
import com.tmtn.app.ui.theme.LocalTmtnTextScale
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReferenceReadingTest {
    @get:Rule val compose = createComposeRule()

    private fun result() = TuntunScoreV2Response(
        tuntunIndex = 68.0, physicalScore = 70.0, diabetesScore = 66.0, hypertensionScore = 72.0,
        lifestyleScore = 64.0, aerobicScore = 78.0, strengthScore = 50.0,
        lifestyleAvailableSubcomponentCount = 2, lifestyleScoreSource = "questionnaire",
        componentScores = listOf(
            TuntunComponentScoreV2("physical", "신체", 70.0, true, "양호", "신체 정보에 대한 안내", "test"),
            TuntunComponentScoreV2("diabetes", "당뇨", 66.0, true, "보통", "당뇨 영역 안내", "test"),
            TuntunComponentScoreV2("hypertension", "고혈압", 72.0, true, "양호", "고혈압 영역 안내", "test"),
            TuntunComponentScoreV2("lifestyle", "생활습관", 64.0, true, "보통", "입력한 운동 정보에 대한 안내", "test"),
        ),
        availableComponentCount = 4, availableComponents = listOf("physical", "diabetes", "hypertension", "lifestyle"),
        unavailableComponents = emptyList(), isPartialScore = false, scoreAvailable = true,
        activityWindowStart = "2026-08-31", activityWindowEnd = "2026-09-06", recordedDays = 6,
        missionIntegrationStatus = "pending_evidence", scoreContractVersion = "test", modelVersion = "test",
        calibrationVersion = null, notice = "비진단용 참고 정보", olderAdultNotice = null, isMock = true,
    )

    private fun fixture() = ReferenceState().apply { score.value = result() }

    @Test fun homeAndSummaryKeepTheSameActualFractionalScore() {
        val state = fixture().apply { score.value = result().copy(tuntunIndex = 79.6) }
        val home = com.tmtn.app.ui.cardhome.CardHomeState().apply {
            tuntunIndexValue.value = 80
            tuntunIndexPresentationValue.value = 79.6
        }
        val showHome = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { TMTNv1Theme {
            if (showHome.value) com.tmtn.app.ui.cardhome.TmtnIndexSummaryCard(home) { showHome.value = false }
            else ReferenceSummaryScreen(state)
        } }
        val description = "틈튼지수 79.6점"
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        compose.onNodeWithText("지수 보기 ›").performClick()
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    @Test fun waistSummaryOpensEstimateAndReturnsFromInputsWithoutChangingScore() {
        val state = fixture().apply {
            scoreInputs.value = ScoreInputsResponse("1990년 3월", "여성", 164.0, 58.5, "주 1일", 0, 120, 0)
        }
        val waist = WaistEstimateUi.Available("82.36".toBigDecimal(), java.time.Instant.parse("2026-09-09T00:00:00Z"))
        compose.setContent { TMTNv1Theme {
            ReferencePageTransition(state.step.value) {
                when (it) {
                    ReferenceStep.WAIST -> ReferenceWaistScreen(state, waist) {}
                    ReferenceStep.INPUTS -> ReferenceInputsScreen(state, rememberCoroutineScope(), {}, {}, onLoad = {})
                    else -> ReferenceSummaryScreen(state, waist = waist)
                }
            }
        } }
        compose.onNode(hasText("허리둘레") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithContentDescription("모델이 추정한 허리둘레 약 82.4 센티미터").assertIsDisplayed()
        compose.onNodeWithText("입력 정보 확인").performScrollTo().performClick()
        compose.onNodeWithText("계산에 쓰인 값").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("추정 허리둘레").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithContentDescription("내 틈튼일보 펼치기").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(68.0, state.score.value!!.tuntunIndex!!, 0.0) }
    }

    @Test fun newspaperOpensAndBeaverAreasCanBeExploredThenReturnedToSummary() {
        val state = fixture()
        compose.setContent {
            TMTNv1Theme {
                ReferencePageTransition(state.step.value) {
                    if (it == ReferenceStep.DETAIL) ReferenceDetailScreen(state) else ReferenceSummaryScreen(state)
                }
            }
        }
        compose.onNodeWithContentDescription("내 틈튼일보 펼치기").performClick()
        // Bring the vertically scrolling article into view before scrolling its horizontal tabs.
        compose.onNode(hasText("신체") and isSelectable()).performScrollTo().performClick()
        compose.onNodeWithText("내 몸의 정보가\n한 장의 소식으로.").assertIsDisplayed()
        compose.onNode(hasText("생활습관") and isSelectable()).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("나의 운동 습관,\n한 장에 담았어요.").assertIsDisplayed()
        compose.onNodeWithContentDescription("틈튼지수 64점").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithContentDescription("내 틈튼일보 펼치기").assertIsDisplayed()
    }

    @Test fun partialDataNeverBecomesAnOverallScoreOrAnUnavailableNumber() {
        val state = fixture().apply {
            score.value = result().copy(availableComponentCount = 1, isPartialScore = true,
                componentScores = listOf(
                    TuntunComponentScoreV2("physical", "신체", 99.0, false, null, "정보 부족 안내", "test"),
                    result().componentScores.last(),
                ))
        }
        compose.setContent { TMTNv1Theme { ReferenceDetailScreen(state) } }
        compose.onNodeWithContentDescription("틈튼지수 68점").assertDoesNotExist()
        compose.onNodeWithContentDescription("정보를 더 입력해 주세요").assertIsDisplayed()
        compose.onNode(hasText("신체") and isSelectable()).performScrollTo().performClick()
        compose.onNodeWithText("99점").assertDoesNotExist()
        compose.onNodeWithText("계산할 정보가 부족해요. 입력 정보를 확인해 주세요.").performScrollTo().assertIsDisplayed()
    }

    @Test fun failedInputsHaveRetryAndRetainZeroAndDecimalValues() {
        val state = fixture()
        var requests = 0
        compose.setContent {
            TMTNv1Theme {
                ReferenceInputsScreen(state, rememberCoroutineScope(), {}, {}, onLoad = {
                    requests++
                    if (requests == 1) state.errorMessage.value = "test request failed"
                    else state.scoreInputs.value = ScoreInputsResponse("1990년 3월", "여성", 164.0, 58.5,
                        "주 0일", 0, 0, 0)
                })
            }
        }
        compose.onNodeWithText("입력 정보를 불러오지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithText("58.5 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("주 0일").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun inputEditingKeepsHealthAndExerciseDestinationsDistinct() {
        val state = fixture().apply {
            scoreInputs.value = ScoreInputsResponse("1990년 3월", "여성", 164.0, 58.5, "주 1일", 0, 120, 0)
        }
        var health = 0
        var exercise = 0
        compose.setContent {
            TMTNv1Theme { ReferenceInputsScreen(state, rememberCoroutineScope(), { health++ }, { exercise++ }, onLoad = {}) }
        }
        compose.onNode(hasText("신체 정보") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("운동 정보") and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, health); assertEquals(1, exercise) }
    }

    @Test fun paperRemainsReadableAt320dpAndTwoHundredPercentText() {
        val state = fixture()
        compose.setContent {
            TMTNv1Theme {
                CompositionLocalProvider(LocalTmtnTextScale provides 2f) {
                    Box(Modifier.width(320.dp).testTag("paper")) { ReferenceDetailScreen(state) }
                }
            }
        }
        compose.onNodeWithText("읽는 법").assertIsDisplayed().performClick()
        compose.onNodeWithText("닫기").assertIsDisplayed().performClick()
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        val count = texts.fetchSemanticsNodes().size
        val pageBounds = compose.onNodeWithTag("paper").fetchSemanticsNode().boundsInRoot
        repeat(count) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val bounds = texts[index].fetchSemanticsNode().boundsInRoot
            if (bounds.width > 0f) {
                assertTrue("Text outside page", bounds.left >= pageBounds.left - 1f && bounds.right <= pageBounds.right + 1f)
            }
            // Line coordinates use the paragraph constraints, including centered labels;
            // wrap-content Text may report a smaller size than that paragraph.
            layouts.forEach { layout ->
                assertFalse("Height clipped: " + layout.layoutInput.text.text, layout.didOverflowHeight)
                repeat(layout.lineCount) { line ->
                    assertTrue("Right edge clipped: " + layout.layoutInput.text.text,
                        layout.getLineRight(line) <= layout.layoutInput.constraints.maxWidth + 1f)
                    assertTrue("Left edge clipped: " + layout.layoutInput.text.text, layout.getLineLeft(line) >= -1f)
                }
            }
        }
    }

    @Test fun peerPreviewChangesOnlyTheSelectedReferenceAndNeverTheScore() {
        val state = fixture()
        compose.setContent {
            TMTNv1Theme {
                ReferenceDetailScreen(state, listOf(
                    ScorePeerPositionUi("physical", "신체", 35, "또래", "예시 기준"),
                    ScorePeerPositionUi("diabetes", "당뇨", 48, "또래", "예시 기준"),
                ))
            }
        }
        compose.onNode(hasText("신체") and isSelectable()).performScrollTo().performClick()
        compose.onNodeWithContentDescription("100명 중 약 35번째").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("당뇨") and isSelectable()).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("100명 중 약 48번째").assertIsDisplayed()
        compose.runOnIdle { assertEquals(68.0, state.score.value!!.tuntunIndex!!, 0.0) }
    }

    @Test fun currentScoresDoNotNeedDevelopmentCopyOrPretendToBeRanks() {
        val state = fixture().apply {
            score.value = result().copy(notice = "현재 화면의 건강영역 점수는 연동 확인용 Mock 값입니다.", olderAdultNotice = "고령자 참고 안내")
        }
        compose.setContent { TMTNv1Theme { ReferenceDetailScreen(state) } }
        compose.onNodeWithContentDescription("틈튼지수 68점").assertIsDisplayed()
        compose.onNodeWithText("새 모델 연결 후", substring = true).assertDoesNotExist()
        compose.onNodeWithText("표시 예시", substring = true).assertDoesNotExist()
        compose.onNodeWithText("35번째쯤").assertDoesNotExist()
        compose.onNodeWithText("읽는 법").performClick()
        compose.onNodeWithText("Mock", substring = true).assertDoesNotExist()
        compose.onNodeWithText("비진단용 참고 정보").assertIsDisplayed()
        compose.onNodeWithText("고령자 참고 안내").performScrollTo().assertIsDisplayed()
    }
}
