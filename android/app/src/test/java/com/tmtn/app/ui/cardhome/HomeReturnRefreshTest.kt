package com.tmtn.app.ui.cardhome

import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.network.model.CardWindowResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import java.time.LocalDate

class HomeReturnRefreshTest {
    private val oldCard = CardRevealResponse("old-card", "SENSOR_STEPS", "한 걸음", "", "WOOD", "유산소", 100, "걸음", "COMPLETED",
        fortune_text = "", lucky_location = "", line_text = "")
    private val nextDay = CardWindowResponse("AWAITING_SELECTION", "2026-09-16", "new-set", listOf("a", "b", "c"), null, null, null)
    private fun state(read: suspend () -> Response<CardWindowResponse>): CardHomeState {
        val unsupported = Proxy.newProxyInstance(CardHomeApi::class.java.classLoader, arrayOf(CardHomeApi::class.java)) { _, method, _ ->
            error("Unexpected endpoint: ${method.name}")
        } as CardHomeApi
        val api = object : CardHomeApi by unsupported {
            override suspend fun getTodayCards(): Response<CardWindowResponse> = read()
        }
        return CardHomeState(missionApiProvider = { api }).apply {
            step.value = CardHomeStep.HOME; setId.value = "old-set"
            cardServiceDate.value = "2026-09-15"
            todayChallengeId.value = oldCard.challenge_id; revealedCard.value = oldCard
            hasMemoToday.value = true
        }
    }

    @Test fun aNewServiceDayClearsYesterdayCardAndUsesTheServerDate() = runBlocking {
        val state = state { Response.success(nextDay) }
        state.refreshHomeOnReturn()
        assertEquals(CardHomeStep.HOME, state.step.value)
        assertEquals(LocalDate.of(2026, 9, 16), state.displayDateLabel())
        assertEquals(listOf("a", "b", "c"), state.optionIds.value)
        assertNull(state.revealedCard.value)
        assertNull(state.todayChallengeId.value)
        assertNull(state.hasMemoToday.value)
    }

    @Test fun failedRefreshKeepsTheLastCardAndCanRetry() = runBlocking {
        var fail = true
        val state = state { if (fail) error("offline") else Response.success(nextDay) }
        state.refreshHomeOnReturn()
        assertEquals(oldCard, state.revealedCard.value)
        assertEquals("old-set", state.setId.value)
        assertNotNull(state.errorMessage.value)
        fail = false
        state.refreshHomeOnReturn()
        assertEquals("new-set", state.setId.value)
        assertNull(state.errorMessage.value)
    }

    @Test fun returningDuringMeasurementOrAnOpenSheetDoesNotLoadOrChangeIt() = runBlocking {
        var requests = 0
        val state = state { requests++; Response.success(nextDay) }
        for (step in listOf(CardHomeStep.SENSOR_MEASURING, CardHomeStep.CHALLENGE_TIMER_PAUSED, CardHomeStep.REVEALED)) {
            state.step.value = step
            state.refreshHomeOnReturn()
            assertEquals(step, state.step.value)
        }
        state.step.value = CardHomeStep.HOME
        state.showRestDaySheet.value = true
        state.refreshHomeOnReturn()
        assertTrue(state.showRestDaySheet.value)
        assertEquals(oldCard, state.revealedCard.value)
        assertEquals(0, requests)
    }

    @Test fun aLateReplyCannotReplaceTheCardPickerOrAnOpenedSheet() = runBlocking {
        for (showSheet in listOf(false, true)) {
            val reply = CompletableDeferred<Response<CardWindowResponse>>()
            val state = state { reply.await() }
            val job = launch(start = CoroutineStart.UNDISPATCHED) { state.refreshHomeOnReturn() }
            if (showSheet) state.showRestCancelSheet.value = true else state.step.value = CardHomeStep.DECK_PICK
            reply.complete(Response.success(nextDay)); job.join()
            assertEquals("old-set", state.setId.value)
            assertEquals(oldCard, state.revealedCard.value)
            if (showSheet) assertTrue(state.showRestCancelSheet.value) else assertEquals(CardHomeStep.DECK_PICK, state.step.value)
        }
    }

    @Test fun repeatedReturnsCoalesceAndCancellationAllowsAnotherTry() = runBlocking {
        val reply = CompletableDeferred<Response<CardWindowResponse>>()
        var requests = 0
        val state = state { requests++; reply.await() }
        val first = launch(start = CoroutineStart.UNDISPATCHED) { state.refreshHomeOnReturn() }
        state.refreshHomeOnReturn()
        assertEquals(1, requests)
        first.cancel(); first.join()
        assertNull(state.errorMessage.value)
        reply.complete(Response.success(nextDay))
        state.refreshHomeOnReturn()
        assertEquals(2, requests)
        assertEquals("new-set", state.setId.value)
    }
}
