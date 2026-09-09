package com.tmtn.app.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 어떤 요청이든 401(토큰 만료/무효)로 응답이 오면, 토큰을 지우고 전역 신호(SessionManager)를
 * 켬. 로그인/회원가입 자체를 요청하는 경로는 "비밀번호 틀림" 같은 걸로도 401이 날 수 있어서
 * 세션 만료로 오인하지 않게 제외함.
 */
class SessionInterceptor : Interceptor {

    companion object {
        /**
         * 이 헤더가 붙은 요청의 401은 전역 세션 만료로 처리하지 않는다.
         * ⚠️ MissionApi 쪽 @Headers 문자열과 값이 같아야 함.
         */
        const val BACKGROUND_SYNC_HEADER = "X-Tmtn-Background-Sync"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val response = chain.proceed(request)

        val isAuthEndpoint = path.contains("/auth/login") ||
            path.contains("/auth/email-verification") ||
            path.contains("/auth/signup")

        // ⚠️ 2026-09-08 QA 반영(회원가입 정보 입력 중에 "다시 로그인해주세요"로 튕기던 버그):
        // 애초에 토큰이 없어서 Authorization 헤더도 안 붙은 요청의 401은 "세션이 만료된 것"이
        // 아니라 "아직 로그인 안 한 것"임. 그런데 그 401까지 세션 만료로 처리하는 바람에,
        // 로그아웃 상태(온보딩 중)에서 보호된 API가 한 번이라도 호출되면 회원가입 도중에
        // 세션 만료 화면이 덮어버렸음(MainActivity의 접근성 설정 조회가 그랬음).
        // 진짜 만료(토큰은 있는데 서버가 거부)는 헤더가 붙어 있으므로 그대로 감지됨.
        val hadAuthHeader = request.header("Authorization") != null

        // ⚠️ 2026-09-08 추가: 사용자가 아무것도 안 눌렀는데 튕기는 두 번째 경로 차단.
        // 센서 측정 배치 전송은 포그라운드 서비스가 30초마다 알아서 보내는 요청이라,
        // 여기서 401이 났다고 화면을 세션 만료로 덮으면 사용자는 미션 도중에 이유 없이
        // 로그인 화면으로 튕김. 토큰 갱신(TokenAuthenticator)은 그대로 시도되고,
        // 그래도 401이면 MissionSyncManager가 쿨다운을 걸고 조용히 대기한다.
        val isBackgroundSync = request.header(BACKGROUND_SYNC_HEADER) != null

        if (response.code == 401 && !isAuthEndpoint && hadAuthHeader && !isBackgroundSync) {
            TokenHolder.clear()
            SessionManager.markExpired()
        }

        return response
    }
}
