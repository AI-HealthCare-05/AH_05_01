package kr.tmtn.app.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.tmtn.app.designsystem.MaterialIcon
import kr.tmtn.app.designsystem.TmtnCardArt
import kr.tmtn.app.domain.model.MissionCard
import kr.tmtn.app.ui.TmtnDate

/**
 * B06 카드 앞면 — 브랜드 시안 **04 틈 노트**를 Compose 로 옮긴 것.
 *
 * 읽는 순서를 시안 그대로 지킨다:
 * **오늘의 운세 → 행운의 행동 → 위치 → 숫자 → 오늘의 한 줄.**
 * 재료는 오른쪽 위 스탬프 한 곳에서만 바뀌므로 카드 200장에 그대로 확장된다.
 *
 * 색은 `TmtnCardArt` 에서만 가져온다 — 화면 UI 팔레트(`TmtnColor`)와 섞지 않는다.
 * 카드는 "물건" 이고 화면은 "종이" 다.
 *
 * 출처: brand-identity/card-front-concepts-2026-08-29 (`.card-04`, `previews/card-front-04-v2.png`)
 */
@Composable
fun FortuneCardFront(card: MissionCard, dateKey: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TmtnCardArt.Radius))
            .background(TmtnCardArt.Ink)
            // 바깥 여백은 **테두리까지**만 준다. 본문은 아래 Column 에서 더 들어간다.
            // 여기서 Padding(22) 을 주고 테두리를 음수로 되밀면
            // Compose 가 "Padding must be non-negative" 로 앱을 죽인다.
            .padding(TmtnCardArt.InnerInset),
    ) {
        // 시안의 안쪽 실선 테두리. 카드가 인쇄물처럼 보이게 하는 장치다.
        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, TmtnCardArt.CreamFrame, RoundedCornerShape(TmtnCardArt.InnerRadius)),
        )

        Column(
            // 테두리(10)에서 본문까지 12 더. 카드 가장자리 기준으로는 시안과 같은 22dp 다.
            Modifier.padding(TmtnCardArt.Padding - TmtnCardArt.InnerInset),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            /* ── 머리 ─────────────────────────────────────── */
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "오늘의 틈",
                        color = TmtnCardArt.Cream,
                        fontSize = TmtnCardArt.TitleSize,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        TmtnDate.weekdayLabel(dateKey),
                        color = TmtnCardArt.CreamStrong,
                        fontSize = TmtnCardArt.DateSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    // 한자 축(木·火·水·土·金)은 보여 주지 않는다. 사용자용 재료 이름만 쓴다.
                    Text(
                        "${card.mission.rewardName} · ${card.mission.area}",
                        color = TmtnCardArt.CreamFaint,
                        fontSize = TmtnCardArt.MaterialSize,
                        letterSpacing = 0.8.sp,
                    )
                }
                Box(
                    Modifier
                        .size(TmtnCardArt.StampSize)
                        .clip(RoundedCornerShape(TmtnCardArt.StampRadius))
                        .background(TmtnCardArt.Wood),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialIcon(card.mission.rewardName, size = 44.dp)
                }
            }

            /* ── 오늘의 운세 ───────────────────────────────── */
            Column {
                CardLabel("오늘의 운세")
                Spacer(Modifier.height(8.dp))
                Text(
                    card.mission.fortune,
                    color = TmtnCardArt.Cream,
                    fontSize = TmtnCardArt.FortuneSize,
                    lineHeight = TmtnCardArt.FortuneSize * 1.4f,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.6).sp,
                )
            }

            /* ── 가운데 표식 ───────────────────────────────── */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(TmtnCardArt.CreamRule))
                Text(
                    "TMTN",
                    color = TmtnCardArt.Amber,
                    fontSize = TmtnCardArt.MarkSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.padding(horizontal = 9.dp),
                )
                Box(Modifier.weight(1f).height(1.dp).background(TmtnCardArt.CreamRule))
            }

            /* ── 세 가지 ──────────────────────────────────── */
            Column {
                FactRow("행운의 행동", card.mission.action)
                FactRow("행운의 위치", card.place)
                FactRow("행운의 숫자") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${card.targetNumber}",
                            color = TmtnCardArt.Amber,
                            fontSize = TmtnCardArt.NumberSize,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            card.mission.unit,
                            color = TmtnCardArt.Cream,
                            fontSize = TmtnCardArt.ValueSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }

            /* ── 오늘의 한 줄 ──────────────────────────────── */
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TmtnCardArt.LineBoxRadius))
                    .background(TmtnCardArt.Cream)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Text(
                    "오늘의 한 줄",
                    color = TmtnCardArt.Wood,
                    fontSize = TmtnCardArt.LabelSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.7.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    card.oneLine,
                    color = TmtnCardArt.Ink,
                    fontSize = TmtnCardArt.LineSize,
                    lineHeight = TmtnCardArt.LineSize * 1.4f,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text,
        color = TmtnCardArt.CreamLabel,
        fontSize = TmtnCardArt.LabelSize,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.7.sp,
    )
}

/**
 * 라벨은 왼쪽, 값은 오른쪽. 아래 얇은 선을 깔아 세 줄이 표처럼 읽히게 한다.
 * 라벨 폭 93dp 는 시안 그대로다.
 */
@Composable
private fun FactRow(
    label: String,
    value: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(93.dp)) { CardLabel(label) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (content != null) {
                    content()
                } else {
                    Text(
                        value.orEmpty(),
                        color = TmtnCardArt.Cream,
                        fontSize = TmtnCardArt.ValueSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TmtnCardArt.CreamRow))
    }
}
