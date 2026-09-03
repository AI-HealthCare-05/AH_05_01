package com.tmtn.app.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * ⚠️ 2026-09-03 리뷰 반영: OkHttpClient에 CookieJar가 아예 없어서, 로그인 응답이 httponly
 * 쿠키로 내려주는 refresh_token이 클라이언트에 저장조차 안 되고 있었음. 그러니 나중에
 * /auth/token/refresh를 호출해도 쿠키가 없어서 애초에 갱신이 불가능한 상태였음.
 *
 * 앱 프로세스가 살아있는 동안만 메모리에 들고 있는 가장 단순한 구현 - 앱을 완전히 종료하면
 * 다시 로그인해야 함(그래도 지금(=아예 저장 안 함)보다는 훨씬 나음). 재시작 후에도 유지하려면
 * EncryptedSharedPreferences 등에 영구 저장하는 CookieJar로 나중에 교체하면 됨.
 */
class InMemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, List<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = cookies
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val saved = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val valid = saved.filter { it.expiresAt > now }
        if (valid.size != saved.size) store[url.host] = valid
        return valid
    }
}
