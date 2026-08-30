package kr.tmtn.app.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 틈튼 색 토큰 — Figma v5 (`TMTN / 2 Semantic` · 모드 "순백 + 채도 블록") 와 1:1.
 *
 * 2026-08-29 팔레트 교체. v4 의 초록(`#0C3B2E` `#6D9773`)은 폐기됐다.
 * 이름은 v4 와 그대로 두었으니 화면 코드는 고칠 필요가 없다. 값만 바뀐다.
 *
 * 규칙 하나: **Secondary(주황)는 "오늘"에만 쓴다.**
 * 완료·실천·주 버튼은 전부 Primary(먹색)다. 주황을 늘리면 화면이 시끄러워진다.
 *
 * 다크 모드는 Figma 에 정의가 없어 **의도적으로 만들지 않았다.**
 */
object TmtnColor {
    val Background = Color(0xFFFFFFFF)          // color/background · 순백
    val Surface = Color(0xFFF7F4EE)             // color/surface · 카드·시트
    val OnSurface = Color(0xFF16181C)           // color/on-surface · 먹색
    val OnSurfaceVariant = Color(0xFF666A71)    // color/on-surface-variant

    val Primary = Color(0xFF16181C)             // color/primary
    val OnPrimary = Color(0xFFFFFFFF)           // color/on-primary
    val Secondary = Color(0xFFFF7A1A)           // color/secondary · 오늘 전용
    val SecondaryContainer = Color(0xFFF7F4EE)  // color/secondary-container

    val Wood = Color(0xFFFF7A1A)                // color/wood
    val WoodContainer = Color(0xFFF7F4EE)       // color/wood-container

    val Reward = Color(0xFFFF7A1A)              // color/reward
    val OnReward = Color(0xFF16181C)            // color/on-reward
    val RewardContainer = Color(0xFFFFB400)     // color/reward-container

    val Error = Color(0xFFC92A2A)               // color/error
    val OnError = Color(0xFFFFFFFF)             // color/on-error
    val ErrorContainer = Color(0xFFFDECEC)      // color/error-container

    val Outline = Color(0xFFB8B2A6)             // color/outline · 미완료 링
    val OutlineVariant = Color(0xFFE5E0D6)      // color/outline-variant · 옅은 구분선

    val Scrim = Color(0xFF16181C)               // color/scrim · 32% 알파로 사용
    val DisabledContainer = Color(0xFFEFEAE0)   // color/disabled-container · 쉼 바탕
    val OnDisabled = Color(0xFF807D76)          // color/on-disabled · 예정 날짜

    // ── 댐 재료 5종 (v5 신규) ──────────────────────────────
    val MaterialBranch = Color(0xFFFF7A1A)      // 나뭇가지 · 움직임/유산소
    val MaterialStone = Color(0xFFE03131)       // 받침돌   · 근력
    val MaterialWater = Color(0xFF1C6DD0)       // 물길     · 수분
    val MaterialEarth = Color(0xFFFFB400)       // 다짐흙   · 생활리듬
    val MaterialLeaf = Color(0xFF2F9E44)        // 새잎     · 식사/기록
    val MaterialContainer = Color(0xFFF7F4EE)   // 5종 공통 배경

    /** 지난달·다음달 날짜처럼 한 단계 더 흐린 글자 */
    val OnSurfaceFaint = Color(0xFFB8B2A6)

    /** 그림자 색. 먹색을 옅게 깐다 (초록 그림자 아님) */
    val Shadow = Color(0xFF161411)
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
