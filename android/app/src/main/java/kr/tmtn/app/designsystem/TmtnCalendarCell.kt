package kr.tmtn.app.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kr.tmtn.app.domain.model.DayStatus

/**
 * 기록 달력의 날짜 한 칸.
 *
 * **실선 = 쉼(내가 고른 것), 점선 = 미완료(빠진 것).**
 * 색만으로 가르지 않는다. 색을 구별하기 어려운 사람도 선 종류로 읽을 수 있어야 한다.
 * 이 대비를 없애면 두 상태를 못 알아본다.
 *
 * | 상태 | 표시 |
 * |---|---|
 * | 실천 | 먹색 꽉 찬 원 + 흰 숫자 |
 * | 쉼 | 먹색 실선 테두리 + 바탕 `DisabledContainer` |
 * | 미완료 | 회색 점선 테두리 |
 * | 오늘 | 주황 실선 테두리 (위 상태 위에 겹쳐 그린다) |
 */
@Composable
fun CalendarDayCell(
    day: Int,
    status: DayStatus,
    isToday: Boolean,
    inCurrentMonth: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val label = buildString {
        append("${day}일 ")
        append(
            when (status) {
                DayStatus.DONE -> "실천함"
                DayStatus.REST -> "쉼"
                DayStatus.MISSED -> "미완료"
                DayStatus.FUTURE -> "예정"
            },
        )
        if (isToday) append(", 오늘")
    }

    // 그려지는 원은 34dp 지만 **누르는 영역은 그보다 넓다.**
    // 날짜 칸을 원 크기 그대로 두면 손가락으로 정확히 짚기 어렵다.
    // 가로는 달력이 나눠 준 칸을 다 쓰고, 세로는 최소 터치 크기를 지킨다.
    // (7칸이라 가로로 48dp 를 온전히 주면 화면을 넘긴다 — 세로로 벌어 채운다.)
    Box(
        Modifier
            .fillMaxWidth()
            .height(TmtnTarget.Min)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // 원·테두리는 그림일 뿐이다. 읽어 주는 것은 위에서 만든 한 문장이면 된다.
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(TmtnCalendar.CellSize)) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            when (status) {
                DayStatus.DONE ->
                    drawCircle(TmtnColor.Primary, radius = r, center = center)

                DayStatus.REST -> {
                    drawCircle(TmtnColor.DisabledContainer, radius = r, center = center)
                    drawCircle(
                        TmtnColor.OnSurface,
                        radius = r - TmtnCalendar.RingWidth.toPx() / 2f,
                        center = center,
                        style = Stroke(width = TmtnCalendar.RingWidth.toPx()),
                    )
                }

                DayStatus.MISSED ->
                    drawCircle(
                        TmtnColor.Outline,
                        radius = r - TmtnCalendar.RingWidth.toPx() / 2f,
                        center = center,
                        style = Stroke(
                            width = TmtnCalendar.RingWidth.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(
                                    TmtnCalendar.Dash.dp.toPx(),
                                    TmtnCalendar.Gap.dp.toPx(),
                                ),
                            ),
                        ),
                    )

                DayStatus.FUTURE -> Unit
            }

            // 오늘 테두리는 맨 위에 겹친다. 주황은 "오늘" 에만 쓰는 색이다.
            if (isToday) {
                drawCircle(
                    TmtnColor.Secondary,
                    radius = r - TmtnCalendar.TodayRingWidth.toPx() / 2f,
                    center = center,
                    style = Stroke(width = TmtnCalendar.TodayRingWidth.toPx()),
                )
            }
        }

        Text(
            "$day",
            style = TmtnText.Caption,
            color = when {
                status == DayStatus.DONE -> TmtnColor.OnPrimary
                !inCurrentMonth -> TmtnColor.OnSurfaceFaint
                status == DayStatus.FUTURE -> TmtnColor.OnDisabled
                else -> TmtnColor.OnSurface
            },
        )
    }
}

/** 달력 아래 범례. 네 상태가 무엇인지 글자로도 밝혀 둔다. */
@Composable
fun CalendarLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(TmtnSpace.S12)) {
        LegendItem(DayStatus.DONE, "실천")
        LegendItem(DayStatus.REST, "쉼")
        LegendItem(DayStatus.MISSED, "미완료")
    }
}

@Composable
private fun LegendItem(status: DayStatus, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmtnSpace.S4),
    ) {
        Canvas(Modifier.size(TmtnCalendar.LegendSwatch)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            when (status) {
                DayStatus.DONE -> drawCircle(TmtnColor.Primary, r, c)
                DayStatus.REST -> {
                    drawCircle(TmtnColor.DisabledContainer, r, c)
                    drawCircle(
                        TmtnColor.OnSurface, r - TmtnCalendar.RingWidth.toPx() / 2f, c,
                        style = Stroke(TmtnCalendar.RingWidth.toPx()),
                    )
                }
                else -> drawCircle(
                    TmtnColor.Outline, r - TmtnCalendar.RingWidth.toPx() / 2f, c,
                    style = Stroke(
                        TmtnCalendar.RingWidth.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(TmtnCalendar.Dash.dp.toPx(), TmtnCalendar.Gap.dp.toPx()),
                        ),
                    ),
                )
            }
        }
        Text(label, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
    }
}
