package com.tmtn.app.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 로그인 토큰을 메모리 + 암호화된 저장소(EncryptedSharedPreferences)에 같이 보관.
 * 팀 문서: "refresh token 원문 저장 금지" — 지금은 refresh token을 서버가 안 내려주고
 * access token만 다루지만, 그래도 안전하게 암호화해서 저장함.
 *
 * ⚠️ 앱 시작 시(MainActivity.onCreate) init(context)를 반드시 먼저 불러야 저장된
 * 토큰을 불러옴. 안 부르면 예전처럼 메모리에서만 동작(매번 로그아웃 상태로 시작).
 */
object TokenHolder {
    private const val PREFS_NAME = "tmtn_secure_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"

    private var prefs: SharedPreferences? = null
    private var memoryToken: String? = null

    fun init(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            memoryToken = prefs?.getString(KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            // ⚠️ 2026-09-02: "저장소 파일이 깨지는 경우" 대응 방향은 맞았는데, 폴백이
            // 암호화 안 된 일반 SharedPreferences였음 — 그 뒤로 accessToken setter가
            // 이 평문 저장소에 계속 토큰을 그대로 써서, 한 번 이 경로를 타면 그 기기에서는
            // 영구히 평문 저장으로 남는 문제가 있었음(2026-09-03 리뷰 반영).
            // 깨진 파일만 지우고 EncryptedSharedPreferences를 다시 만들어보고, 그것도
            // 실패하면 prefs=null로 두어 메모리 전용(재로그인 필요)으로 동작시킴 —
            // 어느 쪽이든 평문 저장은 안 됨.
            context.applicationContext.deleteSharedPreferences(PREFS_NAME)
            prefs = runCatching {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
            memoryToken = null
        }
    }

    var accessToken: String?
        get() = memoryToken
        set(value) {
            memoryToken = value
            prefs?.edit()?.putString(KEY_ACCESS_TOKEN, value)?.apply()
        }

    /** 로그아웃 시 호출 — 메모리와 저장소 둘 다에서 지움. */
    fun clear() {
        accessToken = null
    }
}
