package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.*

/** 오늘 카드 다음에 이어지는 보조 운동. 작은 비버를 제목 안에 묶고 상태와 설명을 유지한다. */
@Composable
internal fun ExtraExerciseCard(completed: Boolean, used: Int?, limit: Int?, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val large = LocalTmtnTextScale.current * LocalDensity.current.fontScale > 1.25f
    Column(
        Modifier.fillMaxWidth().testTag("extra-exercise-entry")
            .clip(shape).background(ColorBrandForest)
            .border(TmtnLayout.Hairline, ColorBrandForest, shape)
            .then(if (completed) Modifier.tmtnClickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TmtnMascot(R.drawable.beaver_wave, null, Modifier.size(if (large) 36.dp else 48.dp), greet = false)
            Text("틈새 운동", style = TmtnType.missionName, color = ColorOnForest, modifier = Modifier.weight(1f).semantics { heading() })
            if (!completed) Icon(Icons.Outlined.Lock, "오늘 카드 완료 후 이용 가능", Modifier.size(18.dp), tint = ColorOnForestSecondary)
            else if (used != null && limit != null) Text("$used / ${limit}회", style = TmtnType.caption, color = ColorOnForest)
        }
        Text(
            when {
                !completed -> "오늘의 카드를 마치면 열려요."
                used != null && limit != null && used >= limit -> "오늘 받을 수 있는 추가 재료를 모두 모았어요."
                else -> "짧은 운동으로 재료를 하나 더 모아요."
            },
            style = TmtnType.caption, color = ColorOnForestSecondary,
        )
        if (completed) Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("틈새 운동 둘러보기", style = TmtnType.label, color = ColorOnForest, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = ColorOnForest)
        }
    }
}

/** 줄자 비버와 두 단위가 함께 보이는 홈 요약. 장식 눈금에는 수치나 건강 구간을 넣지 않는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeWaistSummary(result: WaistEstimateUi.Available, onToggle: () -> Unit) {
    val colors = LocalTmtnColors.current
    val large = LocalTmtnTextScale.current * LocalDensity.current.fontScale > 1.25f
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(ColorArtworkPaper)
        .border(TmtnLayout.Hairline, colors.outlineVariant, shape)
        .padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("허리둘레", style = TmtnType.missionName, color = colors.onSurface, modifier = Modifier.semantics { heading() })
                    Text("추정값", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
                if (!large) HomeWaistValues(result)
            }
            TmtnMascot(R.drawable.beaver_waist_white, null, Modifier.size(if (large) 72.dp else 116.dp), greet = false)
        }
        if (large) HomeWaistValues(result)
        Canvas(Modifier.fillMaxWidth().height(10.dp).clearAndSetSemantics {}) {
            val ticks = 24
            for (i in 0..ticks) {
                val x = size.width * i / ticks
                drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, if (i % 4 == 0) size.height else size.height * .45f), 1.dp.toPx())
            }
        }
        Text("입력한 신체 정보로 추정한 값이에요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).tmtnClickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("허리둘레 자세히 보기", style = TmtnType.label, color = ColorBrandForest, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = ColorBrandForest)
        }
    }
}

@Composable
private fun HomeWaistValues(result: WaistEstimateUi.Available) {
    val colors = LocalTmtnColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("약 " + result.displayValue, style = TmtnType.title, color = colors.onSurface)
            Text("cm", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Text("약 ${result.displayInches} 인치", style = TmtnType.bodyLarge, color = colors.onSurfaceVariant)
    }
}
