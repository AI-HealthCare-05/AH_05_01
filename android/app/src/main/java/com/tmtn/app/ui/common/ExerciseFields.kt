package com.tmtn.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnIntensityCard
import com.tmtn.app.ui.onboarding.TmtnIntensityBand
import com.tmtn.app.ui.onboarding.TmtnStepper
import com.tmtn.app.ui.theme.*

/** One presentation for signup and profile editing. Callers keep their existing save APIs. */
@Composable
fun TmtnExerciseFields(
    strengthCount: Int, onStrengthCount: (Int) -> Unit,
    intensity: String?, onIntensity: (String) -> Unit,
    lowMinutes: Int, onLowMinutes: (Int) -> Unit,
    moderateMinutes: Int, onModerateMinutes: (Int) -> Unit,
    highMinutes: Int, onHighMinutes: (Int) -> Unit,
) {
    val colors = LocalTmtnColors.current
    val textScale = LocalDensity.current.fontScale * LocalTmtnTextScale.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("근력운동", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })
            Text("팔굽혀펴기 · 스쿼트 · 기구 운동 등", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("일주일에 몇 번 하나요?", style = TmtnType.body, color = colors.onSurface)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 320.dp || textScale > 1.3f) 2 else 3
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..5).toList().chunked(columns).forEach { group ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { count ->
                                TmtnIntensityCard(if (count == 0) "안 함" else if (count == 5) "주 5회+" else "주 ${count}회", "",
                                    strengthCount == count, { onStrengthCount(count) },
                                    Modifier.weight(1f).fillMaxHeight().testTag("strength-count-$count"))
                            }
                        }
                    }
                }
            }
        }
        if (strengthCount > 0) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("어느 정도 힘들게 하나요?", style = TmtnType.body, color = colors.onSurface)
            val choices = listOf(Triple("LIGHT", "가볍게", "15회 이상\n가능해요"), Triple("MODERATE", "적당히", "10~12회면\n힘들어요"), Triple("HARD", "힘들게", "8회면\n한계예요"))
            BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                if (textScale > 1.3f || maxWidth < 290.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { (value, title, hint) ->
                            TmtnIntensityCard(title, hint.replace('\n', ' '), intensity == value, { onIntensity(value) }, Modifier.fillMaxWidth().testTag("strength-intensity-$value"))
                        }
                    }
                } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (value, title, hint) ->
                        TmtnIntensityCard(title, hint, intensity == value, { onIntensity(value) }, Modifier.weight(1f).fillMaxHeight().testTag("strength-intensity-$value"))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("유산소 운동", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })
            Text("강도별로 일주일 동안 한 시간을 알려 주세요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        AerobicInput("저강도", "숨이 차지 않아요", "산책 · 스트레칭", lowMinutes, onLowMinutes)
        AerobicInput("중강도", "숨이 조금 차요", "빠르게 걷기 · 자전거", moderateMinutes, onModerateMinutes)
        AerobicInput("고강도", "숨이 많이 차요", "달리기 · 등산", highMinutes, onHighMinutes)
        Text("하지 않는 운동은 0분으로 두세요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun AerobicInput(label: String, description: String, examples: String, minutes: Int, onChange: (Int) -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(12.dp))
        .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TmtnIntensityBand(label, colors.secondaryContainer)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(description, style = TmtnType.body, color = colors.onSurface)
            Text(examples, style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        TmtnStepper(value = minutes, unit = "분", onDecrement = { onChange((minutes - 10).coerceAtLeast(0)) }, onIncrement = { onChange(minutes + 10) })
    }
}
