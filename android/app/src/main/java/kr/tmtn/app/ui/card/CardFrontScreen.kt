package kr.tmtn.app.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                TmtnQuietButton("오늘은 하기 어려워") { nav.navigate(Route.HOME) { popUpTo(Route.HOME) } }
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
