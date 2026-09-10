package com.tmtn.app.ui.theme

import androidx.compose.ui.graphics.Color

// Supplied splash_v1/design.json. Reserved for launch/brand artwork.
val ColorBrandForest = Color(0xFF3F5D4B)

// ⚠️ 2026-08-31 팀장님이 준 새 Figma 자료("TMTN_Final_App_UI", 107개 화면 PNG)의
// 실제 픽셀에서 직접 뽑은 색상값. 예전 tokens.css 기준(진한 초록 계열) 팔레트를 완전히
// 교체함 — 초록은 이제 어디에도 안 쓰임. A/B/D/G 여러 화면에서 교차 확인해서 일관됨.

// 색 · 기본
// ⚠️ 2026-09-04 희주조교님 피드백(P2 ⑧) 반영: 순수 검정에 가까운 #16181C가 "너무
// 새까맣다"는 의견이라 #2C2C2C로 올림. 순백(#FFFFFF) 배경 대비 14.0:1로 WCAG AA
// 기준(4.5:1)을 여유 있게 넘음. 하드코딩된 색이 없어서 이 토큰 두 줄만 바꾸면 전체 반영됨.
val ColorPrimary = Color(0xFF2C2C2C)
val ColorOnSurface = Color(0xFF2C2C2C)
val ColorOnSurfaceVariant = Color(0xFF666A71)
val ColorBackground = Color(0xFFFFFFFF)
val ColorSurface = Color(0xFFF7F4EE)
val ColorOutline = Color(0xFF9C9E9F)
val ColorOutlineVariant = Color(0xFFE5E0D6)

// 색 · 보조(강조) - 예전엔 연두색이었는데 이제 주황
val ColorSecondary = Color(0xFFFF7A1A)
// ⚠️ 이 값은 스크린샷에서 별도로 뽑은 "옅은 주황"이 없어서 secondary를 흰색 쪽으로
// 옅게 섞어 만든 추정값. 정확한 값이 필요하면 팀에 확인 요청할 것.
val ColorSecondaryContainer = Color(0xFFFFE8D6)

// 색 · 댐 재료(나무) - 이번 캡처엔 댐 화면 재료색이 뚜렷하게 안 보여서 기존값 유지(추정)
val ColorWood = Color(0xFFBB8A52)
val ColorWoodContainer = Color(0xFFF4ECE3)

// 색 · 보상 - 이번 캡처에서 확인 안 됨, 기존값 유지(추정)
val ColorReward = Color(0xFFFFBA00)
val ColorRewardContainer = Color(0xFFFFF4D6)

// 색 · 오류
val ColorError = Color(0xFFC92A2A)
val ColorErrorContainer = Color(0xFFFDECEC)

// 색 · 비활성 - 이번 캡처에서 확인 안 됨, 기존 톤 유지(추정)
val ColorDisabledContainer = Color(0xFFE5E0D6)
val ColorOnDisabled = Color(0xFF9C9E9F)

// 버튼
val ButtonPrimaryContainer = ColorPrimary
val ButtonPrimaryLabel = Color(0xFFFFFFFF)
val ButtonTonalContainer = ColorSecondaryContainer
val ButtonTonalLabel = ColorPrimary
val ButtonOutlinedOutline = ColorOutline
val ButtonOutlinedLabel = ColorPrimary
val ButtonTextLabel = ColorPrimary
val ButtonDangerOutline = ColorError
val ButtonDangerLabel = ColorError

// 하단 내비게이션
val NavigationContainer = ColorBackground
val NavigationIndicator = ColorOutlineVariant
val NavigationIconActive = ColorPrimary
val NavigationIconInactive = ColorOnSurfaceVariant
val NavigationLabelActive = ColorPrimary
val NavigationLabelInactive = ColorOnSurfaceVariant
