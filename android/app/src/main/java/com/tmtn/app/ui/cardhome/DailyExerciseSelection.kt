package com.tmtn.app.ui.cardhome

import android.content.SharedPreferences
import com.tmtn.app.network.model.ExerciseMissionOption
import com.tmtn.app.ui.common.isSensorMissionExecType
import java.security.MessageDigest
import java.time.LocalDate

/** 같은 계정·날짜에는 순서와 완료 여부가 바뀌어도 같은 운동을 보여준다. */
internal fun selectDailyExercises(
    options: List<ExerciseMissionOption>,
    date: LocalDate,
    accountKey: String,
    savedIds: List<String> = emptyList(),
): List<ExerciseMissionOption> {
    val unique = options.distinctBy { it.catalog_entry_id }
    val byId = unique.associateBy { it.catalog_entry_id.toString() }
    val kept = savedIds.distinct().mapNotNull(byId::get).take(5)
    val ranked = unique.filterNot { it in kept }.sortedBy {
        selectionHash("$date|$accountKey|${it.catalog_entry_id}")
    }
    // 센서 운동 2개를 우선 확보하고, 직접 확인 운동도 함께 제안한다.
    val sensorCount = kept.count { isSensorMissionExecType(it.exec_type) }
    val sensors = ranked.filter { isSensorMissionExecType(it.exec_type) }
        .take((2 - sensorCount).coerceAtLeast(0).coerceAtMost(5 - kept.size))
    val remaining = ranked.filterNot { it in sensors }
        .sortedBy { isSensorMissionExecType(it.exec_type) }
    return (kept + sensors + remaining).take(5)
        .sortedBy { !isSensorMissionExecType(it.exec_type) }
}

/** 저장하는 값은 날짜와 카탈로그 ID뿐이며 완료·보상은 항상 서버 응답을 사용한다. */
internal fun loadDailyExercises(
    preferences: SharedPreferences,
    options: List<ExerciseMissionOption>,
    date: LocalDate,
    accountKey: String,
): List<ExerciseMissionOption> {
    if (options.isEmpty()) return emptyList()
    val key = "selection_${selectionHash(accountKey)}"
    val saved = preferences.getString(key, null)?.split('|').orEmpty()
    val ids = if (saved.firstOrNull() == date.toString()) saved.drop(1) else emptyList()
    val selection = selectDailyExercises(options, date, accountKey, ids)
    val value = (listOf(date.toString()) + selection.map { it.catalog_entry_id.toString() }).joinToString("|")
    if (preferences.getString(key, null) != value) preferences.edit().putString(key, value).apply()
    return selection
}

private fun selectionHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
