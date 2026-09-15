package com.tmtn.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.*

enum class TmtnActionStyle { Primary, Outlined, Tonal, Text }

/** One state/interaction contract; callers own requests, validation and navigation. */
@Composable
fun TmtnActionButton(
    text: String,
    onClick: () -> Unit,
    style: TmtnActionStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = LocalTmtnColors.current
    val interactions = remember { MutableInteractionSource() }
    val actionable = enabled && !loading
    val container = when (style) {
        TmtnActionStyle.Primary -> colors.primary
        TmtnActionStyle.Tonal -> colors.surface
        else -> Color.Transparent
    }
    val foreground = if (style == TmtnActionStyle.Primary) Color.White else colors.onSurface
    val minimum = if (AccessibilitySettingsHolder.largeControlsEnabled.value) TmtnLayout.LargeControlMin
        else if (style == TmtnActionStyle.Text) TmtnLayout.TouchTarget else TmtnLayout.ControlMin
    Button(
        onClick = { com.tmtn.app.audio.TmtnAudio.play(com.tmtn.app.audio.TmtnSound.Tap); onClick() }, enabled = actionable, interactionSource = interactions,
        modifier = modifier.fillMaxWidth().heightIn(min = minimum)
            .tmtnPressFeedback(interactions, actionable)
            .tmtnFocusOutline(interactions, TmtnLayout.ControlShape, actionable)
            .semantics { if (loading) stateDescription = "처리 중" },
        shape = TmtnLayout.ControlShape,
        border = if (style == TmtnActionStyle.Outlined) BorderStroke(TmtnLayout.Hairline, colors.outlineVariant) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = foreground,
            disabledContainerColor = if (loading) container else when (style) {
                TmtnActionStyle.Primary, TmtnActionStyle.Tonal -> colors.disabledContainer
                else -> Color.Transparent
            },
            disabledContentColor = if (loading) foreground else colors.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = if (style == TmtnActionStyle.Text) 10.dp else TmtnLayout.ControlPaddingVertical,
        ),
    ) {
        if (loading) {
            if (rememberTmtnReducedMotion()) {
                // A static mark plus the accessible state retains feedback without rotation.
                CircularProgressIndicator(progress = { .75f }, modifier = Modifier.size(20.dp).clearAndSetSemantics {},
                    color = foreground, strokeWidth = 2.dp)
            } else CircularProgressIndicator(Modifier.size(20.dp).clearAndSetSemantics {}, color = foreground, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = TmtnType.actionLabel, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
    }
}
