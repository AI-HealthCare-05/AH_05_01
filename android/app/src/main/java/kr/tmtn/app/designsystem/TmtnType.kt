package kr.tmtn.app.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Figma v4 텍스트 스타일 7종.
 * Figma 는 Pretendard 가 없어 Noto Sans KR 로 대체돼 있다.
 * 앱은 시스템 기본 한글 폰트를 쓴다. 브랜드 폰트를 넣을 때 여기 fontFamily 만 추가하면 된다.
 */
private val KoLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

object TmtnText {
    val Display = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, lineHeightStyle = KoLineHeight)
    val Headline = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, lineHeightStyle = KoLineHeight)
    val Title = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, lineHeightStyle = KoLineHeight)
    val BodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal, lineHeightStyle = KoLineHeight)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, lineHeightStyle = KoLineHeight)
    val Label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, lineHeightStyle = KoLineHeight)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, lineHeightStyle = KoLineHeight)

    /** 타이머·거리·계단수처럼 큰 숫자 하나만 보여줄 때 */
    val Metric = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, lineHeightStyle = KoLineHeight)
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
