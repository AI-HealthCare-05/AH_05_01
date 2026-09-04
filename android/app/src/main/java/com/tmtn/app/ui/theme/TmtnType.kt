package com.tmtn.app.ui.theme

import androidx.compose.runtime.Composable
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
 *
 * ⚠️ 2026-09-04 QA(P0-6) 반영: 예전엔 전부 고정 val이라 "내 정보 > 접근성 > 글자 크기"를
 * 켜도 화면에 반영되는 코드가 없었음. 각 스타일을 @Composable get()으로 바꿔서
 * LocalTmtnTextScale(TMTNv1Theme이 AccessibilitySettingsHolder 값으로 채워줌)을 읽어
 * fontSize·lineHeight에 곱해줌. 호출부(`TmtnType.body` 등)는 전부 이미 Composable 안에서
 * 쓰이고 있어서 코드 변경 없이 그대로 적용됨.
 */
private val TmtnFontFamily = FontFamily.SansSerif

object TmtnType {
    val display: TextStyle @Composable get() = scaled(32.sp, 40.sp, FontWeight.Bold)
    val headline: TextStyle @Composable get() = scaled(28.sp, 36.sp, FontWeight.Bold)
    val title: TextStyle @Composable get() = scaled(22.sp, 30.sp, FontWeight.Bold)
    val bodyLarge: TextStyle @Composable get() = scaled(18.sp, 28.sp, FontWeight.Medium)
    val body: TextStyle @Composable get() = scaled(16.sp, 24.sp, FontWeight.Normal)
    val label: TextStyle @Composable get() = scaled(14.sp, 20.sp, FontWeight.Bold)
    val caption: TextStyle @Composable get() = scaled(12.sp, 18.sp, FontWeight.Medium)

    @Composable
    private fun scaled(baseSize: androidx.compose.ui.unit.TextUnit, baseLineHeight: androidx.compose.ui.unit.TextUnit, weight: FontWeight): TextStyle {
        val scale = LocalTmtnTextScale.current
        return TextStyle(
            fontFamily = TmtnFontFamily,
            fontSize = baseSize * scale,
            lineHeight = baseLineHeight * scale,
            fontWeight = weight,
        )
    }
}