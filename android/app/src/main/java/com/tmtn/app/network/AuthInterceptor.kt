package com.tmtn.app.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * TokenHolder에 토큰이 있으면 모든 요청에 자동으로 Authorization 헤더를 붙인다.
 * "로그인·가입 자체를 요청하는" 경로는 토큰이 필요 없으니 건너뛴다.
 */
class AuthInterceptor : Interceptor {

    companion object {
        /**
         * 아직 로그인하지 않은 상태에서 부르는 경로들 - Authorization 헤더를 붙이지 않는다.
         *
         * ⚠️ 2026-09-09 반영: /auth/google이 빠져 있었음. 계정을 바꿔 로그인하거나 세션이
         * 만료된 뒤 구글 로그인을 누르면 예전 토큰이 이 요청에 그대로 붙었음. 그 자체로는
         * 서버가 무시하지만(이 엔드포인트는 헤더를 안 봄), 구글 토큰이 거부돼서 401이 나면
         * SessionInterceptor가 "헤더가 붙어 있으니 진짜 세션 만료"로 오인해서 온보딩 화면
         * 위에 "다시 로그인해주세요"를 덮어버렸음 - 9/8에 고친 재가입 중 튕김과 같은 구조.
         * 게다가 TokenAuthenticator까지 깨어나 쓸데없는 리프레시를 한 번 더 시도했음.
         *
         * ⚠️ SessionInterceptor.AUTH_ENDPOINT_PATHS와 같은 목록을 유지할 것.
         * 한쪽만 추가하면 위와 같은 어긋남이 다시 생김.
         */
        val UNAUTHENTICATED_PATHS = listOf(
            "/auth/login",
            "/auth/signup",
            "/auth/google",
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        if (UNAUTHENTICATED_PATHS.any { path.contains(it) }) {
            return chain.proceed(original)
        }

        val token = TokenHolder.accessToken
        val request = if (token != null) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
