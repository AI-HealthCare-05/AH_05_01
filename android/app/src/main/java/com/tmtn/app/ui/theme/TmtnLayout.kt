package com.tmtn.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Figma-led control dimensions. Legacy screen-only values are identified in /DESIGN.md. */
object TmtnLayout {
    val ScreenInset = 20.dp
    val ControlMin = 52.dp
    val LargeControlMin = 60.dp
    val TouchTarget = 48.dp
    val TopBarMin = 64.dp
    val ControlPaddingVertical = 14.dp
    val FieldShape = RoundedCornerShape(12.dp)
    val ControlShape = RoundedCornerShape(14.dp)
    val PanelShape = RoundedCornerShape(16.dp)
    val ExerciseShape = RoundedCornerShape(20.dp)
    // Earlier Android home, not the latest Figma TodayCard (R24).
    val SummaryShape = RoundedCornerShape(18.dp)
    val Hairline = 1.dp
    // New starting value for keyboard / switch focus, not a selection indicator.
    val FocusWidth = 2.dp
}
