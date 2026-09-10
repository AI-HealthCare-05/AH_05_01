package com.tmtn.app.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.network.model.TuntunScorePeerV2Response
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.theme.*

/** Legacy scores drive an explicitly labelled layout preview until the percentile contract is connected. */
@Composable
internal fun ScoreBeaverStory(score: TuntunScorePeerV2Response, positions: List<ScorePeerPositionUi> = emptyList(), initialArea: String? = null) {
    val colors = LocalTmtnColors.current
    val tabs = (score.components.map { it.componentKey to it.label } + positions.map { it.key to it.label }).distinctBy { it.first }
    var selectedKey by rememberSaveable { mutableStateOf(initialArea ?: tabs.firstOrNull()?.first) }
    val currentKey = selectedKey.takeIf { key -> tabs.any { it.first == key } } ?: tabs.firstOrNull()?.first
    val component = score.components.firstOrNull { it.componentKey == currentKey }
    val position = positions.firstOrNull { it.key == currentKey }
    val large = LocalTmtnTextScale.current * androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.35f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("비버 마을 소식", style = TmtnType.body, color = colors.onSurfaceVariant)
        Text(if (large) "100명 속\n내 자리는?" else "나와 비슷한 비버\n100명이 모이면?",
            style = if (large) TmtnType.title else TmtnType.headline, color = colors.onSurface)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            tabs.forEach { (key, label) ->
                val selected = currentKey == key
                val interaction = remember { MutableInteractionSource() }
                Column(Modifier.tmtnPressFeedback(interaction).selectable(
                    selected, role = Role.Tab, interactionSource = interaction, indication = ripple(),
                    onClick = { selectedKey = key },
                ).heightIn(min = 52.dp).padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(label, style = TmtnType.bodyLarge, color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(24.dp).height(3.dp).background(if (selected) colors.secondary else colors.surface))
                }
            }
        }
        if (position != null) {
            // A verified future peer response can replace the waiting state without another layout.
            ScorePeerPositions(listOf(position), showHeading = false)
        } else {
            val percentile = ScorePercentilePresentation.fromCurrent(component?.peerPercentile, component?.hasResult == true)
            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${component?.label ?: "내"} 영역의 위치", style = TmtnType.body, color = colors.onSurfaceVariant)
                if (percentile != null) {
                    PercentileReading(percentile, withMascot = false)
                    PercentilePositionTrack(percentile)
                } else {
                    Text("아직 비교할 결과가 없어요", style = TmtnType.title, color = colors.onSurface)
                    if (!large) TmtnMascot(R.drawable.beaver_waiting, null, Modifier.size(104.dp), reactToTap = true)
                }
            }
        }
        if (component != null && !component.hasResult) Text("계산할 정보가 부족해요. 입력 정보를 확인해 주세요.",
            style = TmtnType.body, color = colors.onSurfaceVariant)
        Text("건강 순위나 질병에 걸릴 확률을 뜻하지 않아요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}
