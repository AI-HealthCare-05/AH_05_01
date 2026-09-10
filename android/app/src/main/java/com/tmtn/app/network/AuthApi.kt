package com.tmtn.app.network

import com.tmtn.app.network.model.LoginRequest
import com.tmtn.app.network.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ⚠️ 2026-09-03 리뷰 반영: refresh_token 쿠키로 access_token을 새로 받는 엔드포인트.
    // 지금까지는 아예 안 불리고 있어서 60분마다 세션 만료 화면으로 튕기고 있었음
    // (TokenAuthenticator 참고).
    @GET("auth/token/refresh")
    suspend fun refresh(): Response<LoginResponse>
}
