package com.tmtn.app.ui.record

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.tmtn.app.network.model.CalendarDayItem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.cardhome.MaterialIcon
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Figma D02 · 기록 · 주간 리포트 (D08 데이터없음도 여기서 같이 처리) */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WeeklyReportScreen(state: RecordState, scope: CoroutineScope, onGoPickCard: () -> Unit) {
    val colors = LocalTmtnColors.current
    val report = state.weeklyReport.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("기록", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.heightIn(min = 48.dp).wrapContentSize(Alignment.CenterStart))
        RecordPeriodTabs(state.tab.value) { state.tab.value = it }

        if (report == null || report.completed_count == 0) {
            WeeklyEmptyCard(onGoPickCard)
        } else {
            val start = LocalDate.parse(report.start_date)
            val end = LocalDate.parse(report.end_date)
            Text("${start.year}. ${start.monthValue}. ${start.dayOfMonth}. ~ ${end.monthValue}. ${end.dayOfMonth}.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("이번 주 ${report.completed_count}일 실천", style = TmtnType.title, color = colors.onSurface)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    report.days.forEach { day ->
                        val dayOfWeekLabel = LocalDate.parse(day.date).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DaySmallCell(day)
                            Text(dayOfWeekLabel, style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendDot(colors.onSurface, "실천")
                    LegendDot(colors.disabledContainer, "쉼", outlineColor = colors.onSurface)
                    LegendDot(Color.Transparent, "미완료", dashed = true, outlineColor = colors.outline)
                    LegendDot(Color.Transparent, "오늘", outlineColor = colors.secondary)
                }
                if (report.best_time_slot != null) {
                    Text("가장 많이 완료한 시간대: ${report.best_time_slot}", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }

            if (report.materials_this_week.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("이번 주에 모은 재료", style = TmtnType.label, color = colors.onSurface)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.materials_this_week.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .background(colors.surface, RoundedCornerShape(999.dp))
                                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MaterialIcon(element = m.element, size = 24.dp)
                                Text("${m.material_name} ${m.count}개", style = TmtnType.caption, color = colors.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySmallCell(day: CalendarDayItem) {
    val colors = LocalTmtnColors.current
    val date = LocalDate.parse(day.date)
    val isToday = date == LocalDate.now()
    val status = day.status
    Box(
        modifier = Modifier.size(34.dp)
            .semantics { contentDescription = "${date.monthValue}월 ${date.dayOfMonth}일, " + when(status) {
                "COMPLETED" -> "실천"; "REST" -> "쉼"; "INCOMPLETE" -> "미완료"; else -> "기록 없음"
            } + if (isToday) ", 오늘" else "" }
            .then(if (isToday) Modifier.border(2.dp, colors.secondary, CircleShape).padding(2.dp) else Modifier).then(
            when (status) {
                "COMPLETED" -> Modifier.background(colors.onSurface, CircleShape)
                "REST" -> Modifier.background(colors.disabledContainer, CircleShape).border(1.5.dp, colors.onSurface, CircleShape)
                "INCOMPLETE" -> Modifier.dashedCircleBorder(1.5.dp, colors.outline)
                else -> Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text("${date.dayOfMonth}", style = TmtnType.label,
            color = if (status == "COMPLETED") colors.background else if (date.isAfter(LocalDate.now())) colors.onDisabled else colors.onSurface)
    }
}

@Composable
private fun WeeklyEmptyCard(onGoPickCard: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        com.tmtn.app.ui.common.TmtnMascot(com.tmtn.app.R.drawable.beaver_rest,
            "쉬고 있는 비버", Modifier.fillMaxWidth().height(180.dp))
        Text("이번 주 기록이 없어요", style = TmtnType.headline, color = colors.onSurface, textAlign = TextAlign.Center)
        Text("하루만 완료해도 여기에 표시됩니다.", style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text("지난주 기록은 월간에서 볼 수 있습니다.", style = TmtnType.body, color = colors.onSurface)
        }
        TmtnPrimaryButton(text = "오늘의 카드 고르러 가기", onClick = onGoPickCard)
    }
}
