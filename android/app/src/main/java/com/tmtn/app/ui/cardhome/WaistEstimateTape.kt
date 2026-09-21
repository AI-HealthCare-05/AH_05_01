package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.*
import kotlin.math.ceil
import kotlin.math.floor

/** 표시값 ±5cm를 펼친 읽기 전용 줄자. 장식 눈금이나 드래그 입력기가 아니다. */
@Composable
internal fun WaistEstimateTape(displayValue: String) {
    val value = displayValue.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return
    val measurer = rememberTextMeasurer()
    val style = TmtnType.caption.copy(color = ColorOnSurface)
    val labelHeight = with(LocalDensity.current) { measurer.measure("100", style).size.height.toDp() }
    Canvas(Modifier.fillMaxWidth().height(52.dp + labelHeight)
        .clip(RoundedCornerShape(12.dp)).background(TmtnHomeColor.Ruler)
        .testTag("waist-estimate-tape").semantics {
            contentDescription = "줄자 눈금, 추정 허리둘레 $displayValue 센티미터 위치"
        }) {
        val perCm = size.width / 10f
        val bottom = size.height - 4.dp.toPx()
        val ink = ColorOnSurfaceVariant
        val first = floor((value - 5) * 10).toInt().coerceAtLeast(0)
        val last = ceil((value + 5) * 10).toInt()
        val labelWidth = measurer.measure("100", style).size.width + 12.dp.toPx()
        val labelInterval = if (labelWidth <= perCm * 2) 20 else 40
        for (tick in first..last) {
            val x = center.x + (tick / 10f - value) * perCm
            val major = tick % 10 == 0
            val half = tick % 5 == 0
            val length = (if (major) 28 else if (half) 18 else 11).dp.toPx()
            drawLine(ink.copy(alpha = if (major) .95f else .55f), Offset(x, bottom - length),
                Offset(x, bottom), if (major) 1.dp.toPx() else .6.dp.toPx())
            if (tick % labelInterval == 0) {
                val label = measurer.measure((tick / 10).toString(), style)
                val left = x - label.size.width / 2f
                if (left >= 6.dp.toPx() && left + label.size.width <= size.width - 6.dp.toPx())
                    drawText(label, topLeft = Offset(left, 12.dp.toPx()))
            }
        }
        drawLine(ink.copy(alpha = .35f), Offset(0f, bottom), Offset(size.width, bottom), .6.dp.toPx())
        val pointer = Path().apply {
            moveTo(center.x - 7.dp.toPx(), 2.dp.toPx())
            lineTo(center.x + 7.dp.toPx(), 2.dp.toPx())
            lineTo(center.x, 10.dp.toPx())
            close()
        }
        drawPath(pointer, TmtnHomeColor.RulerMarker)
        drawLine(TmtnHomeColor.RulerMarker, Offset(center.x, bottom - 34.dp.toPx()),
            Offset(center.x, bottom), 2.dp.toPx())
    }
}
