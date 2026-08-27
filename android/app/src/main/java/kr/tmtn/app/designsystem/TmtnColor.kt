package kr.tmtn.app.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 틈튼 색 토큰 — Figma v4 (`TMTN / 2 Semantic`) 와 1:1 로 맞춘다.
 * Figma 변수 이름을 주석에 그대로 남겨 두었으니, 디자인이 바뀌면 여기만 고치면 된다.
 *
 * 다크 모드는 DESIGN.md 에 정의가 없어서 **의도적으로 만들지 않았다.**
 * 임의로 어두운 색을 지어내면 대비 검증을 안 거친 색이 앱에 들어간다.
 */
object TmtnColor {
    val Background = Color(0xFFF4F5F3)          // color/background
    val Surface = Color(0xFFFFFFFF)             // color/surface
    val OnSurface = Color(0xFF0C3B2E)           // color/on-surface
    val OnSurfaceVariant = Color(0xFF527369)    // color/on-surface-variant

    val Primary = Color(0xFF0C3B2E)             // color/primary
    val OnPrimary = Color(0xFFFFFFFF)           // color/on-primary
    val Secondary = Color(0xFF6D9773)           // color/secondary
    val SecondaryContainer = Color(0xFFE5ECE6)  // color/secondary-container

    val Wood = Color(0xFFBB8A52)                // color/wood
    val WoodContainer = Color(0xFFF4ECE3)       // color/wood-container

    val Reward = Color(0xFFFFBA00)              // color/reward
    val OnReward = Color(0xFF0C3B2E)            // color/on-reward
    val RewardContainer = Color(0xFFFFF4D6)     // color/reward-container

    val Error = Color(0xFFB3261E)               // color/error
    val OnError = Color(0xFFFFFFFF)             // color/on-error
    val ErrorContainer = Color(0xFFF3DCDB)      // color/error-container

    val Outline = Color(0xFF748F87)             // color/outline
    val OutlineVariant = Color(0xFFD1D9D5)      // color/outline-variant

    val Scrim = Color(0xFF0C3B2E)               // color/scrim
    val DisabledContainer = Color(0xFFD8DFDB)   // color/disabled-container
    val OnDisabled = Color(0xFF8AA199)          // color/on-disabled
}

internal val TmtnColorScheme: ColorScheme = lightColorScheme(
    primary = TmtnColor.Primary,
    onPrimary = TmtnColor.OnPrimary,
    primaryContainer = TmtnColor.SecondaryContainer,
    onPrimaryContainer = TmtnColor.OnSurface,
    secondary = TmtnColor.Secondary,
    onSecondary = TmtnColor.OnPrimary,
    secondaryContainer = TmtnColor.SecondaryContainer,
    onSecondaryContainer = TmtnColor.OnSurface,
    tertiary = TmtnColor.Wood,
    onTertiary = TmtnColor.OnPrimary,
    tertiaryContainer = TmtnColor.WoodContainer,
    onTertiaryContainer = TmtnColor.OnSurface,
    background = TmtnColor.Background,
    onBackground = TmtnColor.OnSurface,
    surface = TmtnColor.Surface,
    onSurface = TmtnColor.OnSurface,
    surfaceVariant = TmtnColor.SecondaryContainer,
    onSurfaceVariant = TmtnColor.OnSurfaceVariant,
    error = TmtnColor.Error,
    onError = TmtnColor.OnError,
    errorContainer = TmtnColor.ErrorContainer,
    onErrorContainer = TmtnColor.OnSurface,
    outline = TmtnColor.Outline,
    outlineVariant = TmtnColor.OutlineVariant,
    scrim = TmtnColor.Scrim,
)
