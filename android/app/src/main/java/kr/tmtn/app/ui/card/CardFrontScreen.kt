package kr.tmtn.app.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.ui.TmtnDate
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.nav.Route

/**
 * B06 — 확정한 카드의 앞면.
 * 여기서 미션 유형에 따라 갈라진다.
 *   모델 측정형 → 측정 안내(MissionIntro) → 모델 측정 화면
 *   자가 수행형 → 바로 자가 수행 화면
 */
@Composable
fun CardFrontScreen(today: TodayViewModel, nav: NavHostController) {
    val card = today.picked
    if (card == null) {
        EmptyPicked(nav)
        return
    }
    val done = today.isDoneToday()
    var showHardToday by remember { mutableStateOf(false) }
    // B12 · 대체 미션을 받은 직후에만 띄우는 알림
    var swapped by remember { mutableStateOf(false) }

    // 오늘 못 하겠다고 그냥 나가면 그 날은 **미완료**로 남는다.
    // 쉼은 직접 골라야만 기록되므로, 여기서 고를 기회를 준다.
    // (CLAUDE.md 가 정한 쉼 버튼 자리 중 하나가 이 화면 B06 이다.)
    if (showHardToday) {
        HardTodayDialog(
            restLeft = today.restLeftThisWeek(),
            canSwap = today.canSwapToday(),
            onSwap = {
                swapped = today.swapMission()
                showHardToday = false
            },
            onRest = {
                today.markRestToday()
                showHardToday = false
                nav.navigate(Route.HOME) { popUpTo(Route.HOME) }
            },
            onJustLeave = {
                showHardToday = false
                nav.navigate(Route.HOME) { popUpTo(Route.HOME) }
            },
            onDismiss = { showHardToday = false },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("오늘의 카드", onBack = { nav.popBackStack() })

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            TmtnCardBox(shape = TmtnShape.TodayCard, padding = 20.dp) {
                Row {
                    Text("오늘의 카드", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(TmtnDate.label(today.dateKey), style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                }

                Text(card.mission.fortune, style = TmtnText.Title, color = TmtnColor.OnSurface)

                HorizontalRule()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TmtnIcons.Walk, contentDescription = null, tint = TmtnColor.OnSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("오늘의 행동", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                        Text(card.title, style = TmtnText.Label, color = TmtnColor.OnSurface)
                    }
                }

                Text("오늘의 한 줄", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                Text(card.oneLine, style = TmtnText.Body, color = TmtnColor.OnSurface)

                Spacer(Modifier.height(4.dp))
                RewardChip(card.mission.rewardName, card.mission.rewardHint)

                Text(
                    "${card.mission.axis.accessibleText()} · ${card.mission.area}",
                    style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                )
            }

            // B12 · 대체 미션 적용 완료
            if (swapped) {
                NoteBox(
                    tone = NoteTone.Notice,
                    title = "다른 행동으로 바꿨어요",
                    body = "쌓을 재료는 그대로예요. 바꾸기는 하루에 한 번만 할 수 있어요.",
                )
            }

            if (card.mission.safetyNote.isNotBlank()) {
                NoteBox(title = "이렇게 해 주세요", body = card.mission.safetyNote)
            }

            if (done) {
                NoteBox(tone = NoteTone.Notice, title = "오늘은 이미 끝냈어요", body = "기록은 기록 탭에 남아 있어요.")
                TmtnTonalButton("기록 보러 가기") { nav.navigate(Route.RECORD) }
            } else {
                TmtnFilledButton(
                    text = "이 행동 시작하기",
                    onClick = {
                        val next = if (card.type.isModelMeasured) Route.MISSION_INTRO else Route.MISSION_SELF
                        nav.navigate(next)
                    },
                )
                TmtnQuietButton("오늘은 하기 어려워") { showHardToday = true }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
internal fun HorizontalRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TmtnColor.OutlineVariant),
    )
}

@Composable
private fun EmptyPicked(nav: NavHostController) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TmtnTopBar("오늘의 카드", onBack = { nav.popBackStack() })
        NoteBox(title = "아직 고른 카드가 없어요", body = "오늘의 카드를 먼저 골라 주세요.")
        TmtnFilledButton("카드 고르러 가기", onClick = { nav.navigate(Route.CARD_PICK) })
    }
}

/**
 * "오늘은 하기 어려워" 를 눌렀을 때.
 *
 * 그냥 내보내면 그 날은 미완료가 되어 연속 기록이 끊긴다.
 * 쉼은 **직접 고른 날만** 인정되므로, 나가기 전에 고를 기회를 준다.
 * 이번 주 몫을 다 썼으면 쉼을 권하지 않고 이유를 밝힌다.
 */
@Composable
private fun HardTodayDialog(
    restLeft: Int,
    canSwap: Boolean,
    onSwap: () -> Unit,
    onRest: () -> Unit,
    onJustLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TmtnColor.Background,
        shape = TmtnShape.Sheet,
        title = { Text("오늘은 어떻게 할까요?", style = TmtnText.Title, color = TmtnColor.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 쉼보다 바꾸기를 먼저 권한다. 오늘 하루를 살릴 수 있는 쪽이 먼저다.
                if (canSwap) {
                    TmtnTonalButton("다른 행동으로 바꾸기", onClick = onSwap)
                    Text(
                        "같은 재료를 쌓는 다른 행동을 드려요. 하루에 한 번만 바꿀 수 있어요.",
                        style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                    )
                } else {
                    Text(
                        "오늘은 이미 한 번 바꿨어요.",
                        style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                    )
                }

                if (restLeft > 0) {
                    TmtnOutlinedButton("오늘은 쉬어가기", onClick = onRest)
                    Text(
                        "쉼으로 표시하면 연속 기록이 끊기지 않아요. 이번 주에 ${restLeft}번 남았어요.",
                        style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                    )
                } else {
                    Text(
                        "이번 주 쉼은 다 썼어요. 그냥 나가면 오늘은 기록이 비어요.",
                        style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TmtnQuietButton("그냥 나가기", fillWidth = false, onClick = onJustLeave)
        },
    )
}
