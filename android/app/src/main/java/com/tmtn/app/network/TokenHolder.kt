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
            // ⚠️ 기기에 따라 "데이터 삭제"를 해도 암호화 키(Keystore)만 남고 저장소 파일이
            // 깨지는 경우가 있음 — 그러면 복호화가 실패해서 예외가 남. 이때는 안전하게
            // "로그인 안 한 상태"로 취급하고, 다음 저장부터는 새로 깨끗하게 씀.
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .also { it.edit().clear().apply() }
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
