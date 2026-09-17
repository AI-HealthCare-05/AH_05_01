package com.tmtn.app.ui.theme

import androidx.compose.ui.graphics.Color

// 2026-09-17 승인된 B안: 기존 숲색을 주요 행동에 사용하고 넓은 면은 흰색·무채색으로 유지한다.
val ColorBrandForest = Color(0xFF3F5D4B)
val ColorPrimary = ColorBrandForest
val ColorOnForest = Color(0xFFFFFFFF)
val ColorOnForestSecondary = Color(0xFFE8E8E8)
val ColorOnSurface = Color(0xFF2C2C2C)
val ColorOnSurfaceVariant = Color(0xFF666A71)
val ColorBackground = Color(0xFFFFFFFF)
val ColorSurface = Color(0xFFF5F5F5)
val ColorArtworkPaper = ColorBackground
val ColorOutline = Color(0xFF858585)
val ColorOutlineVariant = Color(0xFFD9D9D9)

// 주황은 오늘 표시처럼 의미가 있는 작은 표시에만 사용한다. 연한 유채색 면을 만들지 않는다.
val ColorSecondary = Color(0xFFFF7A1A)
val ColorSecondaryContainer = ColorSurface
val ColorWood = Color(0xFFBB8A52)
val ColorWoodContainer = ColorSurface
val ColorReward = Color(0xFFFFBA00)
val ColorRewardContainer = ColorSurface
val ColorError = Color(0xFFC92A2A)
val ColorErrorContainer = ColorSurface
val ColorDisabledContainer = Color(0xFFE8E8E8)
val ColorOnDisabled = Color(0xFF777777)

val ColorCharcoal = Color(0xFF16181C)
// 선택지는 흰 바탕·숲색 외곽선·체크를 함께 사용한다.
val ColorSelectedSurface = ColorBackground
val ColorSelectionSurface = ColorSelectedSurface

val ButtonPrimaryContainer = ColorPrimary
val ButtonPrimaryLabel = ColorOnForest
val ButtonTonalContainer = ColorSurface
val ButtonTonalLabel = ColorPrimary
val ButtonOutlinedOutline = ColorOutline
val ButtonOutlinedLabel = ColorPrimary
val ButtonTextLabel = ColorPrimary
val ButtonDangerOutline = ColorError
val ButtonDangerLabel = ColorError

val NavigationContainer = ColorBackground
val NavigationIndicator = ColorBrandForest
val NavigationIconActive = ColorOnForest
val NavigationIconInactive = ColorOnSurfaceVariant
val NavigationLabelActive = ColorBrandForest
val NavigationLabelInactive = ColorOnSurfaceVariant
