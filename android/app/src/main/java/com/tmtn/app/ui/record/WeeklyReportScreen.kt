package com.tmtn.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.cardhome.MaterialIcon
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Figma E04 1314:6664: seven dates, a comparable period, materials, then the newspaper. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyReportScreen(state: RecordState, scope: CoroutineScope, onGoPickCard: () -> Unit, onOpenJournal: (() -> Unit)? = null) {
    val colors = LocalTmtnColors.current
    val report = state.weeklyReport.value
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("최근 7일", { state.tab.value = RecordTab.MONTHLY })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("나의 일주일을\n한눈에.", style = TmtnType.headline, color = colors.onSurface)
            when {
                state.weeklyLoadFailed.value -> {
                    Text("일주일 기록을 불러오지 못했어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                    TmtnTonalButton("다시 불러오기", { scope.launch { state.loadWeekly() } }, enabled = !state.isLoading.value)
                }
                report == null -> Text("실천한 날들을 모으고 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                else -> {
                    Text("${shortRecordDate(report.start_date)} – ${shortRecordDate(report.end_date)}", style = TmtnType.caption, color = colors.onSurfaceVariant)
                    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val dateScale = androidx.compose.ui.platform.LocalDensity.current.fontScale * LocalTmtnTextScale.current
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cellWidth = maxOf(maxWidth / 7, (34 * dateScale.coerceAtLeast(1f) + 8).dp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            report.days.take(7).forEach { day ->
                                val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
                                if (date != null) Column(Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN), style = TmtnType.caption, color = colors.onSurfaceVariant)
                                    DayCell(date.dayOfMonth, day.status, date == LocalDate.now(), date.isAfter(LocalDate.now())) {
                                        scope.launch { state.openDayDetail(day.date) }
                                    }
                                }
                            }
                        }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LegendDot(colors.onSurface, "실천")
                            LegendDot(colors.disabledContainer, "쉼", colors.onSurface)
                            LegendDot(androidx.compose.ui.graphics.Color.Transparent, "미완료", colors.outline, dashed = true)
                            LegendDot(androidx.compose.ui.graphics.Color.Transparent, "오늘", colors.secondary)
                        }
                        Text("실천 ${report.completed_count}일 · 쉼 ${report.days.count { it.status == "REST" }}일", style = TmtnType.label, color = colors.onSurface)
                    }
                    val previous = state.previousWeek.value
                    if (previous != null && report.days.size == 7 && report.days.none { it.status == "BEFORE_SIGNUP" }) {
                        val before = previous.count { it.status == "COMPLETED" }
                        val difference = report.completed_count - before
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(when {
                                difference > 0 -> "지난번보다 ${difference}일 더 실천했어요."
                                difference == 0 -> "이번에도 ${report.completed_count}일, 나의 속도로."
                                else -> "이번에 남긴 ${report.completed_count}일도 소중해요."
                            }, style = TmtnType.title, color = colors.onSurface)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                ComparisonBar("이전 7일", before, false, Modifier.weight(1f))
                                ComparisonBar("최근 7일", report.completed_count, true, Modifier.weight(1f))
                            }
                            Text("각 기간 7일 기준 · ${shortRecordDate(previous.first().date)}부터 / ${shortRecordDate(report.start_date)}부터", style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                    }
                    if (report.materials_this_week.isNotEmpty()) Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("받은 재료 ${report.materials_this_week.sumOf { it.count }}개", style = TmtnType.title, color = colors.onSurface)
                        report.materials_this_week.forEach { material ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MaterialIcon(material.element, 44.dp)
                                Text(material.material_name, style = TmtnType.body, color = colors.onSurface, modifier = Modifier.weight(1f))
                                Text("${material.count}개", style = TmtnType.label, color = colors.onSurface)
                            }
                        }
                    }
                    if (report.completed_count == 0) {
                        Text(if (report.days.any { it.status == "REST" }) "쉬어 간 날도 기록에 남았어요. 다음 실천은 한 장부터 골라 봐요." else "첫 실천이 남으면 이곳에 모아 둘게요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                        TmtnTonalButton("오늘의 카드 보러 가기", onGoPickCard)
                    }
                    if (onOpenJournal != null) TmtnPrimaryButton("주간면 읽기", onOpenJournal)
                }
            }
        }
    }
}

@Composable
private fun ComparisonBar(label: String, days: Int, current: Boolean, modifier: Modifier) {
    val colors = LocalTmtnColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = TmtnType.caption, color = colors.onSurfaceVariant)
        Text("${days}일", style = TmtnType.display, color = if (current) colors.secondary else colors.onSurface)
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().height((96 * days.coerceIn(0, 7) / 7f).dp)
                .background(if (current) colors.secondary else colors.outline, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
        }
    }
}

private fun shortRecordDate(value: String): String = runCatching { LocalDate.parse(value) }.getOrNull()?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: value
