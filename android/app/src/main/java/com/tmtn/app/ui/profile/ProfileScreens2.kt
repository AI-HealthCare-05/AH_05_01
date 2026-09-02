package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnChip
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnStepper
import com.tmtn.app.ui.onboarding.TmtnTextField
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma F08 · 몸 정보 수정 */
@Composable
fun HealthEditScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val user = state.userInfo.value
    val health = state.healthInput.value
    var heightText by remember(health) {
        mutableStateOf((health?.input_values?.get("height_cm") as? Number)?.toInt()?.toString() ?: "")
    }
    var weightText by remember(health) {
        mutableStateOf((health?.input_values?.get("weight_kg") as? Number)?.toInt()?.toString() ?: "")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "몸 정보", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                ProfileListItem("생년월일", if (user?.birth_year != null) "${user.birth_year}년 ${user.birth_month}월" else "-") { }
                ProfileListItem("성별", genderLabel(user?.gender)) { }
            }

            TmtnTextField(
                value = heightText,
                onValueChange = { if (it.length <= 3) heightText = it.filter(Char::isDigit) },
                label = "키 (cm)", keyboardType = KeyboardType.Number,
            )
            TmtnTextField(
                value = weightText,
                onValueChange = { if (it.length <= 3) weightText = it.filter(Char::isDigit) },
                label = "몸무게 (kg)", keyboardType = KeyboardType.Number,
            )

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
            ) {
                Text("값을 고치면 틈튼지수를 다시 계산합니다. 지난 기록은 그대로 남습니다.", style = TmtnType.body, color = colors.onSurface)
            }
            Text("생년월일은 연·월까지만 받습니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            TmtnPrimaryButton(
                text = "저장하고 다시 계산",
                onClick = {
                    val h = heightText.toIntOrNull()
                    val w = weightText.toIntOrNull()
                    if (h != null && w != null) scope.launch { state.saveHealthInput(h, w) }
                },
                enabled = heightText.toIntOrNull() != null && weightText.toIntOrNull() != null,
            )
        }
    }
}

private fun genderLabel(gender: String?): String = when (gender) {
    "MALE" -> "남성"
    "FEMALE" -> "여성"
    else -> "-"
}

/** Figma F09 · 운동 정보 수정 */
@Composable
fun ExerciseEditScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val existing = state.exerciseHabits.value

    var strengthCount by remember(existing) { mutableStateOf(existing?.strength_weekly_count ?: 0) }
    var strengthIntensity by remember(existing) { mutableStateOf(existing?.strength_intensity ?: "MODERATE") }
    var aerobicLow by remember(existing) { mutableStateOf(existing?.aerobic_low_minutes ?: 0) }
    var aerobicModerate by remember(existing) { mutableStateOf(existing?.aerobic_moderate_minutes ?: 0) }
    var aerobicHigh by remember(existing) { mutableStateOf(existing?.aerobic_high_minutes ?: 0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "운동 정보", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("근력운동", style = TmtnType.label, color = colors.onSurface)
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("주 횟수", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 2, 3, 4, 5).forEach { n ->
                        TmtnChip(
                            text = if (n == 0) "안 함" else "주 ${n}회", selected = strengthCount == n,
                            onClick = { strengthCount = n },
                        )
                    }
                }
                if (strengthCount > 0) {
                    Text("강도", style = TmtnType.caption, color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LIGHT" to "가볍게", "MODERATE" to "적당히", "HARD" to "힘들게").forEach { (value, label) ->
                            TmtnChip(text = label, selected = strengthIntensity == value, onClick = { strengthIntensity = value })
                        }
                    }
                }
            }

            Text("유산소 · 주당 시간", style = TmtnType.label, color = colors.onSurface)
            AerobicStepperRow("저강도", aerobicLow) { aerobicLow = it }
            AerobicStepperRow("중강도", aerobicModerate) { aerobicModerate = it }
            AerobicStepperRow("고강도", aerobicHigh) { aerobicHigh = it }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
            ) {
                Text("0분도 정상입니다. 값을 비워 두는 것과는 다르게 셉니다.", style = TmtnType.body, color = colors.onSurface)
            }
            Text("값을 고치면 틈튼지수를 다시 계산합니다.", style = TmtnType.body, color = colors.onSurfaceVariant)

            TmtnPrimaryButton(
                text = "저장하고 다시 계산",
                onClick = {
                    scope.launch {
                        state.saveExerciseHabits(strengthCount, strengthIntensity, aerobicLow, aerobicModerate, aerobicHigh)
                    }
                },
            )
        }
    }
}

@Composable
private fun AerobicStepperRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = TmtnType.label, color = colors.onSurface)
        TmtnStepper(
            value = minutes, unit = "분",
            onDecrement = { onChange((minutes - 10).coerceAtLeast(0)) },
            onIncrement = { onChange((minutes + 10).coerceAtMost(1000)) },
        )
    }
}
