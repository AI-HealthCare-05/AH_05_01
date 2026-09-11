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

/** Figma A08 · 필수 입력 · 운동 정보 (node 136:235), 2/2단계 */
@Composable
fun A08ExerciseScreen(state: OnboardingState, scope: CoroutineScope, hasSensorPermissions: () -> Boolean) {
    val colors = LocalTmtnColors.current
    var weeklyCount by state.strengthWeeklyCount
    var intensity by state.strengthIntensity
    var lowMin by state.aerobicLowMinutes
    var modMin by state.aerobicModerateMinutes
    var highMin by state.aerobicHighMinutes

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "필수 입력", onBack = { state.step.value = OnboardingStep.A07_PROFILE })
        StepProgressHeader(2, 2, "운동 정보")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {


            Text("평소 운동량을 알려 주세요", style = TmtnType.headline, color = colors.onSurface)
            Text("대략이면 충분해요. 틈튼지수 계산에 씁니다.", style = TmtnType.body, color = colors.onSurfaceVariant)

            com.tmtn.app.ui.common.TmtnExerciseFields(
                weeklyCount, { weeklyCount = it }, intensity, { intensity = it },
                lowMin, { lowMin = it }, modMin, { modMin = it }, highMin, { highMin = it },
            )

            Spacer(modifier = Modifier.height(8.dp))
            TmtnPrimaryButton(
                text = "필수 정보 저장하기",
                onClick = { scope.launch { state.submitExerciseHabits(hasSensorPermissions()) } },
            )
        }
    }
}
