package com.tmtn.app.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 어떤 요청이든 401(토큰 만료/무효)로 응답이 오면, 토큰을 지우고 전역 신호(SessionManager)를
 * 켬. 로그인/회원가입 자체를 요청하는 경로는 "비밀번호 틀림" 같은 걸로도 401이 날 수 있어서
 * 세션 만료로 오인하지 않게 제외함.
 */
class SessionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val response = chain.proceed(request)

        val isAuthEndpoint = path.contains("/auth/login") ||
            path.contains("/auth/email-verification") ||
            path.contains("/auth/signup")

        if (response.code == 401 && !isAuthEndpoint) {
            TokenHolder.clear()
            SessionManager.markExpired()
        }

        return response
    }
}
