package com.tmtn.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

/** 실제 제품 컴포넌트의 탭·스크롤·복귀를 합성 세션으로 검사한다. 회원 API는 호출하지 않는다. */
class HomeJourneyAuditUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun extraListBackUsesTheEntryOrigin() {
        val state = CardHomeState().apply {
            step.value = CardHomeStep.HOME; openExerciseMissionList()
            exerciseMissionsToday.value = ExerciseMissionsTodayResponse(true, 0, 5, 5, emptyList())
        }
        compose.setContent { TMTNv1Theme {
            ExerciseMissionListScreen(state, rememberCoroutineScope(), loadToday = {})
        } }
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.runOnIdle {
            assertEquals(CardHomeStep.HOME, state.step.value)
            state.step.value = CardHomeStep.COMPLETED; state.openExerciseMissionList()
        }
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.runOnIdle { assertEquals(CardHomeStep.COMPLETED, state.step.value) }
    }

    @Test fun restoredPausedExerciseReturnsHomeAndReopensWithoutStartingOrLosingProgress() {
        val session = ExerciseMissionSessionResponse(UUID.randomUUID(), "PAUSED", "SENSOR_WALKING_DURATION",
            "천천히 걷기", "WOOD", "나뭇가지", 300, null, 72, 0, null)
        val state = CardHomeState().apply { step.value = CardHomeStep.EXTRA_RUNNING; activeExerciseSession.value = session }
        var starts = 0
        compose.setContent { TMTNv1Theme {
            val scope = rememberCoroutineScope()
            if (state.step.value == CardHomeStep.HOME) CardHomeScreen(state, scope)
            else ExerciseMissionRunningScreen(state, scope, onStartWalking = { starts++ }, onStartWalkingResume = { starts++ })
        } }
        compose.onNodeWithText("일시정지됨").assertExists()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("틈새 운동 일시정지 · 돌아가기").assertIsDisplayed().performClick()
        compose.onNodeWithText("일시정지됨").assertExists()
        compose.runOnIdle {
            assertNull(state.selectedExerciseOption.value)
            assertEquals(session, state.activeExerciseSession.value)
            assertEquals(0, starts)
        }
    }

    @Test fun selectedHomePrimaryLoadsTheMissingCardAndEntersItsAction() {
        var requests = 0
        val card = CardRevealResponse("audit-card", "CHECK", "해낸 일 표시하기", "", "METAL", "생활습관", 1, "가지", "READY",
            fortune_text = "", lucky_location = "집", line_text = "")
        val api = java.lang.reflect.Proxy.newProxyInstance(CardHomeApi::class.java.classLoader,
            arrayOf(CardHomeApi::class.java)) { _, method, _ ->
            check(method.name == "revealChallenge"); requests++; Response.success(card)
        } as CardHomeApi
        val state = CardHomeState(missionApiProvider = { api }).apply {
            step.value = CardHomeStep.HOME; drawState.value = "SELECTED"; todayChallengeId.value = "audit-card"
            todayChallengeState.value = "READY"
        }
        compose.setContent { TMTNv1Theme { CardHomeScreen(state, rememberCoroutineScope()) } }
        compose.onNodeWithText("이 행동 시작하기").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, requests)
            assertEquals(CardHomeStep.CHALLENGE_CHECK, state.step.value)
            assertEquals(card, state.revealedCard.value)
        }
    }

    @Test fun loadingHomeDisablesBothMissionActions() {
        val state = CardHomeState().apply { step.value = CardHomeStep.HOME; isLoading.value = true }
        compose.setContent { TMTNv1Theme { CardHomeScreen(state, rememberCoroutineScope()) } }
        compose.onNodeWithText("쉬어가기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("오늘의 카드 고르기").performScrollTo().assertIsNotEnabled()
    }

    @Test fun bothExtrasReplaceTheOldCardAndOpenTheDam() {
        var damOpened = false
        val state = CardHomeState().apply {
            cardServiceDate.value = "2026-09-20"; todayChallengeState.value = "COMPLETED"
            homeExerciseProgress.value = HomeExerciseProgress("2026-09-20", 2, 0)
        }
        compose.setContent { TMTNv1Theme {
            CardHomeScreen(state, rememberCoroutineScope(), onOpenDam = { damOpened = true })
        } }
        compose.onNodeWithTag("home-selected-card").assertDoesNotExist()
        compose.onNodeWithText("내 댐 보러 가기").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(damOpened) }
    }

    @Test fun weeklyShortcutClosesOldExplanationAndReturnsFromDailyEdition() {
        val requested = mutableStateOf<Int?>(null)
        compose.setContent { TMTNv1Theme {
            com.tmtn.app.ui.journal.JournalScreen(
                weekly = com.tmtn.app.ui.journal.JournalLoad.Loading,
                collection = com.tmtn.app.ui.journal.JournalLoad.Loading,
                today = com.tmtn.app.ui.journal.JournalLoad.Loading,
                score = null, waist = com.tmtn.app.ui.reference.WaistEstimateUi.Unavailable,
                refreshing = false, onRefresh = {}, onGoPickCard = {}, onEditInformation = {}, onRetryWaist = {},
                requestedEdition = requested.value, onEditionOpened = { requested.value = null },
            )
        } }
        compose.onNodeWithText("일간면").performClick().assertIsSelected()
        compose.onNodeWithText("내 활동 비교 · 계산 이야기").performClick()
        compose.onNodeWithText("내 활동과 계산 이야기").assertIsDisplayed()
        compose.runOnIdle { requested.value = 0 }
        compose.onNodeWithText("내 활동과 계산 이야기").assertDoesNotExist()
        compose.onNodeWithText("주간면").assertIsSelected()
    }
}
