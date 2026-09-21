package com.tmtn.app.ui.onboarding

import com.tmtn.app.network.model.CompanionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class FirstDamStateTest {
    @Test fun serverStageAndMaterialsAreNeverPromotedOrResetByTheIntroduction() = runBlocking {
        for ((stage, total, next) in listOf(Triple(0, 0, 5), Triple(0, 1, 5), Triple(1, 5, 15), Triple(3, 41, 70))) {
            val expected = CompanionResponse(stage, total, next, next - total, emptyList(), emptyList())
            var requests = 0
            val state = FirstDamState { requests++; Response.success(expected) }
            state.refresh()
            assertSame(expected, state.companion)
            assertEquals(1, requests)
            assertFalse(state.loading)
            assertFalse(state.failed)
        }
    }

    @Test fun failureDoesNotInventAnEmptyDamAndRetryReadsActualProgress() = runBlocking {
        var calls = 0
        val state = FirstDamState {
            if (++calls == 1) Response.error(503, "".toResponseBody())
            else Response.success(CompanionResponse(2, 15, 35, 20, emptyList(), emptyList()))
        }
        state.refresh()
        assertNull(state.companion)
        assertTrue(state.failed)
        assertFalse(state.loading)
        state.refresh()
        assertEquals(2, state.companion?.current_stage)
        assertFalse(state.failed)
        assertEquals(2, calls)
    }

    @Test fun leavingScreenCancelsTheReadWithoutReportingAConnectionFailure() = runBlocking {
        val state = FirstDamState { throw CancellationException("leaving introduction") }
        try { state.refresh(); fail("Cancellation must reach the screen lifecycle") }
        catch (_: CancellationException) { }
        assertFalse(state.failed)
        assertFalse(state.loading)
        assertNull(state.companion)
    }
}
