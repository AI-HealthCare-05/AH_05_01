package com.tmtn.app.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * refresh_token 쿠키를 암호화 저장소(EncryptedSharedPreferences)에 보관하는 CookieJar.
 *
 * ⚠️ 2026-09-08 반영: 예전 InMemoryCookieJar는 이름 그대로 메모리에만 들고 있어서, 앱을
 * 완전히 종료하면 refresh_token이 사라졌음. 그러면 서버 쪽 리프레시 토큰 수명을 14일로
 * 잡아도 실제로는 "앱 껐다 켜고 액세스 토큰 만료(1시간) 지나면 재로그인"이 됐음.
 * 액세스 토큰이 이미 같은 방식으로 저장되고 있어서(TokenHolder), 쿠키도 같은 저장소 패턴을
 * 그대로 씀 - 이제 앱을 껐다 켜도 세션이 이어지고, 정말 14일 넘게 안 쓴 경우에만 재로그인함.
 *
 * ⚠️ 저장소 생성이 실패했을 때 평문 SharedPreferences로 폴백하지 않음 - TokenHolder가
 * 2026-09-03에 같은 이유로 고쳤던 부분(한 번 평문 경로를 타면 그 기기에서 계속 평문으로
 * 남는 문제). 깨진 파일만 지우고 한 번 더 시도해보고, 그래도 실패하면 메모리 전용으로
 * 동작함(예전과 같은 동작 = 재로그인 필요, 대신 평문 저장은 절대 안 됨).
 *
 * ⚠️ MainActivity.onCreate에서 init(context)를 반드시 먼저 불러야 저장된 쿠키를 불러옴.
 * 안 부르면 메모리 전용으로만 동작함.
 */
object PersistentCookieJar : CookieJar {
    private const val PREFS_NAME = "tmtn_secure_cookies"

    private var prefs: SharedPreferences? = null

    /** host -> 그 host에 저장된 쿠키들. 저장소가 있으면 저장소와 항상 같은 내용을 유지함. */
    private val memory = mutableMapOf<String, MutableList<Cookie>>()

    @Synchronized
    fun init(context: Context) {
        prefs = runCatching { createPrefs(context) }.getOrElse {
            // 저장소 파일이 깨진 경우 - 지우고 한 번 더 시도(TokenHolder와 같은 방식).
            context.applicationContext.deleteSharedPreferences(PREFS_NAME)
            runCatching { createPrefs(context) }.getOrNull()
        }
        memory.clear()
        loadAllFromPrefs()
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * 저장은 OkHttp Cookie.toString()이 만들어주는 Set-Cookie 문자열을 그대로 씀
     * (이름·값뿐 아니라 expires·path·secure·httponly까지 포함됨). 읽을 때 Cookie.parse()로
     * 같은 형태로 복원되므로 왕복이 정확함.
     */
    private fun loadAllFromPrefs() {
        val stored = prefs?.all ?: return
        for ((host, value) in stored) {
            @Suppress("UNCHECKED_CAST")
            val serialized = value as? Set<String> ?: continue
            val url = HttpUrl.Builder().scheme("https").host(host).build()
            val cookies = serialized.mapNotNull { Cookie.parse(url, it) }.toMutableList()
            if (cookies.isNotEmpty()) memory[host] = cookies
        }
    }

    private fun persist(host: String, cookies: List<Cookie>) {
        val editor = prefs?.edit() ?: return
        if (cookies.isEmpty()) editor.remove(host) else editor.putStringSet(host, cookies.map { it.toString() }.toSet())
        editor.apply()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return

        // ⚠️ 예전 구현은 store[url.host] = cookies로 그 host의 쿠키를 통째로 갈아치웠음.
        // 응답이 쿠키 하나만 새로 내려주면 나머지가 사라지므로, 같은 이름만 교체하고
        // 나머지는 남기는 방식으로 바꿈(지금은 refresh_token 하나뿐이지만 안전하게).
        val current = memory.getOrPut(url.host) { mutableListOf() }
        for (cookie in cookies) {
            current.removeAll { it.name == cookie.name }
            current.add(cookie)
        }
        persist(url.host, current)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = memory[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val valid = stored.filter { it.expiresAt > now }
        if (valid.size != stored.size) {
            // 만료된 건 메모리·저장소 양쪽에서 정리.
            memory[url.host] = valid.toMutableList()
            persist(url.host, valid)
        }
        return valid
    }

    /**
     * 로그아웃·계정 삭제 시 호출. 삭제된 계정의 refresh_token이 남아 있으면 자동 갱신이
     * 엉뚱하게 돌 수 있으므로 메모리와 저장소 양쪽을 다 비움(ApiClient.clearSession 참고).
     */
    @Synchronized
    fun clear() {
        memory.clear()
        prefs?.edit()?.clear()?.apply()
    }
}
