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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Figma D01 · 기록 · 월 캘린더 (D04 기록없음/D07 일부실패도 여기서 같이 처리) */

/** ⚠️ 2026-09-04 디자인 스펙 반영: 미완료 셀은 피그마 기준 실선이 아니라 점선 원
 * (dash="2.5 2.5")임. Modifier.border()는 실선만 지원해서, Canvas에 직접 점선 원을
 * 그리는 방식으로 구현. */
private fun Modifier.dashedCircleBorder(width: Dp, color: Color, dashDp: Dp = 2.5.dp, gapDp: Dp = 2.5.dp): Modifier =
    this.drawBehind {
        val strokeWidthPx = width.toPx()
        drawCircle(
            color = color,
            radius = (size.minDimension - strokeWidthPx) / 2f,
            style = Stroke(
                width = strokeWidthPx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashDp.toPx(), gapDp.toPx()), 0f),
            ),
        )
    }

@Composable
fun MonthlyCalendarScreen(state: RecordState, scope: CoroutineScope, onGoPickCard: () -> Unit) {
    val colors = LocalTmtnColors.current
    val calendar = state.monthlyCalendar.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("기록", style = TmtnType.title, color = colors.onSurface)

        RecordTabs(state, scope)

        if (state.monthlyLoadFailed.value) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.rewardContainer, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("일부 날짜를 불러오지 못했습니다", style = TmtnType.bodyLarge, color = colors.onSurface)
                Text("불러오지 못한 날은 물음표로 두었습니다. 값을 지어내지 않습니다.", style = TmtnType.body, color = colors.onSurface)
            }
        }

        val isEmpty = calendar != null && calendar.completed_count == 0 && calendar.rest_count == 0 &&
            calendar.days.none { it.status == "INCOMPLETE" }
        // ⚠️ 예전엔 isEmpty면 무조건 EmptyRecordCard(마스코트 안내)로 바꿔치기했는데, 그
        // 화면엔 ‹ › 이동 버튼이 아예 없음. 그래서 아직 기록이 없는 미래 달(예: 10월)로
        // 넘어가면 캘린더 자체가 사라지고 되돌아올 방법이 없었음("9월에서 10월로 넘어가니
        // 달력이 안 뜬다"는 게 이 증상). 지금 보고 있는 달이 실제 이번 달일 때만(=처음
        // 들어왔을 때) 안내 화면을 보여주고, 다른 달로 이동한 상태에서는 비어있어도 항상
        // 진짜 캘린더(‹ › 포함)를 그대로 보여줌.
        val today = LocalDate.now()
        val isViewingCurrentMonth = state.year.value == today.year && state.month.value == today.monthValue

        if (calendar == null || (isEmpty && isViewingCurrentMonth)) {
            EmptyRecordCard(onGoPickCard)
        } else {
            MonthCalendarCard(state, scope)
            StreakCard(state)
        }
    }
}

@Composable
private fun RecordTabs(state: RecordState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        listOf(RecordTab.MONTHLY to "월간", RecordTab.WEEKLY to "주간").forEach { (tab, label) ->
            val selected = state.tab.value == tab
            Column(
                modifier = Modifier.weight(1f).fillMaxSize()
                    .clickable {
                        state.tab.value = tab
                        if (tab == RecordTab.WEEKLY) scope.launch { state.loadWeekly() }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(label, style = TmtnType.label, color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                Box(
                    modifier = Modifier.fillMaxWidth(0.2f).height(2.dp)
                        .background(if (selected) colors.onSurface else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun MonthCalendarCard(state: RecordState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val calendar = state.monthlyCalendar.value ?: return
    val ym = YearMonth.of(state.year.value, state.month.value)
    val firstDayOfWeek = ym.atDay(1).dayOfWeek.value // 1=월 ~ 7=일
    val statusByDate = calendar.days.associateBy { it.date }

    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "‹", style = TmtnType.title, color = colors.onSurfaceVariant,
                modifier = Modifier.clickable {
                    val prev = ym.minusMonths(1)
                    scope.launch { state.loadMonthly(prev.year, prev.monthValue) }
                },
            )
            Text("${state.year.value}년 ${state.month.value}월", style = TmtnType.label, color = colors.onSurface)
            Text(
                "›", style = TmtnType.title, color = colors.onSurface,
                modifier = Modifier.clickable {
                    val next = ym.plusMonths(1)
                    scope.launch { state.loadMonthly(next.year, next.monthValue) }
                },
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("월", "화", "수", "목", "금", "토", "일").forEach {
                Text(it, style = TmtnType.caption, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }

        // 42칸(6주) 고정 그리드 - HANDOFF 규칙.
        // ⚠️ 2026-09-04 디자인 스펙 반영: 이전/다음 달 날짜가 빈칸이었는데, 피그마 기준
        // 흐린 색으로 숫자를 그대로 보여줘야 함(첨부 이미지 27~31, 1~6 참고). 날짜 자체를
        // LocalDate로 계산해서, 이번 달이면 실제 상태로, 아니면 흐린 숫자만 표시.
        val leadingBlanks = firstDayOfWeek - 1
        val totalCells = 42
        val monthStart = ym.atDay(1)
        val today = LocalDate.now()
        (0 until totalCells).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { idx ->
                    val cellDate = monthStart.plusDays((idx - leadingBlanks).toLong())
                    if (cellDate.monthValue == state.month.value && cellDate.year == state.year.value) {
                        val dateStr = "%04d-%02d-%02d".format(state.year.value, state.month.value, cellDate.dayOfMonth)
                        val status = statusByDate[dateStr]?.status
                        val isToday = state.year.value == today.year && state.month.value == today.monthValue &&
                            cellDate.dayOfMonth == today.dayOfMonth
                        DayCell(cellDate.dayOfMonth, status, isToday) { scope.launch { state.openDayDetail(dateStr) } }
                    } else {
                        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                            Text("${cellDate.dayOfMonth}", style = TmtnType.label, color = colors.outline)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(colors.onSurface, "실천")
            LegendDot(colors.disabledContainer, "쉼", outlineColor = colors.onSurface)
            LegendDot(Color.Transparent, "미완료", dashed = true, outlineColor = colors.outline)
            LegendDot(Color.Transparent, "오늘", outlineColor = colors.secondary)
        }
        Text(
            "${state.month.value}월 실천 ${calendar.completed_count}일 · 쉼 ${calendar.rest_count}일",
            style = TmtnType.caption, color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCell(day: Int, status: String?, isToday: Boolean, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            // ⚠️ "오늘" 표시가 예전엔 when()의 마지막 분기라 그 날에 완료/쉼/미완료 같은
            // 상태가 하나라도 있으면 전혀 안 보였음(실천+오늘, 쉼+오늘, 미완료+오늘을 구분
            // 못 함). 오늘 표시를 바깥쪽 링으로 분리해서, 상태와 무관하게 항상 같이 보이게 함.
            .then(if (isToday) Modifier.border(2.dp, colors.secondary, CircleShape).padding(2.dp) else Modifier)
            .clickable { onClick() }
            .then(
                when (status) {
                    "COMPLETED" -> Modifier.background(colors.onSurface, CircleShape)
                    "REST" -> Modifier.background(colors.disabledContainer, CircleShape).border(1.5.dp, colors.onSurface, CircleShape)
                    "INCOMPLETE" -> Modifier.dashedCircleBorder(1.5.dp, colors.outline)
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day", style = TmtnType.label,
            color = if (status == "COMPLETED") colors.background else colors.onSurface,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, outlineColor: Color? = null, dashed: Boolean = false) {
    val colors = LocalTmtnColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            // ⚠️ 2026-09-04 디자인 스펙 반영: 캘린더 셀은 원형인데 범례만 사각형이었음(피그마는
            // 전부 원형). 모양을 CircleShape로 통일하고, 점선(미완료)까지 실제 셀과 똑같이 맞춤.
            modifier = Modifier.size(12.dp).background(color, CircleShape)
                .then(
                    when {
                        dashed && outlineColor != null -> Modifier.dashedCircleBorder(1.5.dp, outlineColor, dashDp = 1.5.dp, gapDp = 1.5.dp)
                        outlineColor != null -> Modifier.border(1.5.dp, outlineColor, CircleShape)
                        else -> Modifier
                    },
                ),
        )
        Text(label, style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun StreakCard(state: RecordState) {
    val colors = LocalTmtnColors.current
    val streak = state.streak.value ?: return
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("연속 기록", style = TmtnType.label, color = colors.onSurface)
            Text("규칙 보기 ›", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${streak.current_streak}", style = TmtnType.display, color = colors.onSurface)
            Text("일째", style = TmtnType.label, color = colors.onSurface)
        }
        Text("쉼으로 표시한 날은 기록을 끊지 않아요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        Text("가장 오래 이어간 기록 ${streak.longest_streak}일", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun EmptyRecordCard(onGoPickCard: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(24.dp)).border(1.dp, colors.outline, RoundedCornerShape(24.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Text(
                "마스코트 이미지 (에셋 준비 중)", style = TmtnType.caption, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            "아직 쌓인 기록이 없어.\n오늘 한 가지만 해보면 여기가 채워져.",
            style = TmtnType.bodyLarge, color = colors.onSurface, textAlign = TextAlign.Center,
        )
        Text(
            "지나간 날은 미완료로 남습니다. 쉼은 직접 눌러야 기록돼요.",
            style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        TmtnPrimaryButton(text = "오늘의 카드 고르기", onClick = onGoPickCard)
    }
}
