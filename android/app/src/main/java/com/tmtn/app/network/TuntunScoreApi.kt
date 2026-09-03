package com.tmtn.app.network

import com.tmtn.app.network.model.ScoreInputsResponse
import com.tmtn.app.network.model.TmtnScoreResponse
import com.tmtn.app.network.model.TuntunScoreOrEligibilityResponse
import retrofit2.Response
import retrofit2.http.GET

interface TuntunScoreApi {

    @GET("tuntun-score")
    suspend fun getTuntunScore(): Response<TuntunScoreOrEligibilityResponse>

    @GET("tuntun-score/about")
    suspend fun getTuntunScoreAbout(): Response<TmtnScoreResponse>

    @GET("tuntun-score/inputs")
    suspend fun getTuntunScoreInputs(): Response<ScoreInputsResponse>
}
