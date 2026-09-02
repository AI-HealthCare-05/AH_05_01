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
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

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
