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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                TmtnEqualProgressBar(totalSteps = 2, currentStep = 2)
                Spacer(modifier = Modifier.height(8.dp))
                Text("2 / 2단계 · 모두 필요한 값이에요", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            Text("운동은 어떻게\n하고 계세요?", style = TmtnType.headline, color = colors.onSurface)
            Text("대략이면 충분해요. 틈튼지수 계산에 씁니다.", style = TmtnType.body, color = colors.onSurfaceVariant)

            // 근력운동
            SectionDot(title = "근력운동", hint = "주 횟수", dotColor = colors.wood)
            Text("팔굽혀펴기 · 스쿼트 · 기구 운동 등", style = TmtnType.caption, color = colors.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "안 함", 1 to "주 1회", 2 to "주 2회", 3 to "주 3회").forEach { (count, label) ->
                    TmtnChip(
                        text = label, selected = weeklyCount == count, onClick = { weeklyCount = count },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(4 to "주 4회", 5 to "주 5회+").forEach { (count, label) ->
                    TmtnChip(text = label, selected = weeklyCount == count, onClick = { weeklyCount = count })
                }
            }

            if (weeklyCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("보통 어느 정도 힘들게 하세요?", style = TmtnType.body, color = colors.onSurface)
                    Text("강도", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TmtnIntensityCard(
                        "가볍게", "15회+ 가능", intensity == "LIGHT", { intensity = "LIGHT" },
                        modifier = Modifier.weight(1f),
                    )
                    TmtnIntensityCard(
                        "적당히", "10~12회면 힘듦", intensity == "MODERATE", { intensity = "MODERATE" },
                        modifier = Modifier.weight(1f),
                    )
                    TmtnIntensityCard(
                        "힘들게", "8회면 한계", intensity == "HARD", { intensity = "HARD" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 유산소 운동
            SectionDot(title = "유산소 운동", hint = "강도별 주당 시간", dotColor = colors.secondary)

            AerobicCard(
                bandText = "저강도", bandColor = colors.secondaryContainer,
                description = "숨이 차지 않아요 · 산책, 스트레칭",
                value = lowMin, onDecrement = { if (lowMin > 0) lowMin -= 10 }, onIncrement = { lowMin += 10 },
            )
            AerobicCard(
                bandText = "중강도", bandColor = colors.woodContainer,
                description = "숨이 조금 차요 · 빠르게 걷기, 자전거",
                value = modMin, onDecrement = { if (modMin > 0) modMin -= 10 }, onIncrement = { modMin += 10 },
            )
            AerobicCard(
                bandText = "고강도", bandColor = colors.rewardContainer,
                description = "숨이 많이 차요 · 달리기, 등산",
                value = highMin, onDecrement = { if (highMin > 0) highMin -= 10 }, onIncrement = { highMin += 10 },
                note = "0분도 괜찮아요. 하지 않는다면 0으로 두세요.",
            )

            Text(
                "세 가지 강도를 모두 채워 주세요.\n나중에 마이에서 고칠 수 있어요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))
            TmtnPrimaryButton(
                text = "완료하고 시작하기",
                onClick = { scope.launch { state.submitExerciseHabits(hasSensorPermissions()) } },
            )
        }
    }
}

@Composable
private fun SectionDot(title: String, hint: String, dotColor: androidx.compose.ui.graphics.Color) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = TmtnType.label, color = colors.onSurface)
        Spacer(modifier = Modifier.weight(1f))
        Text(hint, style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun AerobicCard(
    bandText: String,
    bandColor: androidx.compose.ui.graphics.Color,
    description: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    note: String? = null,
) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TmtnIntensityBand(text = bandText, containerColor = bandColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(description, style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        TmtnStepper(value = value, unit = "분", onDecrement = onDecrement, onIncrement = onIncrement)
        note?.let { Text(it, style = TmtnType.caption, color = colors.onSurfaceVariant) }
    }
}
