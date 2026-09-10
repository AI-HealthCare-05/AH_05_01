package com.tmtn.app.network

import com.tmtn.app.network.model.SensorMeasurementBatchRequest
import com.tmtn.app.network.model.SensorMeasurementBatchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface MissionApi {

    /**
     * challenge_id가 body가 아니라 URL 경로에 들어가는 구조로 최종 확정됨
     * (서버: POST /api/v1/challenges/{challenge_id}/sensor-measurements/batch)
     *
     * ⚠️ 2026-09-08 추가: X-Tmtn-Background-Sync 헤더.
     * 이 요청은 포그라운드 서비스가 사용자 모르게 30초마다 보내는 백그라운드 동기화라,
     * 여기서 401이 났다고 화면을 "다시 로그인해주세요"로 덮어버리면 안 됨(사용자는 아무것도
     * 안 눌렀는데 갑자기 튕김). 토큰 갱신(TokenAuthenticator)은 그대로 동작하고,
     * 전역 세션 만료 신호만 SessionInterceptor에서 건너뛴다.
     */
    // 헤더 이름은 SessionInterceptor.BACKGROUND_SYNC_HEADER 와 같아야 함
    // (@Headers 값은 컴파일 상수여야 해서 문자열을 그대로 적음).
    @Headers("X-Tmtn-Background-Sync: 1")
    @POST("challenges/{challengeId}/sensor-measurements/batch")
    suspend fun sendSensorMeasurements(
        @Path("challengeId") challengeId: String,
        @Body body: SensorMeasurementBatchRequest
    ): Response<SensorMeasurementBatchResponse>
}
