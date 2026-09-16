package com.tmtn.app.network

import com.tmtn.app.network.model.PracticeScoreResponse
import retrofit2.Response
import retrofit2.http.GET

interface PracticeScoreApi {

    @GET("practice-score")
    suspend fun getPracticeScore(): Response<PracticeScoreResponse>
}
