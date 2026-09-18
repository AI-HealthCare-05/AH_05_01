package com.tmtn.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.FirstRepairResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.Response
import kotlin.time.TimeSource

internal enum class FirstRepairPhase { Loading, Welcome, Gift, Placing, Complete, Error, Legacy }
internal enum class FirstRepairOperation { Read, Gift, Complete }

/** 서버 상태로 재진입한다. 화면만 1단계로 바꾸거나 선물을 로컬에서 누적하지 않는다. */
internal class FirstRepairState(
    private val fetch: suspend () -> Response<FirstRepairResponse> = { ApiClient.cardHomeApi.getFirstRepair() },
    private val receive: suspend () -> Response<FirstRepairResponse> = { ApiClient.cardHomeApi.receiveFirstGift() },
    private val complete: suspend () -> Response<FirstRepairResponse> = { ApiClient.cardHomeApi.completeFirstRepair() },
) {
    var phase by mutableStateOf(FirstRepairPhase.Loading)
        private set
    var data by mutableStateOf<FirstRepairResponse?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var failedOperation by mutableStateOf(FirstRepairOperation.Read)
        private set

    suspend fun refresh() = request(FirstRepairOperation.Read, 0, fetch)
    suspend fun receiveGift() = request(FirstRepairOperation.Gift, 0, receive)
    suspend fun fillGap(minimumMillis: Int) = request(FirstRepairOperation.Complete, minimumMillis, complete)

    suspend fun retry(minimumMillis: Int) = when (failedOperation) {
        FirstRepairOperation.Read -> refresh()
        FirstRepairOperation.Gift -> receiveGift()
        FirstRepairOperation.Complete -> fillGap(minimumMillis)
    }

    private suspend fun request(operation: FirstRepairOperation, minimumMillis: Int,
                                action: suspend () -> Response<FirstRepairResponse>) {
        if (busy) return
        busy = true
        failedOperation = operation
        if (operation == FirstRepairOperation.Complete) phase = FirstRepairPhase.Placing
        else if (operation == FirstRepairOperation.Read) phase = FirstRepairPhase.Loading
        val started = TimeSource.Monotonic.markNow()
        try {
            val response = action()
            if (operation == FirstRepairOperation.Read && response.code() in listOf(404, 405)) {
                phase = FirstRepairPhase.Legacy
                return
            }
            val result = response.body()
            val next = when (result?.status) {
                "ELIGIBLE" -> FirstRepairPhase.Welcome
                "GIFT_RECEIVED" -> FirstRepairPhase.Gift
                "COMPLETED" -> FirstRepairPhase.Complete
                "UNAVAILABLE" -> FirstRepairPhase.Legacy
                else -> null
            }
            check(response.isSuccessful && result != null && next != null)
            check(operation != FirstRepairOperation.Complete || next == FirstRepairPhase.Complete)
            check(operation != FirstRepairOperation.Gift || next in listOf(FirstRepairPhase.Gift, FirstRepairPhase.Complete))
            check(next != FirstRepairPhase.Gift || result.gift_count == 1)
            check(next != FirstRepairPhase.Complete || (result.gift_count == 1 && result.companion.current_stage >= 1))
            if (minimumMillis > 0) delay((minimumMillis - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
            data = result
            phase = next
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            phase = FirstRepairPhase.Error
        } finally {
            busy = false
        }
    }
}
