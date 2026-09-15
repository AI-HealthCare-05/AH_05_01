package com.tmtn.app.ui.cardhome

import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.model.RestDayRequest
import com.tmtn.app.network.model.StreakResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RestTransitionTest {
    @Test fun restUsesServiceDateAndOnlyClosesAfterAcknowledgement() = runBlocking {
        val state = state { name, args ->
            assertEquals("markRestDay", name)
            assertEquals("2026-09-14", (args!![0] as RestDayRequest).service_date)
            Response.success(StreakResponse(14, 21, 1, 1))
        }.apply { showRestDaySheet.value = true }
        assertTrue(state.confirmRestDay())
        assertTrue(state.isTodayRestDay.value)
        assertFalse(state.showRestDaySheet.value)
        assertEquals(14, state.currentStreak.value)
        assertEquals(1, state.restDaysRemainingThisWeek.value)
    }

    @Test fun failedCancelPreservesRestAndItsRemainingAllowance() = runBlocking {
        val state = state { name, args ->
            assertEquals("cancelRestDay", name)
            assertEquals("2026-09-14", args!![0])
            Response.error<Any>(503, "".toResponseBody())
        }.apply { isTodayRestDay.value = true; restDaysRemainingThisWeek.value = 1 }
        assertFalse(state.cancelRestDay())
        assertTrue(state.isTodayRestDay.value)
        assertEquals(1, state.restDaysRemainingThisWeek.value)
        assertFalse(state.isLoading.value)
        assertNotNull(state.errorMessage.value)
    }

    @Test fun switchUsesOneAtomicEndpointAndClearsOldTimerAfterSuccess() = runBlocking {
        val calls = mutableListOf<String>()
        val state = state { name, _ ->
            calls += name
            Response.success(StreakResponse(0, 21, 0, 2))
        }.apply {
            isTodayRestDay.value = true; timerElapsedSeconds.value = 84
            todayChallengeId.value = "offline-id"; todayChallengeState.value = "PAUSED"
        }
        assertTrue(state.switchRestToGiveUp())
        assertEquals(listOf("switchToGiveUp"), calls)
        assertFalse(state.isTodayRestDay.value)
        assertTrue(state.isTodayGivenUp.value)
        assertEquals("SKIPPED", state.todayChallengeState.value)
        assertEquals(0, state.timerElapsedSeconds.value)
        assertTrue(state.timerIsPaused.value)
        assertEquals(2, state.restDaysRemainingThisWeek.value)
    }

    @Test fun cancellationLeavesTheRestSheetOpenAndReleasesLoading() = runBlocking {
        val state = state { _, _ -> throw CancellationException("test cancellation") }
            .apply { showRestDaySheet.value = true }
        var cancelled = false
        try { state.confirmRestDay() } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertTrue(state.showRestDaySheet.value)
        assertFalse(state.isTodayRestDay.value)
        assertFalse(state.isLoading.value)
    }

    private fun state(block: (String, Array<out Any?>?) -> Any): CardHomeState {
        val api = java.lang.reflect.Proxy.newProxyInstance(CardHomeApi::class.java.classLoader, arrayOf(CardHomeApi::class.java)) { _, method, args -> block(method.name, args) } as CardHomeApi
        return CardHomeState(serviceDateProvider = { "2026-09-14" }, missionApiProvider = { api })
    }
}
