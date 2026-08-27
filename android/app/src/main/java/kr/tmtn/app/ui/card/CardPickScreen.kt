package kr.tmtn.app.ui.card

import androidx.compose.foundation.background
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
                StatusBadge(BadgeState.Rest, "${listOf("첫", "두", "세")[selected]} 번째를 골랐어요")
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
                Text(
                    "확정하면 오늘은 카드를 바꿀 수 없습니다. 고르지 않은 두 장은 공개되지 않습니다.",
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
    Column(
        modifier
            .height(160.dp)
            .clip(TmtnShape.TodayCard)
            .background(if (selected) TmtnColor.Primary else TmtnColor.SecondaryContainer)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) TmtnColor.Primary else TmtnColor.OutlineVariant,
                shape = TmtnShape.TodayCard,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) TmtnColor.Reward else TmtnColor.Surface),
        )
        Text(
            "심볼",
            style = TmtnText.Caption,
            textAlign = TextAlign.Center,
            color = if (selected) TmtnColor.OnPrimary else TmtnColor.OnSurfaceVariant,
        )
        Text(
            "뒷면",
            style = TmtnText.Label,
            color = if (selected) TmtnColor.OnPrimary else TmtnColor.OnSurface,
        )
    }
}
