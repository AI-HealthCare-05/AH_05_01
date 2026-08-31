package kr.tmtn.app.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import kr.tmtn.app.R
import androidx.navigation.NavHostController
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.ui.TmtnDate
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.nav.Route

/**
 * B03~B05 — 뒷면 카드 세 장 중 한 장을 고른다.
 *
 * 규칙: 고르기 전에는 카드 내용을 보여 주지 않는다. 하루에 한 번만 고를 수 있고,
 * 확정하면 고르지 않은 두 장은 공개하지 않는다.
 */
@Composable
fun CardPickScreen(today: TodayViewModel, nav: NavHostController) {
    var selected by remember { mutableStateOf(-1) }
    var confirming by remember { mutableStateOf(false) }
    val cards = today.cards

    // B09 · 덱을 못 불러왔을 때. 빈 화면을 그냥 두면 사용자는 앱이 멈춘 줄 안다.
    if (cards.isEmpty()) {
        DeckUnavailable(today, nav)
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        TmtnTopBar("오늘의 카드", onBack = { nav.popBackStack() })

        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(TmtnDate.weekdayLabel(today.dateKey), style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
            Text("마음 가는 카드로\n한 장만 골라 줘.", style = TmtnText.Headline, color = TmtnColor.OnSurface)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cards.forEachIndexed { i, _ ->
                    CardBack(
                        index = i,
                        selected = selected == i,
                        modifier = Modifier.weight(1f),
                        onClick = { selected = i },
                    )
                }
            }

            if (selected >= 0) {
                StatusBadge(BadgeState.Info, "${listOf("첫", "두", "세")[selected]} 번째를 골랐어요")
            }

            NoteBox(
                body = "고르기 전에는 어떤 카드인지 보이지 않습니다. 하루에 한 번만 고를 수 있고, " +
                    "고른 카드는 앱을 다시 열어도 그대로 남습니다.",
            )

            TmtnFilledButton(
                text = "이 카드로 확정",
                enabled = selected >= 0,
                disabledReason = "카드를 한 장 고르면 확정할 수 있어요.",
                onClick = { confirming = true },
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirming && selected >= 0) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = TmtnColor.Surface,
            shape = TmtnShape.Card,
            title = { Text("이 카드로 확정할까요?", style = TmtnText.Title, color = TmtnColor.OnSurface) },
            text = {
                // 확정 뒤에도 행동은 하루 한 번 바꿀 수 있다(B11).
                // "바꿀 수 없다" 고만 적어 두면 사실과 달라진다.
                Text(
                    "고르지 않은 두 장은 공개되지 않아요. " +
                        "카드는 오늘 하루 이걸로 두되, 행동이 버거우면 한 번은 바꿀 수 있어요.",
                    style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    today.confirmPick(cards[selected])
                    nav.navigate(Route.CARD_FRONT) { popUpTo(Route.CARD_PICK) { inclusive = true } }
                }) { Text("확정하기", style = TmtnText.Label, color = TmtnColor.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text("다시 고르기", style = TmtnText.Label, color = TmtnColor.OnSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun CardBack(index: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // 뒷면은 브랜드 카드 그림이다. 고른 카드는 **테두리와 확대**로 표시한다 —
    // 그림 위에 색을 덮으면 카드가 안 보인다.
    Box(
        modifier
            .height(160.dp)
            .clip(TmtnShape.TodayCard)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) TmtnColor.Primary else TmtnColor.OutlineVariant,
                shape = TmtnShape.TodayCard,
            )
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(R.drawable.card_back),
            contentDescription = "카드 뒷면",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) {
            // 고른 카드에만 얇은 먹색 막을 덮어 나머지와 갈라 준다.
            Box(Modifier.fillMaxSize().background(TmtnColor.Scrim.copy(alpha = 0.28f)))
            // 배지는 **위 왼쪽 귀**에 둔다. 카드 뒷면 그림 한가운데에 이미
            // TMTN 표식이 있어서, 가운데에 두면 두 글자가 겹쳐 둘 다 안 읽힌다.
            Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopStart) {
                Text(
                    "고름",
                    style = TmtnText.Label,
                    color = TmtnColor.OnPrimary,
                    modifier = Modifier
                        .clip(TmtnShape.Chip)
                        .background(TmtnColor.Primary)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}


/**
 * B09 · 카드 덱 오류.
 *
 * 카드를 못 불러오면 오늘 할 수 있는 게 없다. 다시 시도할 길을 주고,
 * 그래도 안 되면 다른 곳으로 갈 수 있게 둔다 — 이 화면에 가두지 않는다.
 */
@Composable
private fun DeckUnavailable(today: TodayViewModel, nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("오늘의 카드", onBack = { nav.popBackStack() })
        TmtnMessageView(
            title = "카드를 불러오지 못했어요",
            body = "잠깐 뒤에 다시 눌러 주세요.",
            primaryLabel = "다시 불러오기",
            onPrimary = { today.refresh() },
            secondaryLabel = "홈으로",
            onSecondary = { nav.navigate(Route.HOME) { popUpTo(Route.HOME) } },
        )
    }
}
