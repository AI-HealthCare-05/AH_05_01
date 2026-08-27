package kr.tmtn.app.ui

import java.time.LocalDate
import java.time.LocalTime

/** DESIGN.md 날짜 형식: `2026. 8. 27.` */
object TmtnDate {

    fun todayKey(): String = LocalDate.now().toString()   // 2026-08-27

    fun label(key: String): String = runCatching {
        val d = LocalDate.parse(key)
        "${d.year}. ${d.monthValue}. ${d.dayOfMonth}."
    }.getOrDefault(key)

    fun weekdayLabel(key: String): String = runCatching {
        val d = LocalDate.parse(key)
        val names = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")
        "${label(key)} ${names[d.dayOfWeek.value - 1]}"
    }.getOrDefault(label(key))

    fun timeLabel(): String {
        val t = LocalTime.now()
        val ampm = if (t.hour < 12) "오전" else "오후"
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "$ampm $h:%02d".format(t.minute)
    }

    fun lastDays(n: Int): List<String> =
        (0 until n).map { LocalDate.now().minusDays((n - 1 - it).toLong()).toString() }
}

/** 초 → `08:04` */
fun Int.asClock(): String = "%02d:%02d".format(this / 60, this % 60)
