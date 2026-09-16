package com.tmtn.app.ui.cardhome

import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.model.StageUpPendingResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class StageUpNavigationTest {
    private val pending = StageUpPendingResponse(2, 3, "몸통 연결하기", 15, 7, 1, "나뭇가지")

    @Test fun acknowledgedNewsOpensDamAndMarksHomeForFreshData() = runBlocking {
        val state = state { Response.success(Unit) }
        var opened = false
        state.acknowledgeStageUp { opened = true }
        assertTrue(opened)
        assertNull(state.stageUpPending.value)
        assertNull(state.setId.value)
        assertEquals(3, state.companionStage.value)
        assertFalse(state.isLoading.value)
    }

    @Test fun failedReceiptKeepsTheNewsAndDestinationForRetry() = runBlocking {
        var failing = true
        val state = state { if (failing) Response.error(503, "".toResponseBody()) else Response.success(Unit) }
        var opened = 0
        state.acknowledgeStageUp { opened++ }
        assertEquals(0, opened)
        assertEquals(pending, state.stageUpPending.value)
        assertEquals(CardHomeStep.STAGE_UP, state.step.value)
        assertNotNull(state.errorMessage.value)
        failing = false
        state.acknowledgeStageUp { opened++ }
        assertEquals(1, opened)
        assertNull(state.errorMessage.value)
    }

    @Test fun cancelledReceiptDoesNotDiscardTheNews() = runBlocking {
        val state = state { throw CancellationException() }
        val error = runCatching { state.acknowledgeStageUp {} }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(pending, state.stageUpPending.value)
        assertFalse(state.isLoading.value)
    }

    private fun state(reply: () -> Response<Unit>): CardHomeState {
        val api = java.lang.reflect.Proxy.newProxyInstance(CardHomeApi::class.java.classLoader, arrayOf(CardHomeApi::class.java)) { _, method, _ ->
            check(method.name == "markStageUpSeen")
            reply()
        } as CardHomeApi
        return CardHomeState(missionApiProvider = { api }).apply {
            step.value = CardHomeStep.STAGE_UP
            setId.value = "old-home"
            stageUpPending.value = pending
        }
    }
}
