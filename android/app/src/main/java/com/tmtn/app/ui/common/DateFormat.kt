package com.tmtn.app.ui.common

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * ⚠️ 2026-09-04 QA 반영: 앱 안에서 날짜 표기가 세 가지로 따로 놀고 있었음
 * (점 포맷 "2026. 8. 27." / ISO "2026-08-29" / "09. 04." 축약) - 전부 이 함수로 통일.
 * 디자인 스펙(design-v5) 기준 포맷: "yyyy. M. d. 요일" 또는 "yyyy. M. d."
 */
fun LocalDate.toKoreanDateLabel(includeDayOfWeek: Boolean = true): String {
    val base = "$year. $monthValue. $dayOfMonth."
    if (!includeDayOfWeek) return base
    val dayOfWeekLabel = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    return "$base $dayOfWeekLabel"
}

/** "2026-08-29" 같은 ISO 문자열을 받아 "2026. 8. 29."로 바꿈. 파싱 실패하면 원본 그대로 돌려줌
 * (서버가 혹시 다른 형식을 주더라도 화면이 깨지지 않게). */
fun isoDateToKoreanLabel(isoDate: String): String {
    return runCatching { LocalDate.parse(isoDate).toKoreanDateLabel(includeDayOfWeek = false) }
        .getOrDefault(isoDate)
}
