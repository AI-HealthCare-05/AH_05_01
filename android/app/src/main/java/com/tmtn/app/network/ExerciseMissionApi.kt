package com.tmtn.app.network

import com.tmtn.app.network.model.CompleteExerciseMissionSessionRequest
import com.tmtn.app.network.model.CompleteExerciseMissionSessionResponse
import com.tmtn.app.network.model.CreateExerciseMissionSessionRequest
import com.tmtn.app.network.model.ExerciseMissionActionRequest
import com.tmtn.app.network.model.ExerciseMissionSessionResponse
import com.tmtn.app.network.model.ExerciseMissionsTodayResponse
import java.util.UUID
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ExerciseMissionApi {

    @GET("exercise-missions/today")
    suspend fun getToday(): Response<ExerciseMissionsTodayResponse>

    @POST("exercise-mission-sessions")
    suspend fun createSession(@Body request: CreateExerciseMissionSessionRequest): Response<ExerciseMissionSessionResponse>

    @PATCH("exercise-mission-sessions/{id}")
    suspend fun patchSession(
        @Path("id") id: UUID,
        @Body request: ExerciseMissionActionRequest,
    ): Response<ExerciseMissionSessionResponse>

    @POST("exercise-mission-sessions/{id}/complete")
    suspend fun completeSession(
        @Path("id") id: UUID,
        @Body request: CompleteExerciseMissionSessionRequest,
    ): Response<CompleteExerciseMissionSessionResponse>

    @POST("exercise-mission-sessions/{id}/cancel")
    suspend fun cancelSession(@Path("id") id: UUID): Response<Unit>
}
