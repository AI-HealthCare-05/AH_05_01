package com.tmtn.app.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 어느 API 응답에서든 401(토큰 만료)이 감지되면 여기에 신호를 켬.
 * MainActivity가 이 값을 구독해서, 켜지면 H07(세션 만료) 화면을 보여줌.
 * TokenHolder와 같은 패턴(전역 싱글턴, Compose 상태 아님 - 여러 화면에서 공유해야 해서).
 */
object SessionManager {
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired

    fun markExpired() {
        _sessionExpired.value = true
    }

    /** H07에서 "로그인하기" 누른 뒤, 다시 401을 만나도 새로 감지할 수 있게 초기화. */
    fun clear() {
        _sessionExpired.value = false
    }
}
