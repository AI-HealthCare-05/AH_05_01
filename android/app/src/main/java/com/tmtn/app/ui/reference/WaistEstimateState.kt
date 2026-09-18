package com.tmtn.app.ui.reference

import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.PredictionResultResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface WaistEstimateUi {
    data object Loading : WaistEstimateUi
    data object Unavailable : WaistEstimateUi
    data object Failed : WaistEstimateUi
    data class Available(val centimeters: BigDecimal, val computedAt: Instant?) : WaistEstimateUi {
        val displayValue: String get() = centimeters.setScale(1, RoundingMode.HALF_UP).toPlainString()
        // ⚠️ 2026-09-18 추가(UI/UX 핸드오프 NH01~11) - "cm 원본을 유지하고 inch 표시는
        // 환산 표시만 적용"이 계약 요구사항. centimeters(원본)는 안 건드리고, 화면에서
        // 부가 정보로만 보여줄 환산값을 추가함.
        private val CM_TO_INCH = BigDecimal("0.393701")
        val displayValueInches: String
            get() = centimeters.multiply(CM_TO_INCH).setScale(1, RoundingMode.HALF_UP).toPlainString()
    }
}

/** Displays approved model output; does not estimate, score, normalize, or persist health data. */
internal fun waistEstimateFrom(results: List<PredictionResultResponse>): WaistEstimateUi {
    val waist = results.filter { it.submodelType == "WAIST_CM_ESTIMATE" && it.status == "COMPUTED" }
        .maxByOrNull { predictionTime(it.computedAt) ?: Instant.MIN }
        ?: return WaistEstimateUi.Unavailable
    val value = waist.value ?: return WaistEstimateUi.Unavailable
    // A missing/invalid result must never look like a circumference of 0 cm.
    if (value.signum() <= 0 || value.setScale(1, RoundingMode.HALF_UP).signum() <= 0) return WaistEstimateUi.Unavailable
    return WaistEstimateUi.Available(value, predictionTime(waist.computedAt))
}

private fun predictionTime(raw: String?): Instant? =
    raw?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

class WaistEstimateState(
    private val request: suspend () -> Response<List<PredictionResultResponse>> = {
        ApiClient.tuntunScoreApi.getLatestPredictionResults()
    },
) {
    val ui = mutableStateOf<WaistEstimateUi>(WaistEstimateUi.Loading)
    private var inFlight = false

    suspend fun load() {
        if (inFlight) return
        inFlight = true
        ui.value = WaistEstimateUi.Loading
        try {
            val response = request()
            ui.value = if (response.isSuccessful && response.body() != null)
                waistEstimateFrom(response.body()!!) else WaistEstimateUi.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ui.value = WaistEstimateUi.Failed
        } finally {
            inFlight = false
        }
    }
}
