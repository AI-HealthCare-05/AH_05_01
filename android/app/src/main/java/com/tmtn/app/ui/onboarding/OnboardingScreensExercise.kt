package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** A16 strength section: Figma 1428:3474. Aerobic fields and the combined save flow stay intact. */
@Composable
fun A08ExerciseScreen(state: OnboardingState, scope: CoroutineScope, hasSensorPermissions: () -> Boolean) {
    val colors = LocalTmtnColors.current
    var weeklyCount by state.strengthWeeklyCount
    var intensity by state.strengthIntensity
    var lowMin by state.aerobicLowMinutes
    var modMin by state.aerobicModerateMinutes
    var highMin by state.aerobicHighMinutes

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TmtnTopBar(title = "운동 정보 · 2 / 2", onBack = { state.step.value = OnboardingStep.A07_PROFILE })

        Column(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {


            Text("평소 일주일,\n운동하는 날을 골라 주세요.", style = TmtnType.inputHeadline, color = colors.onSurface)
            Text("같은 날 여러 번 운동해도 1일로 세어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            com.tmtn.app.ui.common.TmtnExerciseFields(
                weeklyCount, { weeklyCount = it }, intensity, { intensity = it },
                lowMin, { lowMin = it }, modMin, { modMin = it }, highMin, { highMin = it },
                strengthContent = {
                    StrengthWeekdayFields(state.strengthWeekdays.value, weeklyCount, state::toggleStrengthWeekday,
                        state::clearStrengthWeekdays, intensity, { intensity = it })
                },
            )

        }
        Box(Modifier.fillMaxWidth().background(colors.background).padding(horizontal = 20.dp, vertical = 14.dp)) {
            TmtnPrimaryButton(
                text = if (state.isLoading.value) "정보 저장 중…" else "다음으로",
                onClick = { scope.launch { state.submitExerciseHabits(hasSensorPermissions()) } },
                enabled = weeklyCount == 0 || intensity != null,
                loading = state.isLoading.value,
                disabledReason = if (weeklyCount > 0 && intensity == null) "근력운동의 강도를 골라 주세요." else null,
            )
        }
    }
}
