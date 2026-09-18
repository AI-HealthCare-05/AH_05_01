package com.tmtn.app.notification

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ⚠️ 2026-09-18 신규 - 알림함(NotificationInboxScreen)이 지금까지 Figma 고정 예시 3개를
 * 그대로 보여주고 있었음("알림 목록을 실제로 저장·조회하는 API가 아직 없어서"). 서버에
 * 알림 이력 테이블·API를 새로 만드는 건 별도 작업이 필요해서(fcm_device_tokens만 있고
 * 발송 이력 테이블이 없음), 우선 기기에서 실제로 알림을 띄운 순간을 로컬에 기록해서
 * 알림함이 최소한 "이 기기에서 실제로 뭐가 왔는지"는 정확히 보여주게 함. 서버 이력
 * API가 나중에 생기면 그쪽으로 교체 가능하도록 저장 방식(제목/본문/시각)을 단순하게 유지.
 */
object NotificationLog {
    private const val PREFS_NAME = "notification_log_prefs"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 20

    data class Entry(val title: String, val body: String, val timestampMillis: Long)

    fun record(context: Context, title: String, body: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = readAll(context).toMutableList()
        current.add(0, Entry(title, body, System.currentTimeMillis()))
        val trimmed = current.take(MAX_ENTRIES)
        val array = JSONArray()
        trimmed.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("body", entry.body)
                    put("timestampMillis", entry.timestampMillis)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    fun readAll(context: Context): List<Entry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Entry(obj.getString("title"), obj.getString("body"), obj.getLong("timestampMillis"))
            }
        }.getOrDefault(emptyList())
    }
}
