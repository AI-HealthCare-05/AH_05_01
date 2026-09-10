package com.tmtn.app.network

import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.network.model.CardWindowResponse
import com.tmtn.app.network.model.ChallengeProgressResponse
import com.tmtn.app.network.model.CompanionResponse
import com.tmtn.app.network.model.CompleteChallengeRequestBody
import com.tmtn.app.network.model.CompleteChallengeResponse
import com.tmtn.app.network.model.MemoUpdateRequest
import com.tmtn.app.network.model.RestDayRequest
import com.tmtn.app.network.model.SkipChallengeRequest
import com.tmtn.app.network.model.StreakResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CardHomeApi {

    @GET("daily-cards/today")
    suspend fun getTodayCards(): Response<CardWindowResponse>

    @POST("daily-cards/{setId}/options/{optionId}/select")
    suspend fun selectCard(
        @Path("setId") setId: String,
        @Path("optionId") optionId: String,
    ): Response<CardRevealResponse>

    @GET("companion")
    suspend fun getCompanionStatus(): Response<CompanionResponse>

    @GET("companion/materials/{element}/history")
    suspend fun getMaterialHistory(
        @Path("element") element: String,
    ): Response<com.tmtn.app.network.model.MaterialHistoryResponse>

    @GET("companion/cards")
    suspend fun getCardCollection(
        @retrofit2.http.Query("element") element: String? = null,
    ): Response<com.tmtn.app.network.model.CardCollectionResponse>

    @GET("companion/stage-up-pending")
    suspend fun getStageUpPending(): Response<com.tmtn.app.network.model.StageUpPendingResponse>

    @POST("companion/stage-up-seen")
    suspend fun markStageUpSeen(): Response<Unit>

    @POST("challenges/{challengeId}/complete")
    suspend fun completeChallenge(
        @Path("challengeId") challengeId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CompleteChallengeRequestBody = CompleteChallengeRequestBody(),
    ): Response<CompleteChallengeResponse>

    @GET("challenges/{challengeId}/reveal")
    suspend fun revealChallenge(
        @Path("challengeId") challengeId: String,
    ): Response<CardRevealResponse>

    @POST("challenges/{challengeId}/start")
    suspend fun startChallenge(
        @Path("challengeId") challengeId: String,
    ): Response<com.tmtn.app.network.model.ChallengeProgressResponse>

    @POST("challenges/{challengeId}/pause")
    suspend fun pauseChallenge(
        @Path("challengeId") challengeId: String,
    ): Response<com.tmtn.app.network.model.ChallengeProgressResponse>

    @GET("records/streak")
    suspend fun getStreak(): Response<StreakResponse>

    @POST("records/rest-day")
    suspend fun markRestDay(@Body body: RestDayRequest): Response<StreakResponse>

    // ⚠️ 2026-09-07 반영: 백엔드전달_상태전이 문서 G1(P0) - 서버 엔드포인트
    // (DELETE /records/rest-day?service_date=...)는 이미 있었는데 안드로이드 쪽 호출이
    // 없어서 "쉬어가기 취소"가 UI에서 아예 불가능했음(T08/T09/T10/T13 전이).
    @DELETE("records/rest-day")
    suspend fun cancelRestDay(@Query("service_date") serviceDate: String): Response<StreakResponse>

    // ⚠️ 2026-09-07 반영: REST -> GIVE_UP 전환(T13 계열, 화면 C27). "쉬어가기 취소 + 포기
    // 기록"을 서버가 하나의 트랜잭션으로 원자적으로 처리 - 클라이언트에서 cancelRestDay와
    // skipChallenge를 따로 두 번 부르지 않게 함(중간 실패 시 상태 불일치 방지).
    @POST("records/switch-to-give-up")
    suspend fun switchToGiveUp(@Body body: RestDayRequest): Response<StreakResponse>

    @PATCH("records/day/{date}/memo")
    suspend fun updateDayMemo(
        @Path("date") date: String,
        @Body body: MemoUpdateRequest,
    ): Response<Unit>

    @POST("challenges/{challengeId}/skip")
    suspend fun skipChallenge(
        @Path("challengeId") challengeId: String,
        @Body body: SkipChallengeRequest,
    ): Response<ChallengeProgressResponse>
}
