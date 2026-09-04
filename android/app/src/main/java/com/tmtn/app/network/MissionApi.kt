package com.tmtn.app.network

import com.tmtn.app.network.model.SensorMeasurementBatchRequest
import com.tmtn.app.network.model.SensorMeasurementBatchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface MissionApi {

    /**
     * challenge_id가 body가 아니라 URL 경로에 들어가는 구조로 최종 확정됨
     * (서버: POST /api/v1/challenges/{challenge_id}/sensor-measurements/batch)
     */
    @POST("challenges/{challengeId}/sensor-measurements/batch")
    suspend fun sendSensorMeasurements(
        @Path("challengeId") challengeId: String,
        @Body body: SensorMeasurementBatchRequest
    ): Response<SensorMeasurementBatchResponse>
}
