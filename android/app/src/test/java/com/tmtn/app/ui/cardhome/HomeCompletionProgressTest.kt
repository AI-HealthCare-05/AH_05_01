package com.tmtn.app.ui.cardhome

import com.tmtn.app.network.ExerciseMissionApi
import com.tmtn.app.network.model.ExerciseMissionsTodayResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class HomeCompletionProgressTest {
    private val date = "2026-09-20"
    private fun today(used: Int) = ExerciseMissionsTodayResponse(true, used, 2, 2 - used, emptyList())
    private fun state(read: suspend () -> Response<ExerciseMissionsTodayResponse>): CardHomeState {
        val unused = java.lang.reflect.Proxy.newProxyInstance(ExerciseMissionApi::class.java.classLoader,
            arrayOf(ExerciseMissionApi::class.java)) { _, method, _ -> error("뜻밖의 변경 요청: ${method.name}") } as ExerciseMissionApi
        val api = object : ExerciseMissionApi by unused {
            override suspend fun getToday() = read()
        }
        return CardHomeState(exerciseApiProvider = { api }).apply { cardServiceDate.value = date; step.value = CardHomeStep.HOME }
    }

    @Test fun congratulationsFollowOnlyConfirmedCountsFromTheSameDay() {
        assertEquals(HomeCelebration.CARD, homeCelebration(date, null))
        assertEquals(HomeCelebration.CARD, homeCelebration(date, HomeExerciseProgress(date, 0, 2)))
        assertEquals(HomeCelebration.EXTRA_ONE, homeCelebration(date, HomeExerciseProgress(date, 1, 1)))
        assertEquals(HomeCelebration.EXTRA_TWO, homeCelebration(date, HomeExerciseProgress(date, 2, 0)))
        assertEquals(HomeCelebration.CARD, homeCelebration("2026-09-21", HomeExerciseProgress(date, 2, 0)))
    }

    @Test fun homeRefreshRestoresBothCompletionsWithoutNavigatingAway() = runBlocking {
        val state = state { Response.success(today(2)) }
        state.refreshHomeExerciseProgress()
        assertEquals(HomeCelebration.EXTRA_TWO, homeCelebration(date, state.homeExerciseProgress.value))
        assertEquals(CardHomeStep.HOME, state.step.value)
    }

    @Test fun serverFailureDoesNotInventCompletionsOrLoseAnAcknowledgedOne() = runBlocking {
        val state = state { Response.error(503, "".toResponseBody()) }
        state.refreshHomeExerciseProgress()
        assertNull(state.homeExerciseProgress.value)
        state.confirmHomeExerciseProgress(date, 1, 1)
        state.refreshHomeExerciseProgress()
        assertEquals(HomeExerciseProgress(date, 1, 1), state.homeExerciseProgress.value)
    }

    @Test fun slowReadCannotOverwriteANewerCompletionReceipt() = runBlocking {
        val response = CompletableDeferred<Response<ExerciseMissionsTodayResponse>>()
        val state = state { response.await() }
        val request = launch(start = CoroutineStart.UNDISPATCHED) { state.refreshHomeExerciseProgress() }
        state.confirmHomeExerciseProgress(date, 2, 0)
        response.complete(Response.success(today(1)))
        request.join()
        assertEquals(2, state.homeExerciseProgress.value?.completed)
    }

    @Test fun yesterdayReplyDoesNotBecomeTodaysTwoCompletions() = runBlocking {
        val response = CompletableDeferred<Response<ExerciseMissionsTodayResponse>>()
        val state = state { response.await() }
        val request = launch(start = CoroutineStart.UNDISPATCHED) { state.refreshHomeExerciseProgress() }
        state.cardServiceDate.value = "2026-09-21"
        response.complete(Response.success(today(2)))
        request.join()
        state.confirmHomeExerciseProgress(date, 2, 0)
        assertNull(state.homeExerciseProgress.value)
    }

    @Test fun repeatedReceiptUsesServerTotalInsteadOfAddingAgain() {
        val state = state { error("호출 불필요") }
        repeat(2) { state.confirmHomeExerciseProgress(date, 1, 1) }
        assertEquals(1, state.homeExerciseProgress.value?.completed)
        state.confirmHomeExerciseProgress(date, -1, 0)
        assertEquals(1, state.homeExerciseProgress.value?.completed)
    }

    @Test fun uncompletedCardAndSavingSensorTargetCannotUnlockCelebration() = runBlocking {
        val state = state { Response.success(today(2).copy(card_completed = false)) }
        state.exerciseSaveState.value = ExerciseSaveState.SAVING
        state.refreshHomeExerciseProgress()
        assertNull(state.homeExerciseProgress.value)
        state.exerciseSaveState.value = ExerciseSaveState.FAILED
        assertEquals(HomeCelebration.CARD, homeCelebration(date, state.homeExerciseProgress.value))
    }
}
