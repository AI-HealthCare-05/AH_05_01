package com.tmtn.app.ui.reference

import com.google.gson.Gson
import com.tmtn.app.network.model.PredictionResultResponse
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class WaistEstimateStateTest {
    private fun result(value: String? = "82.36", status: String = "COMPUTED", type: String = "WAIST_CM_ESTIMATE",
        at: String = "2026-09-09T09:00:00+09:00") = PredictionResultResponse(type, value?.toBigDecimal(), status, at, "approved-v1")

    @Test fun existingDecimalStringResponseKeepsUnitsAndCalculationTime() {
        val decoded = Gson().fromJson("""{"submodel_type":"WAIST_CM_ESTIMATE","value":"82.3600","status":"COMPUTED","computed_at":"2026-09-09T09:00:00+09:00","model_version":"v1"}""", PredictionResultResponse::class.java)
        val state = waistEstimateFrom(listOf(result("0.2", type = "DIABETES_SCORE"), decoded)) as WaistEstimateUi.Available
        assertEquals(BigDecimal("82.3600"), state.centimeters)
        assertEquals("82.4", state.displayValue)
        assertEquals(Instant.parse("2026-09-09T00:00:00Z"), state.computedAt)
    }

    @Test fun absentNoncomputedOrInvalidValuesNeverBecomeZeroCentimeters() {
        val batches = listOf(emptyList(), listOf(result(type = "DIABETES_SCORE")), listOf(result(status = "FAILED")),
            listOf(result(status = "OUT_OF_RANGE")), listOf(result(null)), listOf(result("0")),
            listOf(result("-2")), listOf(result("0.01")))
        batches.forEach { assertEquals(WaistEstimateUi.Unavailable, waistEstimateFrom(it)) }
    }

    @Test fun calculationTimeChoosesLatestRecordWithoutMixingDiseaseScores() {
        val state = waistEstimateFrom(listOf(result("75.5", at = "2026-09-08T23:00:00Z"),
            result("82.36"), result("99", type = "HYPERTENSION_SCORE"))) as WaistEstimateUi.Available
        assertEquals("82.4", state.displayValue)
    }

    @Test fun networkFailureCanBeRetriedAndDoesNotLookLikeAnEmptyResult() = runBlocking {
        var count = 0
        val state = WaistEstimateState { if (++count == 1) Response.error(503, "unavailable".toResponseBody()) else Response.success(listOf(result())) }
        state.load()
        assertEquals(WaistEstimateUi.Failed, state.ui.value)
        state.load()
        assertTrue(state.ui.value is WaistEstimateUi.Available)
        assertEquals(2, count)
        val empty = WaistEstimateState { Response.success(emptyList()) }
        empty.load()
        assertEquals(WaistEstimateUi.Unavailable, empty.ui.value)
    }

    @Test fun cancelledReadingDoesNotProduceAFailureMessage() = runBlocking {
        val state = WaistEstimateState { throw CancellationException("leave screen") }
        try { state.load(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertNotEquals(WaistEstimateUi.Failed, state.ui.value)
    }
}
