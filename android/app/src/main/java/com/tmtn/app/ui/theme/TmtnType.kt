package com.tmtn.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Figma tokens.css의 타이포 7단계 그대로.
 * ⚠️ 폰트는 원래 "Noto Sans KR"인데, 폰트 파일(res/font 폴더의 ttf)을 아직 프로젝트에
 * 추가하지 않아서 지금은 시스템 기본 산세리프로 대체했어요. 나중에 Noto Sans KR
 * 폰트 파일을 res/font에 넣고 FontFamily(Font(R.font.notosanskr_regular, FontWeight.Normal), ...)
 * 형태로 바꾸면 정확히 일치해요. 크기·줄간격·굵기는 이미 Figma 값 그대로입니다.
 */
private val TmtnFontFamily = FontFamily.SansSerif

object TmtnType {
    val display = TextStyle(
        fontFamily = TmtnFontFamily, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold
    )
    val headline = TextStyle(
        fontFamily = TmtnFontFamily, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold
    )
    val title = TextStyle(
        fontFamily = TmtnFontFamily, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold
    )
    val bodyLarge = TextStyle(
        fontFamily = TmtnFontFamily, fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium
    )
    val body = TextStyle(
        fontFamily = TmtnFontFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal
    )
    val label = TextStyle(
        fontFamily = TmtnFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold
    )
    val caption = TextStyle(
        fontFamily = TmtnFontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium
    )
}