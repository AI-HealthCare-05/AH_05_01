package com.tmtn.app.network

import android.util.Base64
import org.json.JSONObject

/**
 * ⚠️ 2026-09-17 추가(QA #3 후속 - "다른 계정에 요청 전송 없음").
 *
 * 로그인 access_token(JWT) payload의 user_id를, 서명 검증 없이 로컬 상관관계 키로만
 * 꺼내 쓴다. 서버가 모든 요청을 다시 인증하므로 이건 보안 경계가 아니라, 로컬에 남은
 * "저장 대기" 요청(PendingExerciseAction)이 로그아웃 후 다른 계정으로 로그인했을 때
 * 그 계정으로 새어나가지 않게 막는 용도임.
 *
 * user_id는 백엔드 AccessToken.for_user()의 no_copy_claims라 토큰이 갱신(refresh)돼도
 * 같은 계정이면 항상 같은 값이 나온다(app/core/jwt/tokens.py) - TokenAuthenticator의
 * 조용한 401 갱신 때문에 값이 흔들리지 않음.
 */
object AccountKey {

    /** 지금 로그인된 계정의 키. 로그인 안 돼 있으면 null. */
    fun current(): String? = fromToken(TokenHolder.accessToken)

    fun fromToken(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null
        return runCatching {
            val payload = JSONObject(decodeSegment(parts[1]))
            payload.optString("user_id").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun decodeSegment(segment: String): String {
        var normalized = segment.replace('-', '+').replace('_', '/')
        val paddingNeeded = (4 - normalized.length % 4) % 4
        normalized += "=".repeat(paddingNeeded)
        val bytes = Base64.decode(normalized, Base64.DEFAULT)
        return String(bytes, Charsets.UTF_8)
    }
}
