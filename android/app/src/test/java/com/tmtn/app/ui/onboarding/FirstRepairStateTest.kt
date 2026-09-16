package com.tmtn.app.ui.onboarding

import com.tmtn.app.network.model.CompanionResponse
import com.tmtn.app.network.model.FirstRepairResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class FirstRepairStateTest {
    private fun result(status: String) = FirstRepairResponse(status, "WOOD", if (status == "ELIGIBLE") 0 else 1,
        CompanionResponse(if (status == "COMPLETED") 1 else 0, if (status == "COMPLETED") 1 else 0,
            if (status == "COMPLETED") 15 else 5, if (status == "COMPLETED") 14 else 5, emptyList(), emptyList()))

    @Test fun eachServerCheckpointResumesTheMatchingScreen() = runBlocking {
        for ((status, phase) in mapOf("ELIGIBLE" to FirstRepairPhase.Welcome, "GIFT_RECEIVED" to FirstRepairPhase.Gift,
            "COMPLETED" to FirstRepairPhase.Complete, "UNAVAILABLE" to FirstRepairPhase.Legacy)) {
            val state = FirstRepairState(fetch = { Response.success(result(status)) })
            state.refresh()
            assertEquals(phase, state.phase)
        }
    }

    @Test fun oldServerFallsBackButNetworkAndServerFailuresNeverInventAGift() = runBlocking {
        for (code in listOf(404, 405, 401, 500, 503)) {
            val state = FirstRepairState(fetch = { Response.error(code, "".toResponseBody()) })
            state.refresh()
            assertEquals(if (code == 404 || code == 405) FirstRepairPhase.Legacy else FirstRepairPhase.Error, state.phase)
            assertNull(state.data)
        }
    }

    @Test fun completionWaitsForServerAndDoubleTapDoesNotDuplicateRequests() = runBlocking {
        val response = CompletableDeferred<Response<FirstRepairResponse>>()
        var calls = 0
        val state = FirstRepairState(fetch = { Response.success(result("GIFT_RECEIVED")) }, complete = { calls++; response.await() })
        state.refresh()
        val first = launch { state.fillGap(0) }
        yield()
        assertEquals(FirstRepairPhase.Placing, state.phase)
        assertTrue(state.busy)
        state.fillGap(0)
        assertEquals(1, calls)
        response.complete(Response.success(result("COMPLETED")))
        first.join()
        assertEquals(FirstRepairPhase.Complete, state.phase)
        assertFalse(state.busy)
    }

    @Test fun failedSaveRetriesTheSameGiftWithoutClaimingAgain() = runBlocking {
        var claims = 0
        var completions = 0
        val state = FirstRepairState(fetch = { Response.success(result("ELIGIBLE")) },
            receive = { claims++; Response.success(result("GIFT_RECEIVED")) },
            complete = { if (++completions == 1) Response.error(503, "".toResponseBody()) else Response.success(result("COMPLETED")) })
        state.refresh(); state.receiveGift(); state.fillGap(0)
        assertEquals(FirstRepairPhase.Error, state.phase)
        assertEquals("GIFT_RECEIVED", state.data?.status)
        state.retry(0)
        assertEquals(FirstRepairPhase.Complete, state.phase)
        assertEquals(1, claims)
        assertEquals(2, completions)
    }

    @Test fun malformedSuccessDoesNotPretendThatStageZeroIsComplete() = runBlocking {
        val state = FirstRepairState(fetch = { Response.success(result("GIFT_RECEIVED").copy(status = "COMPLETED")) })
        state.refresh()
        assertEquals(FirstRepairPhase.Error, state.phase)
        assertNull(state.data)
    }

    @Test fun cancellationReachesLifecycleAndReleasesBusyFlag() = runBlocking {
        val state = FirstRepairState(fetch = { throw CancellationException("화면 종료") })
        try { state.refresh(); fail("취소가 전달되어야 한다") } catch (_: CancellationException) { }
        assertFalse(state.busy)
        assertNotEquals(FirstRepairPhase.Error, state.phase)
    }
}
