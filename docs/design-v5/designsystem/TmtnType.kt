package kr.tmtn.app.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 틈튼 글자 토큰 — Figma v5 텍스트 스타일 7종과 1:1.
 *
 * 2026-08-29 크기 조정. v4 보다 전반적으로 커졌다.
 * 만성질환 사용자를 염두에 둔 값이라 임의로 줄이지 말 것.
 *
 *   Display 32→40 · Headline 24→32 · Title 18→24
 *   BodyLarge 17→19 · Body 15→16 · Caption 12→14
 *
 * Figma 는 Noto Sans KR 을 쓴다. 앱은 시스템 기본 한글 폰트를 쓰고 있으며,
 * 브랜드 폰트를 넣을 때 여기 fontFamily 만 추가하면 된다.
 * (Noto Sans KR 에는 SemiBold(600)가 없어 Figma 쪽도 Bold(700)로 대체돼 있다.)
 *
 * 숫자가 나오는 곳은 `fontFeatureSettings = "tnum"` 이 걸려 있다.
 * 타이머·점수·연속일수의 자릿수가 흔들리지 않는다.
 */
private val KoLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private const val Tnum = "tnum"

object TmtnText {
    /** 40/50 · 오늘의 핵심 수치. **화면당 하나만** */
    val Display = TextStyle(fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black, lineHeightStyle = KoLineHeight, fontFeatureSettings = Tnum)

    /** 32/42 · 화면 제목 */
    val Headline = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, lineHeightStyle = KoLineHeight, fontFeatureSettings = Tnum)

    /** 24/32 · 카드·섹션 제목 */
    val Title = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, lineHeightStyle = KoLineHeight, fontFeatureSettings = Tnum)

    /** 19/28 · 핵심 설명. 큰 글씨 우선 문장 */
    val BodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, lineHeightStyle = KoLineHeight, fontFeatureSettings = Tnum)

    /** 16/26 · 기본 본문·입력 */
    val Body = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal, lineHeightStyle = KoLineHeight, fontFeatureSettings = Tnum)

    /** 14/20 Bold · 버튼·탭·필드 라벨 */
    val Label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, lineHeightStyle = KoLineHeight, fontFeatureSettings = Tnum)

    /** 14/20 Medium · 날짜·단위·보조 정보. **핵심 정보에는 쓰지 말 것** */
    val Caption = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, lineHeightStyle = KoLineHeight, fontFeatureSettings = Tnum)

    /** 타이머·거리·계단수처럼 큰 숫자 하나만 보여줄 때. 7단계 밖의 예외 */
    val Metric = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, lineHeightStyle = KoLineHeight, fontFeatureSettings = Tnum)
}

internal val TmtnTypography = Typography(
    displaySmall = TmtnText.Display,
    headlineMedium = TmtnText.Headline,
    titleMedium = TmtnText.Title,
    bodyLarge = TmtnText.BodyLarge,
    bodyMedium = TmtnText.Body,
    labelLarge = TmtnText.Label,
    labelSmall = TmtnText.Caption,
)
