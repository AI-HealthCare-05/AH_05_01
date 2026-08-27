package kr.tmtn.app.ui.mission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.domain.model.MissionType
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.asClock
import kr.tmtn.app.ui.nav.Route

/**
 * ▣ 자가 수행형 미션 화면 (SELF_CHECK · SELF_TIMER)
 *
 * 모델을 쓰지 않는다. 시간은 앱이 그냥 세고, 완료는 **사용자가 눌러야** 기록된다.
 * 그래서 이 화면에는 일시정지 버튼이 있다 — 사용자가 멈추지 않으면 시간이 계속 흐르기 때문이다.
 *
 * 모델을 쓰는 미션은 ModelMissionScreen 으로 간다.
 */
@Composable
fun SelfMissionScreen(today: TodayViewModel, nav: NavHostController) {
    val card = today.picked ?: run { nav.popBackStack(); return }

    // 모델형 카드인데 이 화면으로 왔다면 = 센서를 못 쓰거나 사용자가 직접 체크를 골랐다는 뜻.
    val manual = card.type.isModelMeasured
    val vm: MissionRunViewModel = viewModel(
        key = "self_${card.id}",
        factory = viewModelFactory { initializer { MissionRunViewModel(card, forceManual = manual) } },
    )

    LaunchedEffect(vm.phase) {
        if (vm.phase == RunPhase.Done) {
            today.complete(
                achieved = vm.achievedValue(),
                measuredByModel = false,
                fromPlaceholder = false,
            )
            nav.navigate(Route.COMPLETE) { popUpTo(Route.HOME) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("오늘의 행동", onBack = { nav.popBackStack() }, actionLabel = "중단", onAction = { nav.popBackStack() })

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text(card.title, style = TmtnText.Headline, color = TmtnColor.OnSurface)
            RewardChip(card.mission.rewardName, card.mission.rewardHint)

            if (manual) {
                NoteBox(
                    tone = NoteTone.Notice,
                    title = "직접 체크로 진행 중이에요",
                    body = "원래 자동으로 측정하는 미션이지만, 지금은 직접 확인해서 기록합니다. " +
                        "미션 내용과 받는 재료는 그대로예요.",
                )
            }

            if (vm.type == MissionType.SELF_TIMER) {
                TimerPanel(vm, card.targetSeconds)
            }

            TmtnCardBox {
                Text("완료 기준", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Text("· ${card.mission.completeRule}", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
                if (card.mission.safetyNote.isNotBlank()) {
                    Text("· ${card.mission.safetyNote}", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
                }
            }

            NoteBox(
                body = "이 미션은 자동으로 완료되지 않습니다. 직접 확인해야 기록됩니다.",
            )

            when {
                vm.type == MissionType.SELF_CHECK ->
                    TmtnFilledButton("했어요 · 완료하기", onClick = { vm.finishByUser() })

                vm.phase == RunPhase.Idle ->
                    TmtnFilledButton("시작하기", onClick = { vm.start() })

                vm.phase == RunPhase.Running -> {
                    TmtnTonalButton("일시정지") { vm.pause() }
                    TmtnFilledButton(
                        text = "완료하기",
                        enabled = vm.reachedGoal,
                        disabledReason = "목표 ${card.targetNumber}${card.mission.unit}을 다 채우면 완료할 수 있어요. " +
                            "${((card.targetSeconds - vm.selfSeconds).coerceAtLeast(0)).asClock()} 남았습니다.",
                        onClick = { vm.finishByUser() },
                    )
                }

                vm.phase == RunPhase.Paused -> {
                    TmtnFilledButton("이어서 하기", onClick = { vm.resume() })
                    TmtnOutlinedButton("오늘은 여기까지 할래") { vm.finishByUser() }
                }

                else -> TmtnFilledButton("기록하는 중…", enabled = false, onClick = { })
            }

            TmtnQuietButton("오늘은 하기 어려워") { nav.popBackStack() }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TimerPanel(vm: MissionRunViewModel, targetSeconds: Int) {
    val badge = when (vm.phase) {
        RunPhase.Idle -> BadgeState.NotStarted to "아직 시작하지 않았어요"
        RunPhase.Running -> BadgeState.Running to "시간이 흐르고 있어요"
        RunPhase.Paused -> BadgeState.Paused to "잠시 멈춰 있어요"
        else -> BadgeState.Done to "기록하고 있어요"
    }

    TmtnCardBox(padding = 20.dp) {
        StatusBadge(badge.first, badge.second)
        Text(
            vm.selfSeconds.asClock(),
            style = TmtnText.Metric,
            color = TmtnColor.OnSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            "목표 ${targetSeconds / 60}분 · 남은 시간 ${(targetSeconds - vm.selfSeconds).coerceAtLeast(0).asClock()}",
            style = TmtnText.Caption,
            color = TmtnColor.OnSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        MeterBar(vm.progress)
        Text(
            "${(vm.progress * 100).toInt()}% · 목표를 다 채우면 완료할 수 있어요",
            style = TmtnText.Caption,
            color = TmtnColor.OnSurfaceVariant,
        )
    }
}
