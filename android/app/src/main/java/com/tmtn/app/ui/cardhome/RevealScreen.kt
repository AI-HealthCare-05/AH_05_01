package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// CSV 오행 -> 재료 이름 매핑 (댐 화면과 동일한 기준). 같은 패키지 다른 화면(B07 등)에서도 재사용.
val MATERIAL_NAMES = mapOf(
    "WOOD" to ("나뭇가지" to "움직임·유산소"),
    "FIRE" to ("받침돌" to "근력"),
    "EARTH" to ("다짐흙" to "생활리듬"),
    "METAL" to ("새잎" to "식사·기록"),
    "WATER" to ("물길" to "수분"),
)

/**
 * 실제 재료 이미지(2026-09-01 팀장님이 전달한 "재료 이미지(원본)" 자산 기준).
 * 나머지 화면(G01/G02/G05/G06, D03, B07, F09 등)에서도 이 컴포저블 하나만 재사용.
 */
@Composable
fun MaterialIcon(element: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val drawableId = when (element) {
        "WOOD" -> com.tmtn.app.R.drawable.material_wood
        "FIRE" -> com.tmtn.app.R.drawable.material_fire
        "EARTH" -> com.tmtn.app.R.drawable.material_earth
        "METAL" -> com.tmtn.app.R.drawable.material_metal
        "WATER" -> com.tmtn.app.R.drawable.material_water
        else -> null
    }
    if (drawableId != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = drawableId),
            contentDescription = MATERIAL_NAMES[element]?.first,
            modifier = modifier.size(size),
        )
    } else {
        // 알 수 없는 오행값 방어 - 예전처럼 빈 원이라도 표시
        androidx.compose.foundation.layout.Box(
            modifier = modifier.size(size).background(LocalTmtnColors.current.woodContainer, androidx.compose.foundation.shape.CircleShape),
        )
    }
}

// Figma B06 카드는 앱 전역 테마(검정/주황)와 별개로 항상 이 초록 카드 디자인을 씀.
private val NoteCardBg = Color(0xFF0C3B2E)
private val NoteCream = Color(0xFFFFF8ED)
private val NoteGold = Color(0xFFFFBA00)
private val NoteStampColor = Color(0xFFBB8A52)

/** Figma B06 · 카드 공개(winner) — "오늘의 틈 노트" 디자인 */
@Composable
fun RevealScreen(
    state: CardHomeState,
    scope: CoroutineScope,
    onStartAction: () -> Unit,
) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            NoteCard(card)
        }

        // 하단 CTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ⚠️ "오늘 카드 다시 보기"로 완료/중단된 미션을 다시 열었을 때도 이 화면 자체는
            // 그대로 재사용됨. isFinished를 두 버튼에 같이 써서: "이 행동 시작하기"는 완전히
            // 숨기고("어차피 onStartAction이 다시 완료 화면으로 돌려보내니 눌러봤자 의미
            // 없음), "오늘은 쉬어가기"도 숨김(이미 끝난 하루에 쉼까지 쓰면 이번 주 쉼 횟수가
            // 잘못 깎임). "추천 이유 보기"만 남겨서 왜 이 카드가 나왔는지는 계속 볼 수 있게 함.
            //
            // ⚠️ 2026-09-04 반영: 진행 중(ACTIVE/PAUSED)인 미션 화면에서 뒤로가기로 여기
            // 돌아왔을 때 "이 행동 시작하기"가 그대로 보여서 마치 새로 시작하는 것처럼
            // 헷갈렸음. 이제 진행 중이면 "진행 중인 미션 확인"으로 문구만 바꿔서 보여줌 -
            // onStartAction은 이미 stepForRevealedCard()로 진행 상태에 맞는 화면(타이머
            // 진행/일시정지 등)으로 정확히 보내주므로 그대로 재사용. "쉬어가기"는 진행
            // 중인 미션 도중에 쓸 수 있는 게 아니라서 계속 숨김.
            val isFinished = card.state == "COMPLETED" || card.state == "SKIPPED"
            val isInProgress = card.state == "ACTIVE" || card.state == "PAUSED"
            if (!isFinished) {
                TmtnPrimaryButton(
                    text = if (isInProgress) "진행 중인 미션 확인" else "이 행동 시작하기",
                    onClick = onStartAction,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { state.step.value = CardHomeStep.REASON_DETAIL }) {
                    Text("추천 이유 보기", style = TmtnType.label, color = colors.onSurfaceVariant)
                }
                if (!isFinished && !isInProgress) {
                    Text(
                        "·", style = TmtnType.label, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    TextButton(onClick = { scope.launch { state.openRestDaySheet() } }) {
                        Text("오늘은 쉬어가기", style = TmtnType.label, color = colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(card: CardRevealResponse) {
    val material = MATERIAL_NAMES[card.five_element]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NoteCardBg, RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 머리 · 날짜/재료
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("오늘의 틈", style = TmtnType.bodyLarge, color = NoteCream)
                Text(LocalDate.now().toKoreanDateLabel(), style = TmtnType.caption, color = NoteCream.copy(alpha = 0.76f))
                if (material != null) {
                    Text(
                        "${material.first} · ${card.domain ?: material.second}",
                        style = TmtnType.caption, color = NoteCream.copy(alpha = 0.50f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(NoteStampColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                MaterialIcon(element = card.five_element, size = 40.dp)
            }
        }

        // 오늘의 운세
        if (card.fortune_text != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("오늘의 운세", style = TmtnType.caption, color = NoteCream.copy(alpha = 0.56f))
                Text(card.fortune_text, style = TmtnType.title, color = NoteCream)
            }
        }

        // 구분선 · TMTN
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(NoteCream.copy(alpha = 0.22f)))
            Text("TMTN", style = TmtnType.caption, color = NoteGold)
            Box(modifier = Modifier.weight(1f).height(1.dp).background(NoteCream.copy(alpha = 0.22f)))
        }

        // 행운 정보
        Column {
            LuckyRow(label = "행운의 행동", value = card.title)
            if (card.lucky_location != null) {
                LuckyRow(label = "행운의 위치", value = card.lucky_location)
            }
            LuckyRow(label = "행운의 숫자", value = null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${card.target_value}", style = TmtnType.title, color = NoteGold)
                    Text(card.unit, style = TmtnType.body, color = NoteCream)
                }
            }
        }

        // 오늘의 한 줄
        if (card.line_text != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NoteCream, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("오늘의 한 줄", style = TmtnType.caption, color = NoteStampColor)
                Text(card.line_text, style = TmtnType.label, color = NoteCardBg)
            }
        }
    }
}

@Composable
private fun LuckyRow(label: String, value: String?, valueContent: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TmtnType.caption, color = NoteCream.copy(alpha = 0.56f))
        if (valueContent != null) {
            valueContent()
        } else if (value != null) {
            Text(value, style = TmtnType.body, color = NoteCream)
        }
    }
}
