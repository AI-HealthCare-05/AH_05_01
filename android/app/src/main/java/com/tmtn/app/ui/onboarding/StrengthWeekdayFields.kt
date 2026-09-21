package com.tmtn.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.*

/** Existing API buckets: 0, 1, 2, 3, 4, 5 (five or more days). Never transmit weekdays. */
internal fun strengthCountForDays(days: Set<Int>): Int {
    require(days.all { it in 0..6 })
    return days.size.coerceAtMost(5)
}

private val weekdayNames = listOf("월", "화", "수", "목", "금", "토", "일")

/** Figma 1428:3474 / implementation notes 1432:3495, using the product's Pretendard. */
@Composable
internal fun StrengthWeekdayFields(
    selectedDays: Set<Int>?, savedCount: Int,
    onToggle: (Int) -> Unit, onClear: () -> Unit,
    intensity: String?, onIntensity: (String) -> Unit,
) {
    val colors = LocalTmtnColors.current
    val largeType = LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.3f
    val count = selectedDays?.size ?: savedCount
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("근력운동", style = TmtnType.missionName, color = colors.onSurface, modifier = Modifier.semantics { heading() })
            Text("팔굽혀펴기 · 스쿼트 · 기구 운동 등", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("운동하는 요일", style = TmtnType.body, color = colors.onSurface)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Keep 48dp touch targets at narrow widths instead of squeezing seven buttons.
                val columns = if (largeType) 3 else if (maxWidth < 342.dp) 4 else 7
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekdayNames.indices.chunked(columns).forEach { group ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            group.forEach { day ->
                                WeekdayToggle(day, selectedDays?.contains(day) == true, { onToggle(day) },
                                    Modifier.weight(1f).fillMaxHeight())
                            }
                            repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            val summary = when {
                selectedDays == null -> "저장된 일수예요 · 요일 정보는 없어요"
                count == 0 -> "운동한 날이 없으면 선택하지 않아도 돼요"
                else -> selectedDays.sorted().joinToString(" · ") { weekdayNames[it] } +
                    if (count >= 5) "\n주 5일 이상으로 저장해요" else "\n선택한 날 수로 계산해요"
            }
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(12.dp)).padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }) {
                if (largeType) {
                    Text(if (selectedDays == null && count == 5) "주 5일 이상" else "주 ${count}일", style = TmtnType.inputHeadline, color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(summary, style = TmtnType.caption, color = colors.onSurfaceVariant)
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (selectedDays == null && count == 5) "주 5일+" else "주 ${count}일", style = TmtnType.inputHeadline, color = colors.primary)
                    Text(summary, style = TmtnType.caption, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
            if (selectedDays == null && savedCount > 0) {
                Text("요일을 다시 고르면 새 일수로 바뀌어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                TmtnTextButton("근력운동 안 함으로 변경", onClear)
            }
        }
        if (count > 0) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("한 번에 어느 정도 힘들게 하나요?", style = TmtnType.body, color = colors.onSurface)
            val choices = listOf(Triple("LIGHT", "가볍게", "15회 이상\n가능해요"), Triple("MODERATE", "적당히", "10~12회면\n힘들어요"), Triple("HARD", "힘들게", "8회면\n한계예요"))
            BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                if (largeType || maxWidth < 290.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (value, title, hint) ->
                        TmtnIntensityCard(title, hint.replace('\n', ' '), intensity == value, { onIntensity(value) },
                            Modifier.fillMaxWidth().testTag("strength-intensity-$value"), outlined = false)
                    }
                } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (value, title, hint) ->
                        TmtnIntensityCard(title, hint, intensity == value, { onIntensity(value) },
                            Modifier.weight(1f).fillMaxHeight().testTag("strength-intensity-$value"), outlined = false)
                    }
                }
            }
        }
        Text("운동한 날이 없다면, 선택 없이 다음으로 넘어가세요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun WeekdayToggle(day: Int, selected: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val colors = LocalTmtnColors.current
    val reduced = rememberTmtnReducedMotion()
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val fill by animateColorAsState(if (selected) colors.primary else colors.surface,
        animationSpec = if (reduced) snap() else tween(TmtnMotion.PressMillis), label = "weekday selection")
    Column(modifier.heightIn(min = 72.dp).tmtnFocusOutline(interactions, shape).clip(shape).background(fill)
        .toggleable(selected, interactionSource = interactions, indication = null, role = Role.Checkbox, onValueChange = { onToggle() })
        .testTag("strength-weekday-$day").semantics { contentDescription = "${weekdayNames[day]}요일" }
        .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("${weekdayNames[day]}\n${if (selected) "✓" else "−"}", style = TmtnType.actionLabel,
            color = if (selected) colors.background else colors.onSurface, textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { })
    }
}
