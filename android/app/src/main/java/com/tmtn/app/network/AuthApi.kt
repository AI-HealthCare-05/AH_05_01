package com.tmtn.app.network

import com.tmtn.app.network.model.GoogleLoginRequest
import com.tmtn.app.network.model.LoginRequest
import com.tmtn.app.network.model.LoginResponse
import com.tmtn.app.network.model.SocialLoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    /**
     * ⚠️ 2026-09-09 추가: 구글 계정 연동 로그인.
     * 가입과 로그인이 같은 엔드포인트입니다 - 사용자는 "구글로 계속하기" 버튼 하나만
     * 누르고, 서버가 처음 보는 계정이면 만들고 아니면 로그인시킵니다.
     * 다음 화면은 응답의 is_new_user로 정합니다(신규면 온보딩 A06부터).
     */
    @POST("auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<SocialLoginResponse>

    // ⚠️ 2026-09-03 리뷰 반영: refresh_token 쿠키로 access_token을 새로 받는 엔드포인트.
    // 지금까지는 아예 안 불리고 있어서 60분마다 세션 만료 화면으로 튕기고 있었음
    // (TokenAuthenticator 참고).
    @GET("auth/token/refresh")
    suspend fun refresh(): Response<LoginResponse>
}
