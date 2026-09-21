package com.tmtn.app.ui.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** 누락·잘못된 날짜·범위 밖 점수는 그래프에 그리지 않는다. */
internal fun verifiedWeeklyPoints(response: WeeklyXaiHistoryResponse?): List<WeeklyReferencePoint> {
    if (response?.schema_version != "tmtn-weekly-xai-v1" || response.status != "ready") return emptyList()
    val points = response.points.orEmpty()
    if (points.size > 8 || points.map { it.week_start }.distinct().size != points.size) return emptyList()
    return points.map { point ->
        val start = runCatching { LocalDate.parse(point.week_start) }.getOrNull() ?: return emptyList()
        if (start.dayOfWeek != DayOfWeek.MONDAY || point.week_end != start.plusDays(6).toString()) return emptyList()
        val observed = runCatching { LocalDate.parse(point.observed_on) }.getOrNull()
        val valid = point.status == "recorded" && !point.comparison_key.isNullOrBlank() && observed != null &&
            observed >= start && observed <= start.plusDays(6) &&
            listOf(point.diabetes, point.hypertension).all { it != null && it.isFinite() && it in 0.0..100.0 }
        if (valid) point else point.copy(status = if (point.status == "missing") "missing" else "incompatible",
            diabetes = null, hypertension = null, comparison_key = null)
    }.sortedBy { it.week_start }
}

internal fun comparableWeeks(previous: WeeklyReferencePoint, current: WeeklyReferencePoint): Boolean =
    previous.status == "recorded" && current.status == "recorded" && !current.comparison_key.isNullOrBlank() &&
        previous.comparison_key == current.comparison_key && runCatching {
            LocalDate.parse(previous.week_start).plusWeeks(1).toString() == current.week_start
        }.getOrDefault(false)

private fun pointNumber(value: Double?): String = value?.let { String.format(Locale.KOREAN, "%.1f", it) } ?: "—"
private fun weekLabel(value: String?): String = runCatching {
    LocalDate.parse(value).let { "${it.monthValue}/${it.dayOfMonth}" }
}.getOrDefault("—")

@Composable
internal fun JournalWeeklyHistory(history: JournalLoad<WeeklyXaiHistoryResponse>?, onRetry: () -> Unit) {
    if (history == null) return
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(20.dp))
        .padding(20.dp).testTag("journal-weekly-history"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("주마다 쌓이는 내 기록", style = TmtnType.title, color = colors.onSurface,
            modifier = Modifier.semantics { heading() })
        when (history) {
            JournalLoad.Loading -> Text("지난 기록을 불러오고 있어요.", style = TmtnType.body)
            JournalLoad.Failed -> {
                Text("주간 기록을 불러오지 못했어요.", style = TmtnType.body)
                TextButton(onClick = onRetry) { Text("주간 기록 다시 불러오기", style = TmtnType.label) }
            }
            is JournalLoad.Ready -> {
                val response = history.value
                if (response.status != "ready") {
                    Text(if (response.reason == "consent_required") "분석 동의 후 참고점수 기록을 볼 수 있어요."
                        else "참고점수 기록을 준비하고 있어요.", style = TmtnType.body)
                } else {
                    WeeklyPracticeComparison(response.practice.orEmpty())
                    val points = verifiedWeeklyPoints(response)
                    if (points.isEmpty()) Text("아직 주간 참고점수 기록이 없어요.", style = TmtnType.body)
                    else WeeklyReferenceChart(points)
                }
            }
        }
    }
}

@Composable
private fun WeeklyPracticeComparison(practice: List<WeeklyPracticePoint>) {
    if (practice.size != 2) return
    val colors = LocalTmtnColors.current
    Text("지난주와 이번 주의 실천", style = TmtnType.label, color = colors.primary)
    practice.forEachIndexed { index, week ->
        val count = week.completed_days?.takeIf { it in 0..7 }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${if (index == 0) "지난주" else "이번 주"} · ${weekLabel(week.week_start)}", style = TmtnType.body)
            Text(if (week.recorded == true && count != null) "${count}일 실천" else "기록 없음", style = TmtnType.label)
        }
        LinearProgressIndicator(progress = { if (week.recorded == true) (count ?: 0) / 7f else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp), color = if (index == 0) TmtnFeatureColor.High else colors.primary,
            trackColor = colors.outlineVariant)
    }
    val elapsed = practice.last().elapsed_days
    if (elapsed != null && elapsed < 7) Text("이번 주는 ${elapsed}일째예요. 지난주는 7일 전체 기록이에요.",
        style = TmtnType.caption, color = colors.onSurfaceVariant)
    HorizontalDivider(color = colors.outlineVariant)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun WeeklyReferenceChart(points: List<WeeklyReferencePoint>) {
    val colors = LocalTmtnColors.current
    var selectedWeek by rememberSaveable(points.last().week_start) { mutableStateOf(points.last().week_start) }
    val selected = points.firstOrNull { it.week_start == selectedWeek } ?: points.last()
    val previous = points.getOrNull(points.indexOf(selected) - 1)
    val comparable = previous != null && comparableWeeks(previous, selected)
    Text("참고점수 · 최근 8주", style = TmtnType.label, color = colors.primary)
    Text("각 주에 마지막으로 계산한 값이에요. 날짜를 눌러 그 주의 기록을 보세요.",
        style = TmtnType.caption, color = colors.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("● 당뇨 참고", style = TmtnType.caption, color = colors.primary)
        Text("■ 고혈압 참고", style = TmtnType.caption, color = TmtnFeatureColor.High)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("100점", style = TmtnType.caption, color = colors.onSurfaceVariant)
        Text("0–100점 기준", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
    val description = points.joinToString(". ") { "${weekLabel(it.week_start)} 주, " +
        if (it.status == "recorded") "당뇨 참고 ${pointNumber(it.diabetes)}점, 고혈압 참고 ${pointNumber(it.hypertension)}점"
        else "기록 없음" }
    Canvas(Modifier.fillMaxWidth().height(148.dp).testTag("weekly-score-chart").semantics { contentDescription = description }) {
        val inset = 8.dp.toPx()
        val span = size.width - inset * 2
        fun location(index: Int, value: Double) = Offset(inset + span * index / (points.size - 1).coerceAtLeast(1),
            inset + (size.height - 2 * inset) * (1 - value.toFloat() / 100))
        for (value in listOf(0.0, 50.0, 100.0)) drawLine(colors.outlineVariant, location(0, value),
            location(points.lastIndex, value), 1.dp.toPx())
        for (domain in 0..1) {
            val color = if (domain == 0) colors.primary else TmtnFeatureColor.High
            points.forEachIndexed { index, point ->
                val value = if (domain == 0) point.diabetes else point.hypertension
                if (value != null) {
                    val current = location(index, value)
                    val before = points.getOrNull(index - 1)
                    val beforeValue = if (domain == 0) before?.diabetes else before?.hypertension
                    if (before != null && beforeValue != null && comparableWeeks(before, point)) {
                        drawLine(color, location(index - 1, beforeValue), current, 3.dp.toPx(), StrokeCap.Round,
                            pathEffect = if (domain == 1) PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 5.dp.toPx())) else null)
                    }
                    if (domain == 0) drawCircle(color, 4.dp.toPx(), current)
                    else drawRect(color, current - Offset(4.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()))
                }
            }
        }
    }
    Text("0점", style = TmtnType.caption, color = colors.onSurfaceVariant)
    Row(Modifier.fillMaxWidth()) {
        points.forEachIndexed { index, point ->
            Text(if (index % 2 == 1 || index == points.lastIndex) weekLabel(point.week_start) else "",
                style = TmtnType.caption, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        points.forEach { point ->
            FilterChip(selected = point.week_start == selected.week_start, onClick = { selectedWeek = point.week_start },
                label = { Text(weekLabel(point.week_start), style = TmtnType.caption) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary,
                    selectedLabelColor = colors.background),
                modifier = Modifier.heightIn(min = 48.dp).testTag("week-${point.week_start}"))
        }
    }
    Text("${weekLabel(selected.week_start)} — ${weekLabel(selected.week_end)}", style = TmtnType.label)
    if (selected.status == "recorded") {
        Text("당뇨 참고 ${pointNumber(selected.diabetes)}점 · 고혈압 참고 ${pointNumber(selected.hypertension)}점",
            style = TmtnType.body)
        Text("${weekLabel(selected.observed_on)} 입력 기준", style = TmtnType.caption, color = colors.onSurfaceVariant)
        if (comparable) {
            val diabetes = selected.diabetes!! - previous!!.diabetes!!
            val hypertension = selected.hypertension!! - previous.hypertension!!
            fun delta(value: Double) = String.format(Locale.KOREAN, "%+.1f", value)
            Text("지난주 대비 · 당뇨 ${delta(diabetes)}점 / 고혈압 ${delta(hypertension)}점", style = TmtnType.label,
                modifier = Modifier.testTag("weekly-score-delta"))
        } else Text("지난주에 같은 계산 기준으로 저장된 값이 있어야 차이를 볼 수 있어요.", style = TmtnType.caption)
    } else Text(if (selected.status == "incompatible") "계산 기준을 확인할 수 없어 이 주의 비교를 표시하지 않아요."
        else "이 주에는 저장된 참고점수가 없어요.", style = TmtnType.body)
    Text("비진단용 참고 정보입니다. 점수 차이는 건강 개선이나 운동 효과를 뜻하지 않아요. 기록이 없는 주는 선으로 잇지 않아요.",
        style = TmtnType.caption, color = colors.onSurfaceVariant)
}
