package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
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

/** Figma F08 · 신체 정보 수정 */
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
    // Registered gender is read-only; pregnancy remains editable independently.
    val gender = user?.gender
    var isPregnant by remember(user) { mutableStateOf(user?.is_pregnant) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "신체 정보", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("기본 정보", style = TmtnType.title, color = colors.onSurface)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("태어난 연·월", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text(if (user?.birth_year != null && user.birth_month != null) "${user.birth_year}년 ${user.birth_month}월" else "등록된 정보가 없어요",
                    style = TmtnType.bodyLarge, color = colors.onSurface)
            }
            androidx.compose.material3.HorizontalDivider(color = colors.outlineVariant)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("성별", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text(when (gender) { "MALE" -> "남성"; "FEMALE" -> "여성"; else -> "등록된 정보가 없어요" },
                    style = TmtnType.bodyLarge, color = colors.onSurface)
                Text("가입할 때 등록한 정보예요. 변경할 수 없어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            if (gender == "FEMALE") {
                Text("임신 여부 (필수)", style = TmtnType.label, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TmtnChip(text = "아니요", selected = isPregnant == false, onClick = { isPregnant = false })
                    TmtnChip(text = "예", selected = isPregnant == true, onClick = { isPregnant = true })
                }
                Text(
                    "임신 중에는 참고 점수를 정확히 계산하기 어려워 일부 항목을 표시하지 않을 수 있어요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
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

            Text("다음 틈튼지수 계산에 사용해요. 지난 기록은 그대로 남아요.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            val saveBlockedReason = when {
                (heightText.toIntOrNull() ?: 0) <= 0 -> "키를 입력해 주세요."
                (weightText.toIntOrNull() ?: 0) <= 0 -> "몸무게를 입력해 주세요."
                gender == null -> "등록된 성별을 확인하지 못했어요. 이전 화면에서 다시 불러와 주세요."
                gender == "FEMALE" && isPregnant == null -> "임신 여부를 선택해 주세요."
                else -> null
            }
            TmtnPrimaryButton(
                text = if (state.isLoading.value) "저장 중…" else "신체 정보 저장",
                onClick = {
                    val h = heightText.toIntOrNull()
                    val w = weightText.toIntOrNull()
                    scope.launch {
                        if (gender == "FEMALE" && isPregnant != user?.is_pregnant) {
                            state.saveGenderAndPregnancy(gender, isPregnant)
                            if (state.errorMessage.value != null) return@launch
                        }
                        if (h != null && w != null) state.saveHealthInput(h, w)
                    }
                },
                enabled = saveBlockedReason == null && !state.isLoading.value,
                disabledReason = saveBlockedReason,
            )
        }
    }
}

/** Figma F09 · 운동 정보 수정 */
@Composable
fun ExerciseEditScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val existing = state.exerciseHabits.value

    var strengthCount by remember(existing) { mutableStateOf(existing?.strength_weekly_count ?: 0) }
    var strengthIntensity by remember(existing) { mutableStateOf(existing?.strength_intensity) }
    var aerobicLow by remember(existing) { mutableStateOf(existing?.aerobic_low_minutes ?: 0) }
    var aerobicModerate by remember(existing) { mutableStateOf(existing?.aerobic_moderate_minutes ?: 0) }
    var aerobicHigh by remember(existing) { mutableStateOf(existing?.aerobic_high_minutes ?: 0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "운동 정보", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            com.tmtn.app.ui.common.TmtnExerciseFields(
                strengthCount, { strengthCount = it }, strengthIntensity, { strengthIntensity = it },
                aerobicLow, { aerobicLow = it.coerceAtMost(1000) },
                aerobicModerate, { aerobicModerate = it.coerceAtMost(1000) },
                aerobicHigh, { aerobicHigh = it.coerceAtMost(1000) },
            )
            Text("저장하면 틈튼지수에 반영돼요.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            TmtnPrimaryButton(
                text = if (state.isLoading.value) "저장 중…" else "운동 정보 저장",
                onClick = {
                    scope.launch {
                        state.saveExerciseHabits(strengthCount, strengthIntensity, aerobicLow, aerobicModerate, aerobicHigh)
                    }
                },
                enabled = !state.isLoading.value && (strengthCount == 0 || strengthIntensity != null),
                disabledReason = if (strengthCount > 0 && strengthIntensity == null) "근력운동의 강도를 골라 주세요." else null,
            )
        }
    }
}
