package com.tmtn.app.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * TokenHolder에 토큰이 있으면 모든 요청에 자동으로 Authorization 헤더를 붙인다.
 * 로그인 자체를 요청하는 auth/login, auth/signup 경로는 토큰이 필요 없으니 건너뛴다.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        if (path.contains("/auth/login") || path.contains("/auth/signup")) {
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
