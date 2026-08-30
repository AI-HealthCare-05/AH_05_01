package kr.tmtn.app.designsystem

import androidx.compose.ui.unit.dp

/**
 * 틈튼 간격·모서리 토큰 — Figma 컬렉션 `TMTN / Metrics` 와 1:1. (v5 신규)
 */
object TmtnSpace {
    val S4 = 4.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val S20 = 20.dp
    val S24 = 24.dp
    val S32 = 32.dp
    val S40 = 40.dp
    val S48 = 48.dp

    /** 화면 좌우 여백 */
    val ScreenMargin = 20.dp
    val ScreenMarginCompact = 16.dp
}

object TmtnRadius {
    val Input = 12.dp
    val CardSmall = 12.dp
    val Card = 16.dp
    val CardToday = 24.dp
    val Sheet = 24.dp
    val Button = 14.dp
    val Pill = 999.dp
}

object TmtnTarget {
    /** 누를 수 있는 것의 최소 크기 */
    val Min = 48.dp
    val Large = 56.dp
}

object TmtnFocus {
    val Width = 3.dp
    val Offset = 2.dp
}

/**
 * 기록 달력 날짜 칸.
 *
 *   실천   = Primary 로 꽉 채운 원 + OnPrimary 숫자
 *   쉼     = OnSurface **실선** 테두리 [RingWidth] + DisabledContainer 바탕 + OnSurface 숫자
 *   미완료 = Outline **점선** 테두리 [RingWidth] (`dashPathEffect(floatArrayOf(2.5f, 2.5f))`)
 *   오늘   = Secondary **실선** 테두리 [TodayRingWidth] + OnSurface 숫자
 *
 * 실선 = 쉼(내가 고른 것), 점선 = 미완료(빠진 것).
 * 색만이 아니라 선 종류로 구분한다. 이 대비를 없애면 두 상태를 못 알아본다.
 */
object TmtnCalendar {
    val CellSize = 34.dp
    val RingWidth = 1.5.dp
    val TodayRingWidth = 2.dp
    val LegendSwatch = 12.dp
    val Dash = 2.5f
    val Gap = 2.5f
}

/**
 * 화면 높이 예산. 390×844 기준.
 * 844 − 상태바 44 − 앱바 64 − 하단탭 104 = **본문 632dp**
 * 스크롤 없는 화면은 이 안에 들어와야 한다.
 */
object TmtnLayout {
    val StatusBar = 44.dp
    val AppBar = 64.dp
    val NavBar = 104.dp
    val ContentBudget = 632.dp
}
