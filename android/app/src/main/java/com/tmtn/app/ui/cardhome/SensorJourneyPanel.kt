package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** 상태에 맞는 정지 이미지와 실제 센서 측정값을 표시한다. */
@Composable
internal fun SensorJourneyPanel(
    display: SensorDisplay,
    phase: SensorJourneyPhase,
    materialName: String,
    progressDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTmtnColors.current
    val progress = display.progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    Column(
        modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(24.dp))
            .border(2.dp, colors.outlineVariant, RoundedCornerShape(24.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            phase.label, style = TmtnType.label, color = colors.onSurface,
            modifier = Modifier.testTag("sensor-journey-status")
                .semantics { liveRegion = LiveRegionMode.Polite }
                .background(colors.background, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        SensorCheeringCompanion(phase)
        Text(sensorJourneyMessage(phase, progress), style = TmtnType.label,
            color = colors.onSurface, textAlign = TextAlign.Center)
        Text(display.value, style = TmtnType.display, color = colors.onSurface)
        Text(display.target, style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Canvas(Modifier.fillMaxWidth().height(22.dp).semantics {
            contentDescription = progressDescription
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        }) {
            val inset = 7.dp.toPx()
            val start = Offset(inset, center.y)
            val end = Offset(size.width - inset, center.y)
            drawLine(colors.outlineVariant, start, end, 5.dp.toPx(), StrokeCap.Round)
            drawLine(colors.primary, start, Offset(inset + (size.width - 2 * inset) * progress, center.y),
                5.dp.toPx(), StrokeCap.Round)
            (0..4).forEach { index ->
                val point = Offset(inset + (size.width - 2 * inset) * index / 4f, center.y)
                drawCircle(if (progress >= index / 4f) colors.primary else colors.outlineVariant, 5.dp.toPx(), point)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(progress * 100).toInt()}% 달성", style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text("목표 100%", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Text(if (phase == SensorJourneyPhase.COMPLETE) "완료를 누르면 $materialName 1개를 받아요"
            else "완료 보상 · $materialName 1개", style = TmtnType.label,
            color = colors.onSurface, textAlign = TextAlign.Center)
    }
}

/** 일시정지는 쉬는 모습, 목표 달성은 축하하는 모습으로 구분한다. 애니메이션은 없다. */
@Composable
private fun SensorCheeringCompanion(phase: SensorJourneyPhase) {
    val (image, description) = when (phase) {
        SensorJourneyPhase.PAUSED -> R.drawable.beaver_sensor_paused to "잠깐 쉬고 있는 틈튼 비버"
        SensorJourneyPhase.COMPLETE -> R.drawable.beaver_sensor_complete to "목표 달성을 축하하는 틈튼 비버"
        else -> R.drawable.beaver_sensor_cheer to "운동을 응원하는 틈튼 비버"
    }
    Box(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(image),
            contentDescription = description,
            modifier = Modifier.size(172.dp).testTag("sensor-companion"),
        )
    }
}
