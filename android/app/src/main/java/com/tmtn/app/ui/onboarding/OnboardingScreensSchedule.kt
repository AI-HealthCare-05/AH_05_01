package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalContext
import com.tmtn.app.notification.NotificationScheduler
import androidx.compose.ui.res.painterResource
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
            // ⚠️ 2026-09-06 QA(P2) 반영: 여기만 "에셋 준비 중" 플레이스홀더가 화면 1/3을
            // 차지했음(다른 화면엔 비버가 이미 들어가 있음) - "휴식" 포즈가 잠자는 시간
            // 물어보는 이 화면 분위기와 제일 잘 맞아서 사용.
            Box(
                modifier = Modifier
                    .height(240.dp)
                    .fillMaxWidth()
                    .background(colors.secondaryContainer, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.secondary, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.tmtn.app.R.drawable.beaver_rest),
                    contentDescription = "편안하게 쉬고 있는 비버",
                    modifier = Modifier.height(208.dp),
                )
            }

            Text(
                "하루 일정을 알려 주세요", style = TmtnType.headline,
                color = colors.onSurface, textAlign = TextAlign.Center,
            )
            Text(
                // ⚠️ 2026-09-06 QA(P2) 반영: "나중에 하기"(우상단)/"건너뛰고 바로 시작하기"
                // (하단) 버튼이 이미 명확한데, 본문에 "건너뛰어도 바로 시작할 수 있어"까지
                // 또 설명해서 같은 화면에 건너뛰기 안내가 3번 나왔음 - 본문에서는 뺌.
                "그 시간에 맞춰서 알림을 보낼게.",
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
    val context = LocalContext.current
    var wakeTime by state.wakeTime
    var lunchTime by state.lunchTime
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
            Text("몇 시에 자고, 점심은 언제, 일어나?", style = TmtnType.headline, color = colors.onSurface)
            Text(
                // ⚠️ 2026-09-06 QA(P2) 반영: "마이"는 CLAUDE.md가 금지한 표현 - "내 정보"가 맞음.
                "이 시간에 맞춰 알림을 보낼게. 나중에 내 정보에서 고칠 수 있어.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            Text("일어나는 시각", style = TmtnType.label, color = colors.onSurface)
            TimeWheelPicker(timeString = wakeTime, onTimeChange = { wakeTime = it })

            Text("점심 시각", style = TmtnType.label, color = colors.onSurface)
            TimeWheelPicker(timeString = lunchTime, onTimeChange = { lunchTime = it })

            Text("잠자리에 드는 시각", style = TmtnType.label, color = colors.onSurface)
            TimeWheelPicker(timeString = sleepTime, onTimeChange = { sleepTime = it })

            // 알림 미리보기 - 계산 규칙을 그대로 보여줌 (기상+2시간, 점심 직접입력, 취침-2시간)
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
                        "점심 뒤 · ${formatTimeKorean(shiftTime(lunchTime, 1))} · 점심 1시간 후\n" +
                        "자기 전 · ${formatTimeKorean(shiftTime(sleepTime, -2))} · 잠들기 2시간 전",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TmtnPrimaryButton(
                text = "이 시간으로 맞추기",
                onClick = {
                    scope.launch {
                        state.submitSchedule()
                        // ⚠️ 2026-09-08 반영: 서버 저장만으로는 실제 알림이 안 옴(로컬
                        // 스케줄링 방식이라) - 온보딩에서 처음 설정할 때도 기기에 바로
                        // 예약해둠(동의가 아직 없으면 Worker 실행 시점에 조용히 스킵됨).
                        // 서버가 계산해서 돌려준 slots를 그대로 씀(클라이언트가 계산식을
                        // 따로 다시 계산하면 서버와 어긋날 위험이 있음).
                        state.scheduleNotificationSetting.value?.slots?.let { newSlots ->
                            NotificationScheduler.scheduleAllExact(context.applicationContext, newSlots)
                        }
                    }
                },
            )
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
