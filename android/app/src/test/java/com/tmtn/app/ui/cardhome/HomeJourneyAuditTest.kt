package com.tmtn.app.ui.cardhome

import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.network.model.StreakResponse
import com.tmtn.app.ui.reference.ReferenceState
import com.tmtn.app.ui.reference.ReferenceStep
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

/** 시안 C 적용 후 발견한 실제 복귀·재도전 경로의 회귀 검사. */
class HomeJourneyAuditTest {
    private fun card(status: String, type: String = "CHECK") = CardRevealResponse(
        "audit-card", type, "해낸 일 표시하기", "", "METAL", "생활습관", 1, "가지", status,
        fortune_text = "", lucky_location = "집", line_text = "")

    private fun state(block: (String) -> Any) = CardHomeState(
        serviceDateProvider = { "2026-09-20" },
        missionApiProvider = {
            java.lang.reflect.Proxy.newProxyInstance(CardHomeApi::class.java.classLoader,
                arrayOf(CardHomeApi::class.java)) { _, method, _ -> block(method.name) } as CardHomeApi
        },
    ).apply { step.value = CardHomeStep.HOME; todayChallengeId.value = "audit-card" }

    @Test fun cancellingRestThenRetryingSkippedCardReallyEntersTheChallenge() = runBlocking {
        val calls = mutableListOf<String>()
        var status = "SKIPPED"
        val state = state { name ->
            calls += name
            when (name) {
                "cancelRestDay" -> Response.success(StreakResponse(0, 5, 0, 2))
                "revealChallenge" -> Response.success(card(status))
                "startChallenge" -> { status = "ACTIVE"; Response.success<Any>(null) }
                else -> error(name)
            }
        }.apply { isTodayRestDay.value = true; todayChallengeState.value = "SKIPPED" }
        assertTrue(state.cancelRestDay())
        state.enterInProgressMission(restartSkipped = true)
        assertEquals(listOf("cancelRestDay", "revealChallenge", "startChallenge", "revealChallenge"), calls)
        assertEquals(CardHomeStep.CHALLENGE_CHECK, state.step.value)
        assertEquals("ACTIVE", state.todayChallengeState.value)
        assertFalse(state.isTodayRestDay.value)
    }

    @Test fun retryAfterAcceptedStartAndFailedReadDoesNotStartTwice() = runBlocking {
        var status = "SKIPPED"
        var reads = 0
        var starts = 0
        val state = state { name -> when (name) {
            "revealChallenge" -> if (++reads == 2) Response.error<Any>(503, "".toResponseBody()) else Response.success(card(status))
            "startChallenge" -> { starts++; status = "ACTIVE"; Response.success<Any>(null) }
            else -> error(name)
        } }.apply { isTodayGivenUp.value = true }
        state.restartFromGiveUp()
        assertEquals(CardHomeStep.HOME, state.step.value)
        assertNotNull(state.errorMessage.value)
        state.restartFromGiveUp()
        assertEquals(1, starts)
        assertEquals(CardHomeStep.CHALLENGE_CHECK, state.step.value)
        assertNull(state.errorMessage.value)
    }

    @Test fun rejectedRestartKeepsTheHomeAndDoesNotClaimActive() = runBlocking {
        val state = state { name -> when (name) {
            "revealChallenge" -> Response.success(card("SKIPPED"))
            "startChallenge" -> Response.error<Any>(503, "".toResponseBody())
            else -> error(name)
        } }.apply { isTodayGivenUp.value = true; todayChallengeState.value = "SKIPPED" }
        state.restartFromGiveUp()
        assertEquals(CardHomeStep.HOME, state.step.value)
        assertEquals("SKIPPED", state.todayChallengeState.value)
        assertTrue(state.isTodayGivenUp.value)
        assertFalse(state.isLoading.value)
        assertNotNull(state.errorMessage.value)
    }

    @Test fun cachedCardIsNotRequiredAndPausedTimerNeverResumesOnEntry() = runBlocking {
        val state = state { name ->
            assertEquals("revealChallenge", name)
            Response.success(card("PAUSED", "TIMER").copy(elapsed_seconds = 42))
        }
        assertNull(state.revealedCard.value)
        state.enterInProgressMission(restartSkipped = true)
        assertEquals(CardHomeStep.CHALLENGE_TIMER_PAUSED, state.step.value)
        assertEquals(42, state.timerElapsedSeconds.value)
        assertTrue(state.timerIsPaused.value)
    }

    @Test fun concurrentTapDoesNotSendAnotherRequest() = runBlocking {
        val state = state { error("중복 요청") }.apply { isLoading.value = true }
        state.enterInProgressMission()
        assertEquals(CardHomeStep.HOME, state.step.value)
    }

    @Test fun extraListReturnsToItsOriginAndRestoredRunningReturnsHome() {
        val state = state { error("탐색에 API 불필요") }
        state.openExerciseMissionList()
        assertEquals(CardHomeStep.HOME, previousStepFor(state.step.value, state.extraListOrigin.value))
        state.step.value = CardHomeStep.COMPLETED
        state.openExerciseMissionList()
        assertEquals(CardHomeStep.COMPLETED, previousStepFor(state.step.value, state.extraListOrigin.value))
        assertNull(state.selectedExerciseOption.value)
        assertEquals(CardHomeStep.HOME, previousStepFor(CardHomeStep.EXTRA_RUNNING))
    }

    @Test fun journalShortcutClearsTheOldDetailStackAndRequestsWeeklyEdition() {
        val state = ReferenceState(false)
        state.openDetail(); state.openInputs(); state.journal.requestedEdition = 1
        state.openWeeklyJournal()
        assertEquals(ReferenceStep.SUMMARY, state.step.value)
        assertEquals(0, state.journal.requestedEdition)
        assertFalse(state.goBack())
    }
}
