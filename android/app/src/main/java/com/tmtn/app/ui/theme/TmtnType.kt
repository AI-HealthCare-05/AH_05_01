package com.tmtn.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import com.tmtn.app.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Bundled Pretendard typography; honors app text scale and Android font scaling. */
val TmtnFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

object TmtnType {
    val display: TextStyle @Composable get() = scaled(36.sp, 44.sp, FontWeight.Bold)
    val headline: TextStyle @Composable get() = scaled(28.sp, 36.sp, FontWeight.Bold)
    val title: TextStyle @Composable get() = scaled(22.sp, 30.sp, FontWeight.SemiBold)
    val bodyLarge: TextStyle @Composable get() = scaled(18.sp, 28.sp, FontWeight.Medium)
    val body: TextStyle @Composable get() = scaled(16.sp, 25.sp, FontWeight.Normal)
    val label: TextStyle @Composable get() = scaled(14.sp, 21.sp, FontWeight.SemiBold)
    val caption: TextStyle @Composable get() = scaled(14.sp, 21.sp, FontWeight.Medium)
    val navigationLabel: TextStyle @Composable get() = scaled(13.sp, 18.sp, FontWeight.Medium)

    @Composable
    private fun scaled(baseSize: androidx.compose.ui.unit.TextUnit, baseLineHeight: androidx.compose.ui.unit.TextUnit, weight: FontWeight): TextStyle {
        val scale = LocalTmtnTextScale.current
        return TextStyle(
            fontFamily = TmtnFontFamily,
            fontSize = baseSize * scale,
            lineHeight = baseLineHeight * scale,
            fontWeight = weight,
            letterSpacing = if (baseSize.value >= 22f) (-0.4).sp else 0.sp,
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
}

/** Material fields, dialogs and menus inherit the same seven styles. */
@Composable
internal fun tmtnTypography() = Typography(
    displayLarge = TmtnType.display, displayMedium = TmtnType.display, displaySmall = TmtnType.display,
    headlineLarge = TmtnType.headline, headlineMedium = TmtnType.headline, headlineSmall = TmtnType.title,
    titleLarge = TmtnType.title, titleMedium = TmtnType.bodyLarge, titleSmall = TmtnType.label,
    bodyLarge = TmtnType.body, bodyMedium = TmtnType.body, bodySmall = TmtnType.caption,
    labelLarge = TmtnType.label, labelMedium = TmtnType.label, labelSmall = TmtnType.caption,
)
