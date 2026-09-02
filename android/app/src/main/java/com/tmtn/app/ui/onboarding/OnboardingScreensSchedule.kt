package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Figma A09 · 선택 온보딩 안내 (node 100:161) */
@Composable
fun A09ScheduleIntroScreen(state: OnboardingState) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(
            title = "선택 입력",
            onBack = { state.step.value = OnboardingStep.A08_EXERCISE },
            trailing = {
                TextButton(onClick = { state.skipSchedule() }) {
                    Text("나중에 하기", style = TmtnType.label, color = colors.primary)
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 마스코트 이미지 자리 (아직 에셋 없음, Figma 원본도 점선 슬롯)
            Box(
                modifier = Modifier
                    .height(240.dp)
                    .fillMaxWidth()
                    .background(colors.secondaryContainer, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.secondary, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "마스코트 이미지\n(에셋 준비 중)",
                    style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }

            Text(
                "자는 시간이랑\n일어나는 시간만 알려줘", style = TmtnType.headline,
                color = colors.onSurface, textAlign = TextAlign.Center,
            )
            Text(
                "그 시간에 맞춰서 알림을 보낼게. 건너뛰어도 바로 시작할 수 있어.",
                style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            )

            TmtnPrimaryButton(text = "생활 패턴 알려주기", onClick = { state.step.value = OnboardingStep.A10_SCHEDULE })
            TmtnTextButton(text = "건너뛰고 바로 시작하기", onClick = { state.skipSchedule() })
        }
    }
}

/** Figma A10 · 선택 온보딩 · 생활 패턴 (node 100:196) */
@Composable
fun A10ScheduleScreen(state: OnboardingState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    var wakeTime by state.wakeTime
    var sleepTime by state.sleepTime

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(
            title = "선택 입력",
            onBack = { state.step.value = OnboardingStep.A09_SCHEDULE_INTRO },
            trailing = {
                TextButton(onClick = { state.skipSchedule() }) {
                    Text("나중에 하기", style = TmtnType.label, color = colors.primary)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("몇 시에 자고 일어나?", style = TmtnType.headline, color = colors.onSurface)
            Text(
                "이 시간에 맞춰 알림을 보낼게. 나중에 마이에서 고칠 수 있어.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            Text("일어나는 시각", style = TmtnType.label, color = colors.onSurface)
            TimeWheelPicker(timeString = wakeTime, onTimeChange = { wakeTime = it })

            Text("잠자리에 드는 시각", style = TmtnType.label, color = colors.onSurface)
            TimeWheelPicker(timeString = sleepTime, onTimeChange = { sleepTime = it })

            // 알림 미리보기 - 실제 계산된 시각을 그대로 보여줌 (기상+6시간, 취침-1시간)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("이렇게 알림이 갑니다", style = TmtnType.label, color = colors.onSurface)
                Text(
                    "아침 준비 · ${formatTimeKorean(wakeTime)} · 일어난 직후\n" +
                        "점심 뒤 · ${formatTimeKorean(shiftTime(wakeTime, 6))} · 기상 6시간 뒤\n" +
                        "자기 전 · ${formatTimeKorean(shiftTime(sleepTime, -1))} · 잠들기 1시간 전",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TmtnPrimaryButton(text = "이 시간으로 맞추기", onClick = { scope.launch { state.submitSchedule() } })
            TmtnTextButton(text = "건너뛰고 바로 시작하기", onClick = { state.skipSchedule() })
        }
    }
}

/** "HH:MM" -> "오전/오후 h:mm" 표시용. */
private fun formatTimeKorean(time: String): String {
    val parts = time.split(":")
    val hour24 = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val amPm = if (hour24 < 12) "오전" else "오후"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "$amPm $hour12:${"%02d".format(minute)}"
}

/** "HH:MM"에서 hours만큼 이동(자정 넘김 자동 처리). 알림 미리보기 계산용. */
private fun shiftTime(time: String, hours: Int): String {
    val parts = time.split(":")
    val hour24 = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val shifted = ((hour24 + hours) % 24 + 24) % 24
    return "%02d:%02d".format(shifted, minute)
}
