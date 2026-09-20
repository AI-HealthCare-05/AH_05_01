package com.tmtn.app.ui.journal

import com.tmtn.app.network.model.CompanionResponse
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class JournalCompanionStateTest {
    private fun dam(stage: Int, materials: Int = 12) =
        CompanionResponse(stage, materials, null, 0, emptyList(), emptyList())

    @Test fun currentStageComesFromCompanionEvenWithoutWeeklyRecords() = runBlocking {
        val state = JournalState(companionRequest = { Response.success(dam(4, 27)) })
        state.refreshCompanion()
        assertEquals(JournalLoad.Ready(dam(4, 27)), state.companion)
        assertEquals(JournalLoad.Loading, state.weekly)
    }

    @Test fun failedRefreshHidesThePreviousStageAndRetryReadsTheServerAgain() = runBlocking {
        var count = 0
        val state = JournalState(companionRequest = {
            when (count++) {
                0 -> Response.success(dam(2))
                1 -> throw IOException("연결 끊김")
                else -> Response.success(dam(3))
            }
        })
        state.refreshCompanion()
        state.refreshCompanion()
        assertEquals(JournalLoad.Failed, state.companion)
        state.refreshCompanion()
        assertEquals(JournalLoad.Ready(dam(3)), state.companion)
        assertEquals(3, count)
    }

    @Test fun authenticationFailureDoesNotShowAStage() = runBlocking {
        val state = JournalState(companionRequest = { Response.error(401, "".toResponseBody()) })
        state.refreshCompanion()
        assertEquals(JournalLoad.Failed, state.companion)
    }

    @Test fun unsupportedStageOrNegativeTotalDoesNotBecomeZeroOrFive() = runBlocking {
        for (body in listOf(dam(-1), dam(6), dam(2, -1))) {
            val state = JournalState(companionRequest = { Response.success(body) })
            state.refreshCompanion()
            assertEquals(JournalLoad.Failed, state.companion)
        }
    }

    @Test fun lateResponseCannotRollBackARefreshedStage() = runBlocking {
        val oldResponse = CompletableDeferred<Response<CompanionResponse>>()
        var calls = 0
        val state = JournalState(companionRequest = {
            if (calls++ == 0) oldResponse.await() else Response.success(dam(5))
        })
        val old = launch(start = CoroutineStart.UNDISPATCHED) { state.refreshCompanion() }
        assertEquals(JournalLoad.Loading, state.companion)
        state.refreshCompanion()
        oldResponse.complete(Response.success(dam(1)))
        old.join()
        assertEquals(JournalLoad.Ready(dam(5)), state.companion)
    }
}
