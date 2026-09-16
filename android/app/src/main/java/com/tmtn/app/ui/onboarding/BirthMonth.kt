package com.tmtn.app.ui.onboarding

import java.time.YearMonth

/** Mirrors the existing server's year/month and minimum-age validation, without collecting a day. */
internal fun isValidBirthMonth(year: String, month: String, today: YearMonth = YearMonth.now()): Boolean {
    val y = year.toIntOrNull() ?: return false
    val m = month.toIntOrNull() ?: return false
    if (year.length != 4 || y !in 1900..today.year || m !in 1..12) return false
    return (today.year - y) * 12 + today.monthValue - m >= 14 * 12
}
