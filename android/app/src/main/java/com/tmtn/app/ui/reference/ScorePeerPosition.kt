package com.tmtn.app.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.theme.*

/**
 * Optional presentation data for a future peer-reference response, not an API/model contract.
 * positionFromHigherIndex must be supplied from a verified reference distribution (1..100).
 * Never construct it from tuntunIndex, disease probability, or an unspecified percentage.
 * Empty input means no peer section; the shipped API currently has no peer reference.
 */
data class ScorePeerPositionUi(
    val key: String,
    val label: String,
    val positionFromHigherIndex: Int,
    val groupLabel: String,
    val referenceLabel: String,
) {
    init {
        require(positionFromHigherIndex in 1..100)
        require(key.isNotBlank() && label.isNotBlank() && groupLabel.isNotBlank() && referenceLabel.isNotBlank())
    }
}

@Composable
internal fun ScorePeerPositions(positions: List<ScorePeerPositionUi>, showHeading: Boolean = true) {
    if (positions.isEmpty()) return
    val colors = LocalTmtnColors.current
    var selectedKey by rememberSaveable { mutableStateOf(positions.first().key) }
    val current = positions.firstOrNull { it.key == selectedKey } ?: positions.first()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showHeading) Text("또래 속 내 위치", style = TmtnType.title, color = colors.onSurface)
        if (positions.size > 1) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            positions.forEach { item ->
                val selected = item.key == current.key
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier.tmtnPressFeedback(interaction).selectable(selected, role = Role.Tab,
                        interactionSource = interaction, indication = ripple(), onClick = { selectedKey = item.key })
                        .heightIn(min = 48.dp).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(item.label, style = TmtnType.label, color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(24.dp).height(3.dp).background(if (selected) colors.secondary else colors.surface))
                }
            }
        }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(current.groupLabel + " 100명 중", style = TmtnType.body, color = colors.onSurfaceVariant)
            Text(current.positionFromHigherIndex.toString() + "번째쯤", style = TmtnType.display, color = colors.secondary)
            Text(current.label + " 지수가 높은 쪽부터 본 참고 위치예요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        // The number is immediate and stationary. The illustration marks the same position.
        BoxWithConstraints(Modifier.fillMaxWidth().height(88.dp).clearAndSetSemantics {}) {
            val reduced = rememberTmtnReducedMotion()
            val target = with(LocalDensity.current) {
                ((maxWidth - 64.dp) * ((current.positionFromHigherIndex - 1) / 99f)).toPx()
            }
            val x by animateFloatAsState(target, tween(if (reduced) 0 else 180, easing = TmtnMotion.EaseOut),
                label = "peer illustration position")
            Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(2.dp).align(Alignment.BottomCenter).background(colors.outline))
            TmtnMascot(R.drawable.beaver_standing, null,
                Modifier.width(64.dp).height(80.dp).graphicsLayer { translationX = x }, greet = false)
            Box(Modifier.padding(start = 30.dp).width(4.dp).height(12.dp)
                .align(Alignment.BottomStart).graphicsLayer { translationX = x }.background(colors.secondary))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1번째", style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text("100번째", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Text(current.referenceLabel, style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}
