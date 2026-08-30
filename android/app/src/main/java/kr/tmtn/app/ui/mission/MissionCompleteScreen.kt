package kr.tmtn.app.ui.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                        // 완료 표시는 먹색이다. 주황은 "오늘" 에만 쓴다 —
                        // 끝낸 일까지 주황으로 칠하면 화면에 주황이 넘친다.
                        Modifier.size(76.dp).clip(CircleShape).background(TmtnColor.Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(TmtnIcons.Check, contentDescription = null, tint = TmtnColor.OnPrimary, modifier = Modifier.size(26.dp))
                            Text(TmtnDate.label(record.date).removeSuffix("."), style = TmtnText.Caption, color = TmtnColor.OnPrimary)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TmtnIcons.Check, contentDescription = null, tint = TmtnColor.Primary, modifier = Modifier.size(18.dp))
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

            // [DEMO-MODEL] isPlaceholder = false 가 되면 저절로 사라진다. 지우지 말 것.
            if (record.fromPlaceholderModel) {
                NoteBox(
                    tone = NoteTone.Notice,
                    title = "이 기록은 샘플 측정값이에요",
                    body = "실제 모델이 연결되면 같은 화면에서 진짜 측정값으로 바뀝니다.",
                )
            }

            FitAndNote(today)

            TmtnFilledButton("댐 보러 가기", onClick = { nav.navigate(Route.DAM) { popUpTo(Route.HOME) } })
            TmtnTonalButton("홈으로") { nav.navigate(Route.HOME) { popUpTo(Route.HOME) } }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * C08 적합도 피드백 + C20 한 줄 회고.
 *
 * **둘 다 선택이다.** 기록은 이미 남았으므로 안 쓰고 나가도 손해가 없다.
 * 그래서 "저장" 을 강요하지 않고, 답한 것만 조용히 담아 둔다.
 *
 * 완료의 기쁨이 먼저다 — 이 묶음은 보상 카드 **아래**에 둔다.
 * 끝내자마자 설문부터 들이밀면 잘한 일이 일 처리가 된다.
 */
@Composable
private fun FitAndNote(today: TodayViewModel) {
    var note by remember { mutableStateOf(today.reflection) }
    var fit by remember { mutableStateOf(today.fit) }
    var saved by remember { mutableStateOf(today.reflection.isNotBlank() || today.fit.isNotBlank()) }

    TmtnCardBox {
        Text("오늘 이 행동, 어땠어요?", style = TmtnText.Label, color = TmtnColor.OnSurface)
        Text(
            "다음 카드 난이도를 맞추는 데만 써요. 안 골라도 괜찮아요.",
            style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("쉬웠어요", "알맞았어요", "힘들었어요").forEach { label ->
                val selected = fit == label
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = TmtnTarget.Min)
                        .clip(TmtnShape.Chip)
                        .background(if (selected) TmtnColor.Primary else TmtnColor.Background)
                        .clickable {
                            fit = if (selected) "" else label
                            today.saveReflection(note, fit)
                            saved = true
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = TmtnText.Caption,
                        textAlign = TextAlign.Center,
                        color = if (selected) TmtnColor.OnPrimary else TmtnColor.OnSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("한 줄 남기기 (선택)", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it.take(60)
                today.saveReflection(note, fit)
                saved = true
            },
            placeholder = { Text("오늘 어땠는지 한 줄", style = TmtnText.Body) },
            textStyle = TmtnText.Body,
            shape = TmtnShape.Input,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TmtnColor.Background,
                unfocusedContainerColor = TmtnColor.Background,
                focusedBorderColor = TmtnColor.OnSurface,
                unfocusedBorderColor = TmtnColor.Outline,
                cursorColor = TmtnColor.OnSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (saved) {
            Text("담아 뒀어요", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
        }
    }
}
