package com.tmtn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Figma 핸드오프 HANDOFF.md: "다크 모드는 디자인에 정의가 없습니다." 라이트 컬러만 있음.
 * Material3의 기본 색상 슬롯(primary/secondary 등)만으로는 우리 토큰(wood, reward 등)을
 * 다 못 담아서, LocalTmtnColors로 원본 토큰에 직접 접근할 수 있게 같이 제공함.
 * (버튼/칩 등 커스텀 컴포넌트에서 LocalTmtnColors.current.wood 처럼 바로 씀)
 */
private val TmtnLightColorScheme = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = ButtonPrimaryLabel,
    secondary = ColorSecondary,
    onSecondary = ColorOnSurface,
    secondaryContainer = ColorSecondaryContainer,
    onSecondaryContainer = ColorOnSurface,
    background = ColorBackground,
    onBackground = ColorOnSurface,
    surface = ColorSurface,
    surfaceVariant = ColorSelectionSurface,
    surfaceDim = ColorSurface,
    surfaceBright = ColorBackground,
    surfaceContainerLowest = ColorBackground,
    surfaceContainerLow = ColorSurface,
    surfaceContainer = ColorSurface,
    surfaceContainerHigh = ColorSelectionSurface,
    surfaceContainerHighest = ColorOutlineVariant,
    primaryContainer = ColorSelectionSurface,
    onPrimaryContainer = ColorOnSurface,
    tertiary = ColorWood,
    onTertiary = ColorOnSurface,
    tertiaryContainer = ColorWoodContainer,
    onTertiaryContainer = ColorOnSurface,
    inverseSurface = ColorOnSurface,
    inverseOnSurface = ColorBackground,
    inversePrimary = ColorSecondaryContainer,
    onSurface = ColorOnSurface,
    onSurfaceVariant = ColorOnSurfaceVariant,
    outline = ColorOutline,
    outlineVariant = ColorOutlineVariant,
    error = ColorError,
    onError = Color.White,
    errorContainer = ColorErrorContainer,
    onErrorContainer = ColorError,
    surfaceTint = Color.Transparent,
)

data class TmtnColors(
    val primary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val background: Color,
    val surface: Color,
    val outline: Color,
    val outlineVariant: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val wood: Color,
    val woodContainer: Color,
    val reward: Color,
    val rewardContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val disabledContainer: Color,
    val onDisabled: Color,
    val navigationContainer: Color,
    val navigationIndicator: Color,
    val navigationIconActive: Color,
    val navigationIconInactive: Color,
    val navigationLabelActive: Color,
    val navigationLabelInactive: Color,
)

private val DefaultTmtnColors = TmtnColors(
    primary = ColorPrimary,
    onSurface = ColorOnSurface,
    onSurfaceVariant = ColorOnSurfaceVariant,
    background = ColorBackground,
    surface = ColorSurface,
    outline = ColorOutline,
    outlineVariant = ColorOutlineVariant,
    secondary = ColorSecondary,
    secondaryContainer = ColorSecondaryContainer,
    wood = ColorWood,
    woodContainer = ColorWoodContainer,
    reward = ColorReward,
    rewardContainer = ColorRewardContainer,
    error = ColorError,
    errorContainer = ColorErrorContainer,
    disabledContainer = ColorDisabledContainer,
    onDisabled = ColorOnDisabled,
    navigationContainer = NavigationContainer,
    navigationIndicator = NavigationIndicator,
    navigationIconActive = NavigationIconActive,
    navigationIconInactive = NavigationIconInactive,
    navigationLabelActive = NavigationLabelActive,
    navigationLabelInactive = NavigationLabelInactive,
)

val LocalTmtnColors = staticCompositionLocalOf { DefaultTmtnColors }

val LocalTmtnTextScale = staticCompositionLocalOf { 1f }

/** Figma light palette. Text uses Android sp scaling without an extra app multiplier. */
@Composable
fun TMTNv1Theme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTmtnColors provides DefaultTmtnColors, LocalTmtnTextScale provides 1f) {
        MaterialTheme(colorScheme = TmtnLightColorScheme, typography = tmtnTypography(), content = content)
    }
}
