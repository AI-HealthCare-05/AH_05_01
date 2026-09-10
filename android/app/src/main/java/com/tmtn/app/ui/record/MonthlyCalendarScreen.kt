package com.tmtn.app.ui.record

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.width
import com.tmtn.app.ui.theme.TmtnMotion
import com.tmtn.app.ui.theme.tmtnClickable
import com.tmtn.app.ui.theme.tmtnPressFeedback
import com.tmtn.app.ui.theme.rememberTmtnReducedMotion
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
internal fun Modifier.dashedCircleBorder(width: Dp, color: Color, dashDp: Dp = 2.5.dp, gapDp: Dp = 2.5.dp): Modifier =
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
fun MonthlyCalendarScreen(
    state: RecordState, scope: CoroutineScope, onGoPickCard: () -> Unit,
    onMonthChange: ((YearMonth) -> Unit)? = null,
    onDateSelected: ((String) -> Unit)? = null,
    onPeriodSelected: ((RecordTab) -> Unit)? = null,
) {
    val colors = LocalTmtnColors.current
    val calendar = state.monthlyCalendar.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("기록", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text("오늘", style = TmtnType.label, color = colors.onSurface,
                modifier = Modifier.tmtnClickable(enabled = !state.isLoading.value) {
                    val current = YearMonth.now()
                    if (onMonthChange != null) onMonthChange(current)
                    else scope.launch { state.loadMonthly(current.year, current.monthValue) }
                }.size(48.dp).wrapContentSize())
        }

        RecordPeriodTabs(state.tab.value) { tab ->
            if (onPeriodSelected != null) onPeriodSelected(tab) else {
                state.tab.value = tab
                if (tab == RecordTab.WEEKLY) scope.launch { state.loadWeekly() }
            }
        }

        if (state.monthlyLoadFailed.value) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.rewardContainer, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("일부 날짜를 불러오지 못했습니다", style = TmtnType.bodyLarge, color = colors.onSurface)
                Text("연결을 확인한 뒤 다시 열어 주세요.", style = TmtnType.body, color = colors.onSurface)
            }
        }

        if (calendar == null) {
            if (!state.monthlyLoadFailed.value) Text("기록을 불러오는 중이에요", style = TmtnType.body, color = colors.onSurfaceVariant)
        } else {
            MonthCalendarCard(state, scope, onMonthChange, onDateSelected)
            if (calendar.days.none { it.status in listOf("COMPLETED", "REST", "INCOMPLETE") }) {
                EmptyRecordCard(onGoPickCard)
            } else StreakCard(state)
        }
    }
}

/** Figma D01/D02: short underline tabs, with native selection semantics. */
@Composable
internal fun RecordPeriodTabs(selectedTab: RecordTab, onSelect: (RecordTab) -> Unit) {
    val colors = LocalTmtnColors.current
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        listOf(RecordTab.MONTHLY to "월간", RecordTab.WEEKLY to "주간").forEach { (tab, label) ->
            val interactions = remember { MutableInteractionSource() }
            val selected = selectedTab == tab
            Column(Modifier.weight(1f).heightIn(min = 48.dp)
                .tmtnPressFeedback(interactions)
                .selectable(selected, interactionSource = interactions, indication = ripple(), role = Role.Tab,
                    onClick = { if (!selected) onSelect(tab) })
                .padding(top = 12.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label, style = TmtnType.label, color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                Box(Modifier.width(36.dp).height(2.dp).background(if (selected) colors.onSurface else Color.Transparent, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MonthCalendarCard(
    state: RecordState, scope: CoroutineScope,
    onMonthChange: ((YearMonth) -> Unit)? = null,
    onDateSelected: ((String) -> Unit)? = null,
) {
    val colors = LocalTmtnColors.current
    val calendar = state.monthlyCalendar.value ?: return
    val ym = YearMonth.of(state.year.value, state.month.value)
    val firstDayOfWeek = ym.atDay(1).dayOfWeek.value // 1=월 ~ 7=일
    val statusByDate = calendar.days.associateBy { it.date }
    val reducedMotion = rememberTmtnReducedMotion()
    val monthAlpha = remember(ym) { Animatable(if (reducedMotion) 1f else .75f) }
    LaunchedEffect(ym, reducedMotion) {
        if (reducedMotion) monthAlpha.snapTo(1f)
        else monthAlpha.animateTo(1f, tween(160, easing = TmtnMotion.EaseOut))
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", style = TmtnType.title, color = colors.onSurfaceVariant,
                modifier = Modifier.tmtnClickable(enabled = !state.isLoading.value) {
                    val prev = ym.minusMonths(1)
                    if (onMonthChange != null) onMonthChange(prev) else scope.launch { state.loadMonthly(prev.year, prev.monthValue) }
                }.semantics { contentDescription = "이전 달" }.size(48.dp).wrapContentSize(),
            )
            Text("${state.year.value}년 ${state.month.value}월", style = TmtnType.label, color = colors.onSurface)
            Text(
                "›", style = TmtnType.title, color = colors.onSurface,
                modifier = Modifier.tmtnClickable(enabled = !state.isLoading.value) {
                    val next = ym.plusMonths(1)
                    if (onMonthChange != null) onMonthChange(next) else scope.launch { state.loadMonthly(next.year, next.monthValue) }
                }.semantics { contentDescription = "다음 달" }.size(48.dp).wrapContentSize(),
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
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = monthAlpha.value }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until totalCells).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { idx ->
                    val cellDate = monthStart.plusDays((idx - leadingBlanks).toLong())
                    if (cellDate.monthValue == state.month.value && cellDate.year == state.year.value) {
                        val dateStr = "%04d-%02d-%02d".format(state.year.value, state.month.value, cellDate.dayOfMonth)
                        val status = statusByDate[dateStr]?.status
                        val isToday = state.year.value == today.year && state.month.value == today.monthValue &&
                            cellDate.dayOfMonth == today.dayOfMonth
                        Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            DayCell(cellDate.dayOfMonth, status, isToday, cellDate.isAfter(today)) {
                                if (onDateSelected != null) onDateSelected(dateStr) else scope.launch { state.openDayDetail(dateStr) }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            Text("${cellDate.dayOfMonth}", style = TmtnType.label, color = colors.outline)
                        }
                    }
                }
            }
        }

        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun DayCell(day: Int, status: String?, isToday: Boolean, isFuture: Boolean, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth().heightIn(min = 48.dp)
            .tmtnClickable { onClick() }
            .semantics { contentDescription = "${day}일, " + when (status) { "COMPLETED" -> "실천"; "REST" -> "쉼"; "INCOMPLETE" -> "미완료"; else -> "기록 없음" } + if (isToday) ", 오늘" else "" }
            .wrapContentSize(Alignment.Center).size(34.dp)
            // ⚠️ "오늘" 표시가 예전엔 when()의 마지막 분기라 그 날에 완료/쉼/미완료 같은
            // 상태가 하나라도 있으면 전혀 안 보였음(실천+오늘, 쉼+오늘, 미완료+오늘을 구분
            // 못 함). 오늘 표시를 바깥쪽 링으로 분리해서, 상태와 무관하게 항상 같이 보이게 함.
            .then(if (isToday) Modifier.border(2.dp, colors.secondary, CircleShape).padding(2.dp) else Modifier)
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
            color = if (status == "COMPLETED") colors.background else if (isFuture && status == null) colors.onDisabled else colors.onSurface,
        )
    }
}

@Composable
internal fun LegendDot(color: Color, label: String, outlineColor: Color? = null, dashed: Boolean = false) {
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
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("연속 기록", style = TmtnType.label, color = colors.onSurface)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${streak.current_streak}", style = TmtnType.display, color = colors.secondary)
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        com.tmtn.app.ui.common.TmtnMascot(com.tmtn.app.R.drawable.beaver_empty,
            "첫 기록을 기다리는 비버", Modifier.fillMaxWidth().height(180.dp))
        Text(
            "첫 실천을 기록해 보세요.",
            style = TmtnType.bodyLarge, color = colors.onSurface, textAlign = TextAlign.Center,
        )
        Text(
            "지나간 날은 미완료로 남습니다. 쉼은 직접 눌러야 기록돼요.",
            style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        TmtnPrimaryButton(text = "오늘의 카드 고르기", onClick = onGoPickCard)
    }
}
