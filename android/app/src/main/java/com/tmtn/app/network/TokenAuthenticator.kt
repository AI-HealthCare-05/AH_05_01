package com.tmtn.app.network

import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

/**
 * ⚠️ 2026-09-03 리뷰 반영: 지금까지 refresh_token을 아예 안 써서 ACCESS_TOKEN_EXPIRE_MINUTES=60
 * (config.py)마다 세션 만료 화면(H07)으로 튕기고 있었음. 20분짜리 미션 중간에 튕기면 진행
 * 상황이 날아갈 수 있어서, 401을 받으면 refresh_token 쿠키로 access_token을 새로 받아
 * 원래 요청을 자동으로 재시도하게 함.
 *
 * OkHttp Authenticator는 동기(블로킹) API라 Retrofit의 suspend 함수를 그대로 못 쓰고,
 * 별도의 단순한 OkHttpClient(refreshClient)로 직접 요청을 만들어 보냄. 이 refreshClient는
 * authenticator가 안 달려있어서 재시도가 또 재시도를 부르는 무한루프 걱정이 없음.
 *
 * 최종적으로 갱신에 실패하면(refresh_token도 만료 등) null을 돌려주고, 원래의 401 응답이
 * 그대로 SessionInterceptor까지 올라가서 기존 로직(세션 만료 화면 표시)이 그대로 처리함 -
 * 여기서 TokenHolder.clear()/SessionManager를 직접 건드리지 않음(책임 중복 방지).
 */
class TokenAuthenticator(private val baseUrl: String, private val refreshClient: OkHttpClient) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 이미 한 번 재시도했는데도 또 401이면(=refresh로 받은 토큰도 안 먹힘) 포기 -
        // 무한 재시도 방지.
        if (responseCount(response) >= 2) return null
        // 애초에 인증 헤더가 없던 요청(로그인 등)이면 우리가 손댈 대상이 아님.
        if (response.request.header("Authorization") == null) return null

        val newToken = requestNewAccessToken() ?: return null
        TokenHolder.accessToken = newToken
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    @Synchronized
    private fun requestNewAccessToken(): String? {
        // 여러 요청이 동시에 401을 맞아도 이 함수 자체가 synchronized라 refresh 호출은
        // 한 번만 나감(나머지는 기다렸다가 그 결과를 그대로 씀 - 정확히는 각자 다시 호출하지만
        // 서버가 refresh_token 재사용을 허용하는 한 문제 없음).
        return try {
            val request = Request.Builder().url("${baseUrl}auth/token/refresh").get().build()
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val token = JSONObject(body).optString("access_token")
                token.ifBlank { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
