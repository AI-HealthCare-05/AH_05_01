package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.tmtn.app.ui.onboarding.TmtnTopBar
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
        "WOOD", "FIRE", "EARTH", "METAL", "WATER" -> com.tmtn.app.ui.common.tmtnMaterialDrawable(element)
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
    // ⚠️ 2026-09-07 반영(G4): SKIPPED(포기)에서 "다시 도전하기"를 눌렀을 때 - onStartAction과
    // 별도로 둔 이유는 onStartAction은 "아직 시작 전/진행 중" 카드 기준 판단이라, SKIPPED
    // 카드를 그대로 넣으면 stepForRevealedCard가 곧장 REVEALED로 되돌려서(현재 화면) 아무
    // 일도 안 일어남 - 실제로 서버에 재시작을 요청하는 별도 경로가 필요함.
    onRestartFromGiveUp: () -> Unit = {},
) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    // ⚠️ 2026-09-07 반영(G4): COMPLETED와 SKIPPED(포기)를 똑같이 "끝난 미션"으로 묶어서
    // 시작 버튼을 통째로 숨기고 있었음. 그런데 start()는 이미 SKIPPED에서도 재시작을
    // 허용하도록 고쳐져 있었음(G2, 정책 P1 "자정 전에는 어떤 선택도 영구 확정되지
    // 않는다") - 정작 그걸 실제로 누를 방법이 안드로이드 어디에도 없었던 게 이 버그의
    // 본체. COMPLETED만 진짜로 끝난 것으로 취급하고, SKIPPED는 "다시 도전하기"를 보여줌.
    val isCompleted = card.state == "COMPLETED"
    val isSkipped = card.state == "SKIPPED"
    val isFinished = isCompleted || isSkipped

    Column(modifier = Modifier.fillMaxSize()) {
        // ⚠️ 2026-09-06 QA(레이아웃) 반영: 몰입 모드(하단 탭 숨김)인 건 의도된 것인데,
        // 상단 앱바까지 없어서 화면에 보이는 나가는 길이 시스템 뒤로가기 제스처뿐이었음.
        // 몰입 모드는 유지하되 상단 "←"만 추가 - previousStepFor(REVEALED)=HOME과 같은 동작.
        TmtnTopBar(title = "오늘의 카드", onBack = { state.step.value = CardHomeStep.HOME })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            TmtnMissionCard(card, state.displayDateLabel())
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
            // 그대로 재사용됨. isFinished를 써서 "이 카드 실천하기"는 완전히 숨김("어차피
            // onStartAction이 다시 완료 화면으로 돌려보내니 눌러봤자 의미 없음).
            //
            // ⚠️ 2026-09-04 반영: 진행 중(ACTIVE/PAUSED)인 미션 화면에서 뒤로가기로 여기
            // 돌아왔을 때 "이 카드 실천하기"가 그대로 보여서 마치 새로 시작하는 것처럼
            // 헷갈렸음. 이제 진행 중이면 "진행 중인 미션 확인"으로 문구만 바꿔서 보여줌 -
            // onStartAction은 이미 stepForRevealedCard()로 진행 상태에 맞는 화면(타이머
            // 진행/일시정지 등)으로 정확히 보내주므로 그대로 재사용.
            //
            // ⚠️ 2026-09-04 멘토링 반영: "오늘은 쉬어가기"는 홈 화면에만 남기고 다른 화면
            // 전부에서 없애기로 방향이 정해짐 - 이 화면(카드/B06)의 버튼도 제거.
            val isInProgress = card.state == "ACTIVE" || card.state == "PAUSED"
            // ⚠️ 2026-09-07 반영: 쉬어가기(REST) 중에 "오늘 카드 다시 보기"로 들어와서
            // 여기서 곧장 "이 카드 실천하기"를 누르면, 서버에 쉬어가기 취소 절차
            // (cancel_rest_day, C25) 없이 그냥 시작돼버려서 완료해도 쓴 쉬어가기 1회가
            // 안 돌아오는 문제가 있었음. READY 상태(아직 시작 전)에서만 숨기고, 이미
            // 시작된 미션(ACTIVE/PAUSED)은 계속 확인할 수 있어야 하니 그대로 둠 - "새로
            // 시작"만 반드시 홈의 "뽑아둔 카드로 도전하기"(C25, 쉬어가기 취소를 같이 처리)를
            // 거치게 함.
            val isRestingBeforeStart = state.isTodayRestDay.value && !isInProgress && !isFinished
            if (!isFinished && !isRestingBeforeStart) {
                TmtnPrimaryButton(
                    text = if (isInProgress) "진행 중인 미션 확인" else "이 카드 실천하기",
                    onClick = onStartAction,
                )
            } else if (isRestingBeforeStart) {
                Text(
                    "오늘은 쉬어가기로 표시돼 있어요. 시작하려면 홈에서 " +
                        "\"뽑아둔 카드로 도전하기\"를 눌러 주세요 - 쓴 쉬어가기 1회가 함께 돌아와요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            } else if (isSkipped) {
                // ⚠️ 2026-09-07 반영(G4/G2): "포기"는 완료와 달리 되돌릴 수 있음 - 오늘 안에는
                // 언제든 다시 도전할 수 있다는 걸 실제 버튼으로 보여줌.
                TmtnPrimaryButton(text = "다시 도전하기", onClick = onRestartFromGiveUp)
                Text(
                    "오늘 자정 전이면 언제든 다시 시작할 수 있어요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // ⚠️ 2026-09-06 반영: 완료/쉬어감 정보를 이 화면 하단에 보여주던 블록을
            // 다시 없앰 - "카드만 보이면 된다"는 방향으로 정리됨. 별도 축하 화면
            // (CompletedScreen)으로도 안 돌아가고, 그냥 이 카드 화면 자체만 보여줌.

        }
    }
}

@Composable
fun NoteCard(card: CardRevealResponse, displayDate: java.time.LocalDate) {
    val material = MATERIAL_NAMES[card.five_element]
    val cardFont = com.tmtn.app.ui.common.TarotCardFontFamily
    // ⚠️ 2026-09-12 반영: TMTN_V19 카드 리디자인 - 다크 "신문" 감성에서 크림색 종이 +
    // 갈색 장식 프레임(TarotCardFrame, 실제 원화 이미지 9-patch 스트레칭)으로 전면 교체.
    // 모델 인식 배지 규칙(V17)은 그대로 유지 - 프레임 자체에 주황 테두리를 그려 넣음.
    val isModelMission = com.tmtn.app.ui.common.isModelRecognitionExecType(card.exec_type)
    val ink = Color(0xFF16181C) // V19 본문 색
    val secondary = Color(0xFF666A71) // V19 보조정보 색

    com.tmtn.app.ui.common.TarotCardFrame(
        isBack = false,
        modelMission = isModelMission,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 650.dp), // V19: "기본 카드 너비 350, 최소 높이 650"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp).padding(top = 40.dp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("오늘의 틈", style = TmtnType.title.copy(fontFamily = cardFont), color = ink)

            // 원형 메달리온에 재료 이미지
            Box(
                modifier = Modifier.size(90.dp).background(Color(0xFFF7F4EE), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MaterialIcon(element = card.five_element, size = 56.dp)
            }

            if (material != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${material.first} · ${card.domain ?: material.second}", style = TmtnType.label.copy(fontFamily = cardFont), color = ink)
                    Text(displayDate.toKoreanDateLabel(), style = TmtnType.caption.copy(fontFamily = cardFont), color = secondary)
                }
            }

            if (card.fortune_text != null) {
                Text(card.fortune_text, style = TmtnType.title.copy(fontFamily = cardFont), color = ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            if (isModelMission) com.tmtn.app.ui.common.ModelMissionBadge()

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LuckyRowV19(label = "행운의 행동", value = card.title, ink = ink, secondary = secondary, cardFont = cardFont)
                if (card.lucky_location != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("행운의 위치", style = TmtnType.caption.copy(fontFamily = cardFont), color = secondary)
                            Text(card.lucky_location, style = TmtnType.label.copy(fontFamily = cardFont), color = ink)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("행운의 숫자", style = TmtnType.caption.copy(fontFamily = cardFont), color = secondary)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${card.target_value}", style = TmtnType.headline.copy(fontFamily = cardFont), color = ink)
                                Text(card.unit, style = TmtnType.body.copy(fontFamily = cardFont), color = ink)
                            }
                        }
                    }
                } else {
                    LuckyRowV19(label = "행운의 숫자", value = "${card.target_value}${card.unit}", ink = ink, secondary = secondary, cardFont = cardFont)
                }
            }

            if (card.line_text != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF7F4EE), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("오늘의 한 줄", style = TmtnType.caption.copy(fontFamily = cardFont), color = secondary)
                    Text(card.line_text, style = TmtnType.label.copy(fontFamily = cardFont), color = ink)
                }
            }

            if (material != null) {
                Text(
                    // ⚠️ 2026-09-12 반영: V19 C08(완료 카드) - 완료 상태면 "실천하면"이
                    // 아니라 "실천 완료 · 받았어요"로 바뀜. NoteCard를 완료 화면에서도
                    // 재사용하기 위해 카드 자체 상태(card.state)로 분기.
                    if (card.state == "COMPLETED") "실천 완료, ${material.first} 1개를 받았어요"
                    else "실천하면 ${material.first} 1개",
                    style = TmtnType.caption.copy(fontFamily = cardFont), color = secondary,
                )
            }
        }
    }
}

@Composable
private fun LuckyRowV19(label: String, value: String, ink: Color, secondary: Color, cardFont: androidx.compose.ui.text.font.FontFamily) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TmtnType.caption.copy(fontFamily = cardFont), color = secondary)
        Text(value, style = TmtnType.label.copy(fontFamily = cardFont), color = ink)
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
