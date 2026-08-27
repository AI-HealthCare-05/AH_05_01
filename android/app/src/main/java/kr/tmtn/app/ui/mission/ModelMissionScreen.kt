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
import kr.tmtn.app.domain.ml.ModelRegistry
import kr.tmtn.app.domain.model.MissionType
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.asClock
import kr.tmtn.app.ui.nav.Route

/**
 * ▣ 모델 측정형 미션 화면 (MODEL_ACTIVE_TIME · MODEL_DISTANCE · MODEL_STAIR_COUNT)
 *
 * 자가 수행형과 다른 점 — 여기가 이 화면의 존재 이유다.
 *  1) 완료 버튼이 필수가 아니다. 목표에 닿으면 모델이 알아서 끝낸다.
 *  2) 일시정지가 필수가 아니다. 멈춰 있으면 모델이 시간을 세지 않는다.
 *     ("직접 일시정지" 는 사용자가 원할 때만 쓰는 보조 수단으로 남겨 둔다)
 *  3) 대신 측정 상세를 보여 준다 — 전체 경과 · 자동으로 쉰 시간 · 제외된 구간 · 측정 품질.
 *
 * 화면은 ActivitySnapshot 만 그린다. 어떤 모델이 붙어도 이 파일은 바뀌지 않는다.
 */
@Composable
fun ModelMissionScreen(today: TodayViewModel, nav: NavHostController) {
    val card = today.picked ?: run { nav.popBackStack(); return }

    val vm: MissionRunViewModel = viewModel(
        key = "model_${card.id}",
        factory = viewModelFactory { initializer { MissionRunViewModel(card) } },
    )

    LaunchedEffect(Unit) { if (vm.phase == RunPhase.Idle) vm.start() }

    LaunchedEffect(vm.phase) {
        if (vm.phase == RunPhase.Done) {
            today.complete(
                achieved = vm.achievedValue(),
                measuredByModel = true,
                fromPlaceholder = ModelRegistry.activityRecognizer.info.isPlaceholder,
            )
            nav.navigate(Route.COMPLETE) { popUpTo(Route.HOME) }
        }
    }

    val s = vm.snapshot

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("오늘의 행동", onBack = { nav.popBackStack() }, actionLabel = "중단", onAction = { nav.popBackStack() })

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(card.title, style = TmtnText.Headline, color = TmtnColor.OnSurface)

            val stateLabel = when {
                vm.phase == RunPhase.Saving -> "기록하는 중이에요"
                vm.phase == RunPhase.Paused -> "직접 멈춰 두었어요"
                s.moving -> when (card.type) {
                    MissionType.MODEL_DISTANCE -> "지금 이동을 기록하고 있어요"
                    MissionType.MODEL_STAIR_COUNT -> "계단 이동을 확인했어요"
                    else -> "움직임을 확인했어요"
                }
                else -> "멈춰 있어 기록을 잠시 쉬고 있어요"
            }
            StatusBadge(if (s.moving) BadgeState.Running else BadgeState.Paused, stateLabel)

            /* ── 큰 숫자 하나 ─────────────────────────────── */
            TmtnCardBox(padding = 20.dp) {
                val (big, sub) = when (card.type) {
                    MissionType.MODEL_DISTANCE ->
                        "%.2f km".format(s.distanceMeters / 1000.0) to "목표 %.2f km".format(card.targetMeters / 1000.0)
                    MissionType.MODEL_STAIR_COUNT ->
                        "${s.stairs} 계단" to "목표 ${card.targetStairs}계단"
                    else ->
                        s.activeSeconds.asClock() to "목표 ${card.targetSeconds.asClock()}"
                }
                Text(big, style = TmtnText.Metric, color = TmtnColor.OnSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(sub, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                MeterBar(vm.progress)
                Text(
                    "${(vm.progress * 100).toInt()}% · 목표를 다 채우면 완료 버튼 없이 기록돼요",
                    style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                )
            }

            /* ── 측정 상세 ────────────────────────────────── */
            TmtnCardBox {
                StatRow("전체 경과", s.elapsedSeconds.asClock())
                StatRow("자동으로 쉰 시간", s.restSeconds.asClock())
                if (card.type == MissionType.MODEL_DISTANCE || card.type == MissionType.MODEL_STAIR_COUNT) {
                    StatRow("유효 시간", s.activeSeconds.asClock())
                }
                StatRow("측정에서 제외된 구간", s.excludedSeconds.asClock())
                StatRow("측정 품질", s.quality.label)
            }

            val info = ModelRegistry.activityRecognizer.info
            if (info.isPlaceholder) {
                NoteBox(tone = NoteTone.Notice, title = "샘플 측정 중이에요", body = info.note)
            }

            NoteBox(body = "앱을 닫아도 알림에서 상태를 볼 수 있게 만들 예정이에요. 지금은 화면을 켜 둔 동안만 측정합니다.")

            when (vm.phase) {
                RunPhase.Paused -> {
                    TmtnFilledButton("다시 시작", onClick = { vm.resume() })
                    TmtnQuietButton("측정 끝내기") { vm.finishByUser() }
                }
                RunPhase.Saving -> TmtnFilledButton("기록하는 중…", enabled = false, onClick = { })
                else -> {
                    // 일시정지는 선택 기능이다. 멈춰 있으면 모델이 이미 시간을 세지 않는다.
                    TmtnTonalButton("직접 일시정지") { vm.pause() }
                    TmtnQuietButton("측정 끝내기") { vm.finishByUser() }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
