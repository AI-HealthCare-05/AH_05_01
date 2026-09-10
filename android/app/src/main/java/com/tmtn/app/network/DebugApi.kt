package com.tmtn.app.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST

/** ⚠️ 테스트 전용 - 서버가 PROD면 이 엔드포인트들은 전부 404. 하루 1개 미션 제한 때문에
 * 미션 10개를 이어서 테스트하려면 실제로 10일이 걸리는데, "다음 날로"를 눌러서 서버가
 * 인식하는 "오늘"을 하루씩 앞당겨 바로 이어서 테스트할 수 있게 함. */
interface DebugApi {
    @POST("debug/advance-day")
    suspend fun advanceDay(): Response<Map<String, Any>>

    @POST("debug/reset-day")
    suspend fun resetDay(): Response<Map<String, Any>>

    @GET("debug/current-day")
    suspend fun getCurrentDay(): Response<Map<String, Any>>
}
