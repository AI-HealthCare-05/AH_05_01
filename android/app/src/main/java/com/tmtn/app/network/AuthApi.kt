package com.tmtn.app.network

import com.tmtn.app.network.model.LoginRequest
import com.tmtn.app.network.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
