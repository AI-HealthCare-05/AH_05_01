package com.tmtn.app.ui.reference

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.network.model.PeerComponent
import com.tmtn.app.network.model.TuntunScorePeerV2Response
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.theme.*
import java.time.LocalDate
import kotlin.math.roundToInt

// Preserve the existing UI's minimum coverage rule on every page, including details.
internal val TuntunScorePeerV2Response.canShowOverall: Boolean
    get() = scoreAvailable && availableComponentCount >= 2 && peerCompositeScore?.isFinite() == true
internal val TuntunScorePeerV2Response.coverageLabel: String
    get() = if (isPartialScore) "4개 중 $availableComponentCount" + "개 영역 반영"
    else "$availableComponentCount" + "개 영역 반영"
internal val PeerComponent.hasResult: Boolean
    get() = available && peerPercentile?.isFinite() == true

// ⚠️ 2026-09-10 추가 - 새 계약엔 영역별 안내 문구(guidance)가 없어서 클라이언트에
// 정적으로 유지함(componentKey 기준).
internal val COMPONENT_GUIDANCE = mapOf(
    "physical" to "허리둘레 위험을 낮추는 방향으로 체중과 활동 습관을 꾸준히 관리해 보세요.",
    "diabetes" to "규칙적인 활동과 균형 잡힌 식사를 이어가며 생활습관을 관리해 보세요.",
    "hypertension" to "걷기 등 꾸준한 활동과 나트륨 섭취 관리를 실천해 보세요.",
    "lifestyle" to "유산소 운동 시간과 주간 근력운동 일수를 조금씩 늘려 보세요.",
)

internal fun scoreBandLabel(value: Double): String = when {
    value.roundToInt() < 40 -> "관심"
    value.roundToInt() < 70 -> "보통"
    else -> "양호"
}

internal fun scorePeriod(start: String, end: String): String {
    val first = runCatching { LocalDate.parse(start) }.getOrNull()
    val last = runCatching { LocalDate.parse(end) }.getOrNull()
    if (first == null || last == null) return "$start ~ $end"
    val ending = if (first.year == last.year) "" else last.year.toString() + ". "
    return first.year.toString() + ". " + first.monthValue + ". " + first.dayOfMonth +
        ". ~ " + ending + last.monthValue + ". " + last.dayOfMonth + "."
}

@Composable
internal fun ScoreExampleLabel() {
    val colors = LocalTmtnColors.current
    Text(
        "예시", style = TmtnType.caption, color = colors.onSurfaceVariant,
        modifier = Modifier.background(colors.surface, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun ScoreRule(strong: Boolean = false) {
    val colors = LocalTmtnColors.current
    Box(Modifier.fillMaxWidth().height(if (strong) 2.dp else 1.dp)
        .background(if (strong) colors.onSurface else colors.outlineVariant))
}

@Composable
internal fun ScorePaperMasthead(compact: Boolean = false) {
    val colors = LocalTmtnColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScoreRule(strong = true)
        if (LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.35f) {
            Text("틈튼일보", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("틈튼일보", style = if (compact) TmtnType.title else TmtnType.headline,
                fontWeight = FontWeight.Bold, color = colors.onSurface, modifier = Modifier.weight(1f).semantics { heading() })
            if (!compact) Text("TMTN", style = TmtnType.label, color = colors.onSurfaceVariant)
        }
        ScoreRule()
    }
}

@Composable
internal fun ScoreHero(score: TuntunScorePeerV2Response, withMascot: Boolean, mascotImage: Int = R.drawable.beaver_card) {
    val result = ScorePercentilePresentation.fromCurrent(score.peerCompositeScore, score.canShowOverall)
    if (result != null) PercentileReading(result, withMascot, mascotImage)
    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("정보를 더 입력해 주세요", style = TmtnType.title, color = LocalTmtnColors.current.onSurface)
        Text("종합 지수는 2개 이상 영역이 있어야 표시해요.", style = TmtnType.body,
            color = LocalTmtnColors.current.onSurfaceVariant)
    }
}

@Composable
internal fun ScoreGaugeBar(value: Double) {
    ScorePercentilePresentation.fromCurrent(value)?.let { PercentilePositionTrack(it, showMascot = false) }
}

@Composable
internal fun ScoreArrow(accent: Boolean = false) {
    val colors = LocalTmtnColors.current
    Icon(
        if (accent) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.KeyboardArrowRight,
        null, tint = colors.onSurface,
        modifier = if (accent) Modifier.size(36.dp).background(colors.secondary, CircleShape).padding(8.dp)
        else Modifier.size(20.dp),
    )
}

@Composable
internal fun ScoreActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).tmtnClickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = TmtnType.bodyLarge, color = colors.onSurface)
            if (subtitle != null) Text(subtitle, style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        ScoreArrow()
    }
}

@Composable
internal fun ScoreSummaryRow(component: PeerComponent, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    val result = ScorePercentilePresentation.fromCurrent(component.peerPercentile, component.hasResult)
    if (LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.35f) {
        ScoreActionRow(component.label, result?.primaryLabel ?: "정보 부족", onClick)
        return
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).tmtnClickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(component.label, style = TmtnType.body, color = colors.onSurface, modifier = Modifier.weight(1f))
        Text(
            result?.primaryLabel ?: "정보 부족",
            style = TmtnType.bodyLarge, color = colors.onSurface,
        )
        ScoreArrow()
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    val colors = LocalTmtnColors.current
    if (LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.35f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text(value, style = TmtnType.body, color = colors.onSurface)
        }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = TmtnType.caption, color = colors.onSurfaceVariant, modifier = Modifier.weight(.8f))
        Text(value, style = TmtnType.body, color = colors.onSurface,
            textAlign = TextAlign.End, modifier = Modifier.weight(1.5f))
    }
}
