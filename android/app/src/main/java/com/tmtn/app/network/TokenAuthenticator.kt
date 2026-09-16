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
 *
 * ⚠️ 2026-09-08 추가: 동시에 여러 요청이 401을 맞았을 때 각자 refresh를 한 번씩 더 쏘던 문제.
 * 리프레시 토큰 rotation을 켠 뒤로는 refresh 한 번마다 새 리프레시 토큰 쿠키가 내려오므로,
 * 동시에 5개가 refresh를 부르면 쿠키가 5번 덮어써지면서 경합이 생김. 이제 락 안에서
 * "내가 실패할 때 쓰던 토큰"과 "지금 TokenHolder에 있는 토큰"을 비교해서, 다른 스레드가
 * 이미 갱신해 놨으면 refresh를 생략하고 그 토큰으로 바로 재시도한다.
 */
class TokenAuthenticator(private val baseUrl: String, private val refreshClient: OkHttpClient) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 이미 한 번 재시도했는데도 또 401이면(=refresh로 받은 토큰도 안 먹힘) 포기 -
        // 무한 재시도 방지.
        if (responseCount(response) >= 2) return null
        // 애초에 인증 헤더가 없던 요청(로그인 등)이면 우리가 손댈 대상이 아님.
        val staleHeader = response.request.header("Authorization") ?: return null
        val staleToken = staleHeader.removePrefix("Bearer ").trim()

        val newToken = obtainFreshToken(staleToken) ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    /**
     * 401을 맞은 요청이 들고 있던 토큰(staleToken)을 기준으로 "지금 써야 할 토큰"을 돌려준다.
     * 다른 스레드가 이미 갱신해 뒀으면 그 토큰을 그대로 쓰고, 아니면 직접 refresh를 부른다.
     */
    @Synchronized
    private fun obtainFreshToken(staleToken: String): String? {
        val current = TokenHolder.accessToken
        if (!current.isNullOrBlank() && current != staleToken) {
            // 내가 락을 기다리는 동안 다른 요청이 이미 갱신을 끝냈음 - refresh를 또 부르지 않음.
            return current
        }

        val refreshed = requestNewAccessToken() ?: return null
        TokenHolder.accessToken = refreshed
        return refreshed
    }

    private fun requestNewAccessToken(): String? {
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
