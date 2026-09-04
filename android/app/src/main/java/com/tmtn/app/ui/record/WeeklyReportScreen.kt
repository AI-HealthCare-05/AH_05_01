package com.tmtn.app.ui.record

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
fun WeeklyReportScreen(state: RecordState, scope: CoroutineScope, onGoPickCard: () -> Unit) {
    val colors = LocalTmtnColors.current
    val report = state.weeklyReport.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("기록", style = TmtnType.title, color = colors.onSurface)
        RecordTabsForWeekly(state)

        if (report == null || report.completed_count == 0) {
            WeeklyEmptyCard(onGoPickCard)
        } else {
            Text("${report.start_date} ~ ${report.end_date}", style = TmtnType.caption, color = colors.onSurfaceVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("이번 주 ${report.completed_count}일 실천", style = TmtnType.title, color = colors.onSurface)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    report.days.forEach { day ->
                        val dayOfWeekLabel = LocalDate.parse(day.date).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DaySmallCell(day.status)
                            Text(dayOfWeekLabel, style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                    }
                }
                if (report.best_time_slot != null) {
                    Text("가장 많이 완료한 시간대: ${report.best_time_slot}", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }

            if (report.materials_this_week.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("이번 주에 모은 재료", style = TmtnType.label, color = colors.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun RecordTabsForWeekly(state: RecordState) {
    val colors = LocalTmtnColors.current
    Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        listOf(RecordTab.MONTHLY to "월간", RecordTab.WEEKLY to "주간").forEach { (tab, label) ->
            val selected = state.tab.value == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { state.tab.value = tab },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(label, style = TmtnType.label, color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                Box(modifier = Modifier.fillMaxWidth(0.2f).height(2.dp).background(if (selected) colors.onSurface else Color.Transparent))
            }
        }
    }
}

@Composable
private fun DaySmallCell(status: String) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier.size(34.dp).then(
            when (status) {
                "COMPLETED" -> Modifier.background(colors.onSurface, CircleShape)
                "REST" -> Modifier.background(colors.disabledContainer, CircleShape).border(1.5.dp, colors.onSurface, CircleShape)
                else -> Modifier.border(1.dp, colors.outline, CircleShape)
            },
        ),
    )
}

@Composable
private fun WeeklyEmptyCard(onGoPickCard: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(74.dp)) {
            Text(
                "앉아 쉬는 비버 (에셋 준비 중)", style = TmtnType.caption, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center),
            )
        }
        Text("이번 주 기록이\n아직 없어요", style = TmtnType.headline, color = colors.onSurface, textAlign = TextAlign.Center)
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
