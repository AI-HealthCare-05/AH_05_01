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

// High contrast strengthens muted labels, boundaries and orange numeric text.
// The standard palette keeps the Figma accent #FF7A1A.
private val HighContrastTmtnColors = DefaultTmtnColors.copy(
    onSurfaceVariant = ColorOnSurface,
    outlineVariant = ColorOutline,
    secondary = ColorOnSurface,
)

val LocalTmtnTextScale = staticCompositionLocalOf { 1f }

/** 이름은 기존 MainActivity.kt가 참조하던 그대로 유지 (TMTNv1Theme).
 * HANDOFF.md 기준 다크모드 정의가 없어서 시스템 설정과 무관하게 항상 라이트로 고정.
 *
 * ⚠️ 2026-09-04 QA(P0-6) 반영: AccessibilitySettingsHolder를 구독해서 글자 크기·고대비를
 * 전역에 반영함(LocalTmtnTextScale/LocalTmtnColors). 설정이 바뀌면 이 값들을 구독하는
 * 모든 화면이 자동으로 다시 그려짐. */
@Composable
fun TMTNv1Theme(content: @Composable () -> Unit) {
    val seniorMode = AccessibilitySettingsHolder.seniorMode.value
    val textScale = textScaleHintToFactor(AccessibilitySettingsHolder.textScaleHint.value)

    CompositionLocalProvider(
            LocalTmtnColors provides (if (seniorMode) HighContrastTmtnColors else DefaultTmtnColors),
            LocalTmtnTextScale provides textScale,
    ) {
        val colors = LocalTmtnColors.current
        MaterialTheme(colorScheme = TmtnLightColorScheme.copy(
            onSurfaceVariant = colors.onSurfaceVariant,
            outlineVariant = colors.outlineVariant,
            secondary = colors.secondary,
        ), typography = tmtnTypography()) {
            content()
        }
    }
}
