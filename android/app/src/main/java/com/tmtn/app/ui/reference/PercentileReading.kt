package com.tmtn.app.ui.reference

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.theme.*

@Composable
internal fun PercentileReading(result: ScorePercentileUi, withMascot: Boolean = true,
    mascotImage: Int = R.drawable.beaver_standing, compact: Boolean = false) {
    val colors = LocalTmtnColors.current
    val large = LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.35f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val numbers: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clearAndSetSemantics {
                contentDescription = result.reading
            }) {
                if (!result.isPreview) Text("비버 100명 중", style = TmtnType.body, color = colors.onSurfaceVariant)
                Text(result.primaryLabel, style = if (compact || large) TmtnType.headline else TmtnType.display,
                    color = colors.secondary)
            }
        }
        if (withMascot && !large) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { numbers() }
            TmtnMascot(mascotImage, null, Modifier.size(if (compact) 88.dp else 112.dp), reactToTap = true)
        } else if (compact && !large) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { numbers() }
        } else numbers()
        if (!compact) Text(result.disclosure, style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

/** A rank track has a single scale. The number changes immediately; only the beaver moves. */
@Composable
internal fun PercentilePositionTrack(result: ScorePercentileUi, showMascot: Boolean = true) {
    if (result.isPreview) return
    val colors = LocalTmtnColors.current
    val reduced = rememberTmtnReducedMotion()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clearAndSetSemantics {}) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(if (showMascot) 76.dp else 16.dp)) {
            val target = with(LocalDensity.current) {
                ((maxWidth - 56.dp).coerceAtLeast(0.dp) * ((result.position - 1) / 99f)).toPx()
            }
            val x by animateFloatAsState(target, tween(if (reduced) 0 else 180, easing = TmtnMotion.EaseOut),
                label = "percentile beaver position")
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(2.dp)
                .align(Alignment.BottomCenter).background(colors.outlineVariant))
            if (showMascot) TmtnMascot(R.drawable.beaver_standing, null,
                Modifier.size(56.dp).align(Alignment.BottomStart).graphicsLayer { translationX = x }, greet = false)
            Box(Modifier.padding(start = 26.dp).width(4.dp).height(10.dp).align(Alignment.BottomStart)
                .graphicsLayer { translationX = x }.background(colors.secondary))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1번째", style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text("100번째", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
    }
}
