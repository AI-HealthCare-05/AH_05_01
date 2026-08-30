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

    /* ── 달력 ────────────────────────────────────────────── */

    /** 주간·월간 달력 모두 **월요일 시작**이다. */
    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    /** 그 날이 속한 주의 월요일 (키 문자열) */
    fun weekStartKey(key: String): String = runCatching {
        weekStart(LocalDate.parse(key)).toString()
    }.getOrDefault(key)

    /** 이번 주 월요일부터 일요일까지 7일 */
    fun thisWeek(): List<String> {
        val mon = weekStart(LocalDate.now())
        return (0..6).map { mon.plusDays(it.toLong()).toString() }
    }

    /**
     * 월간 달력 격자. 앞뒤로 빈 칸을 두지 않고 **이전·다음 달 날짜로 채운다.**
     * 6주 × 7일 = 42칸 고정이라 달을 넘겨도 표 높이가 흔들리지 않는다.
     */
    fun monthGrid(year: Int, month: Int): List<String> {
        val first = LocalDate.of(year, month, 1)
        val start = weekStart(first)
        return (0 until 42).map { start.plusDays(it.toLong()).toString() }
    }

    fun monthOf(key: String): Int = runCatching { LocalDate.parse(key).monthValue }.getOrDefault(0)
    fun dayOf(key: String): Int = runCatching { LocalDate.parse(key).dayOfMonth }.getOrDefault(0)

    /** 오늘보다 뒤인가 (아직 오지 않은 날) */
    fun isFuture(key: String): Boolean = runCatching {
        LocalDate.parse(key).isAfter(LocalDate.now())
    }.getOrDefault(false)

    val weekdayHeaders = listOf("월", "화", "수", "목", "금", "토", "일")
}

/** 초 → `08:04` */
fun Int.asClock(): String = "%02d:%02d".format(this / 60, this % 60)
