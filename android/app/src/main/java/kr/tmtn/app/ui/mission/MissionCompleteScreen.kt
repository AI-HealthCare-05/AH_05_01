package kr.tmtn.app.ui.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.ui.TmtnDate
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.nav.Route

/** B07 / C 완료 — 미달성이어도 탓하지 않는다. 벌점·차감 표현을 쓰지 않는다. */
@Composable
fun MissionCompleteScreen(today: TodayViewModel, nav: NavHostController) {
    val record = today.lastCompleted ?: run {
        nav.navigate(Route.HOME) { popUpTo(Route.HOME) }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("오늘 완료", onBack = { nav.navigate(Route.HOME) { popUpTo(Route.HOME) } })

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            TmtnCardBox(shape = TmtnShape.TodayCard, padding = 20.dp) {
                Text(
                    "오늘 ${record.achieved}${record.unit} 해냈어요",
                    style = TmtnText.Headline,
                    color = TmtnColor.OnSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${record.title} · ${record.completedAtLabel} 완료",
                    style = TmtnText.Caption,
                    color = TmtnColor.OnSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(TmtnColor.Reward),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(TmtnIcons.Check, contentDescription = null, tint = TmtnColor.OnReward, modifier = Modifier.size(26.dp))
                            Text(TmtnDate.label(record.date).removeSuffix("."), style = TmtnText.Caption, color = TmtnColor.OnReward)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TmtnIcons.Check, contentDescription = null, tint = TmtnColor.Secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("틈튼카드첩에 저장했어요", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
                }

                RewardChip(record.rewardName, record.rewardHint)

                Text(
                    if (record.measuredByModel) "천천히 움직여서 다 채웠네. 오늘은 이걸로 충분해."
                    else "잘했어. 내일도 이만큼이면 충분해.",
                    style = TmtnText.Body, color = TmtnColor.OnSurface,
                )
            }

            if (record.fromPlaceholderModel) {
                NoteBox(
                    tone = NoteTone.Notice,
                    title = "이 기록은 샘플 측정값이에요",
                    body = "실제 모델이 연결되면 같은 화면에서 진짜 측정값으로 바뀝니다.",
                )
            }

            TmtnFilledButton("댐 보러 가기", onClick = { nav.navigate(Route.DAM) { popUpTo(Route.HOME) } })
            TmtnTonalButton("홈으로") { nav.navigate(Route.HOME) { popUpTo(Route.HOME) } }
            Spacer(Modifier.height(40.dp))
        }
    }
}
