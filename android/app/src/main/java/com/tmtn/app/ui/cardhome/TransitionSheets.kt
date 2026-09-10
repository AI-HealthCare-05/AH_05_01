package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTonalButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlin.math.roundToInt

/**
 * 상태전이 정책 신규 바텀시트 6종(C22~C27) — 2026-09-07 홍주님 전달
 * `백엔드전달_상태전이_2026-09-07.zip`(참고/화면/C22~C27.xml) 그대로 옮김.
 * 드래그로 닫는 시트 뼈대는 기존 RestDaySheetScreen(RestDayScreens.kt)과 동일한 패턴.
 *
 * ⚠️ C23은 기존 RestDaySheetScreen(RestDayScreens.kt)과 같은 목적(쉬어가기 확인)이지만
 * 문구가 이번 판으로 갱신됨("2회 중 2회 남음" 형식 통일 — QA N6: 홈이 "남은"이라 써놓고
 * "쓴 횟수"를 보여주던 문제 있었음). 기존 RestDaySheetScreen 호출부를 바로 이걸로
 * 바꿔도 되고, 마이그레이션 전까지 병존해도 됨 — 그건 화면 배선(CardHomeFlow.kt) 몫이라
 * 여기서는 건드리지 않음.
 *
 * ⚠️ 아래 콜백 중 실제 서버 연동이 필요한 것들의 현재 상태(2026-09-07 기준):
 * - onConfirmRest(C23)  → mark_rest_day, 이미 있음(state.confirmRestDay() 재사용 가능)
 * - onGiveUp(C26)       → skip_challenge, 이미 있음(챌린지가 있을 때만 · state.quitChallenge())
 *                          단, 카드조차 안 뽑은 상태(B19)의 포기는 기록할 서버 필드가 없음(G3)
 * - onCancelRest(C25)   → 백엔드 DELETE /records/rest-day는 있지만 안드로이드
 *                          CardHomeApi에 아직 안 뚫림 — 뚫은 뒤 연결
 * - onSwitchToGiveUp(C27) → 쉬어가기 취소 + 포기를 한 번에 처리하는 API 자체가 없음(원자적
 *                          처리 필요 여부 포함 팀 결정 대기) — 정해지면 연결
 */

private enum class SheetButtonStyle { FILLED, TONAL, OUTLINED, TEXT }

private data class SheetButton(
    val style: SheetButtonStyle,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun SheetButtonRow(button: SheetButton) {
    when (button.style) {
        SheetButtonStyle.FILLED -> TmtnPrimaryButton(text = button.label, onClick = button.onClick, enabled = button.enabled)
        SheetButtonStyle.TONAL -> TmtnTonalButton(text = button.label, onClick = button.onClick, enabled = button.enabled)
        SheetButtonStyle.OUTLINED -> TmtnOutlinedButton(text = button.label, onClick = button.onClick, enabled = button.enabled)
        SheetButtonStyle.TEXT -> TmtnTextButton(text = button.label, onClick = button.onClick)
    }
}

/** "이번 주 쉬어가기" 잔여 횟수 행 — 6개 시트 전부 같은 자리에 같은 모양으로 둠. */
@Composable
private fun RestTicketRow(valueText: String) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("이번 주 쉬어가기", style = TmtnType.bodyLarge, color = colors.onSurface)
        Text(valueText, style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

/**
 * 6개 시트 공통 뼈대 — 손잡이 · 제목 · 부제 · 잔여횟수 행 · 안내 카드 · (선택)캡션 · 버튼들.
 * 드래그해서 닫는 동작은 RestDaySheetScreen과 동일(손잡이 영역만 드래그 감지).
 */
@Composable
private fun TransitionBottomSheetShell(
    title: String,
    subtitle: String,
    restTicketValue: String,
    infoText: String,
    captionText: String? = null,
    buttons: List<SheetButton>,
    onDismiss: () -> Unit,
) {
    val colors = LocalTmtnColors.current
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(colors.onSurface.copy(alpha = 0.32f)).clickable(onClick = onDismiss))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetPx > dismissThresholdPx) onDismiss()
                                dragOffsetPx = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.width(36.dp).height(4.dp).background(colors.outline, RoundedCornerShape(2.dp)))
            }

            Text(title, style = TmtnType.title, color = colors.onSurface)
            Text(subtitle, style = TmtnType.bodyLarge, color = colors.onSurface)

            RestTicketRow(restTicketValue)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(infoText, style = TmtnType.body, color = colors.onSurface)
            }

            captionText?.let {
                Text(it, style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            buttons.forEach { SheetButtonRow(it) }
        }
    }
}

/** Figma C22 · 측정 중 이탈 · 네 갈래 [3단계] */
@Composable
fun MeasuringExitSheet(
    elapsedLabel: String,          // "지금까지 6분 20초 했어요"
    goalLabel: String,             // "목표 8분"
    restTicketValue: String,       // "2회 중 2회 남음"
    onContinue: () -> Unit,        // 계속하기 -> C03
    onPauseForLater: () -> Unit,   // 나중에 이어서 하기 -> B22
    onRestInstead: () -> Unit,     // 오늘은 쉬어가기 · 1회 사용 -> C23
    onGiveUp: () -> Unit,          // 오늘 미션 포기 -> C26
    onDismiss: () -> Unit,
) {
    TransitionBottomSheetShell(
        title = "오늘 미션, 어떻게 할까요?",
        subtitle = "$elapsedLabel · $goalLabel",
        restTicketValue = restTicketValue,
        infoText = "어떤 걸 골라도 지금까지 잰 기록은 남습니다. 자정 전이면 언제든 마음을 바꿀 수 있어요.",
        captionText = "자정을 넘기면 그때 남겨둔 상태로 정산됩니다.",
        buttons = listOf(
            SheetButton(SheetButtonStyle.FILLED, "계속하기", onContinue),
            SheetButton(SheetButtonStyle.TONAL, "나중에 이어서 하기", onPauseForLater),
            SheetButton(SheetButtonStyle.OUTLINED, "오늘은 쉬어가기 · 1회 사용", onRestInstead),
            SheetButton(SheetButtonStyle.TEXT, "오늘 미션 포기", onGiveUp),
        ),
        onDismiss = onDismiss,
    )
}

/**
 * Figma C23 · 쉬어가기 확인 · 1회 차감
 * (기존 RestDaySheetScreen과 같은 자리 — 새 문구로 갱신된 버전. 배선은 CardHomeFlow.kt 몫.)
 */
@Composable
fun RestConfirmSheet(
    dateLabel: String,
    restTicketValue: String,  // "2회 중 2회 남음"
    onConfirmRest: () -> Unit,
    onDismiss: () -> Unit,
) {
    TransitionBottomSheetShell(
        title = "오늘은 쉬어갈까요?",
        subtitle = dateLabel,
        restTicketValue = restTicketValue,
        infoText = "쉬어가기로 두면 연속 기록이 끊기지 않아요.",
        captionText = "자정 전에 미션에 도전하면 쓴 1회는 그대로 돌아옵니다.",
        buttons = listOf(
            SheetButton(SheetButtonStyle.FILLED, "오늘은 쉬어가기", onConfirmRest),
            SheetButton(SheetButtonStyle.TEXT, "닫기", onDismiss),
        ),
        onDismiss = onDismiss,
    )
}

/** Figma C24 · 쉬어가기 소진 · 차단 [5-1] */
@Composable
fun RestExhaustedSheet(
    dateLabel: String,
    onChallengeInstead: () -> Unit, // 미션 도전하기 -> B03
    onDismiss: () -> Unit,
) {
    TransitionBottomSheetShell(
        title = "이번 주 쉬어가기를 다 썼어요",
        subtitle = dateLabel,
        restTicketValue = "2회 모두 사용",
        infoText = "쉬어가기는 한 주에 두 번까지예요. 다음 주 월요일에 2회가 다시 생깁니다.",
        captionText = "오늘은 미션에 도전하거나 '오늘 포기'를 고를 수 있어요.",
        buttons = listOf(
            SheetButton(SheetButtonStyle.FILLED, "미션 도전하기", onChallengeInstead),
            SheetButton(SheetButtonStyle.TEXT, "닫기", onDismiss),
        ),
        onDismiss = onDismiss,
    )
}

/** Figma C25 · 쉬어가기 취소 · 1회 복구 [1-3 · 2-3] */
@Composable
fun RestCancelSheet(
    dateLabel: String,
    restTicketValue: String, // "남은 횟수:1회 → 2회"
    onCancelRestAndChallenge: () -> Unit, // 쉬어가기 취소하고 도전하기 -> C03
    onKeepResting: () -> Unit,            // 그대로 쉬기
    onDismiss: () -> Unit,
) {
    TransitionBottomSheetShell(
        title = "쉬어가기를 취소하고 도전할까요?",
        subtitle = dateLabel,
        restTicketValue = restTicketValue,
        infoText = "오늘 쓴 쉬어가기 1회가 그대로 돌아와요. 뽑아둔 카드로 이어서 진행합니다.",
        buttons = listOf(
            SheetButton(SheetButtonStyle.FILLED, "쉬어가기 취소하고 도전하기", onCancelRestAndChallenge),
            SheetButton(SheetButtonStyle.TEXT, "그대로 쉬기", onKeepResting),
        ),
        onDismiss = onDismiss,
    )
}

/**
 * Figma C26 · 오늘 포기 확인.
 * ⚠️ 주 버튼이 사용자가 누르려던 것의 "반대"(오늘은 쉬어가기)임 — 연속 기록을 잃는 선택
 * 앞에서 한 번 붙잡는 자리(상태전이_흐름도 4절). 보조 버튼(그래도 포기하기)은 그대로 눌리게 둠.
 */
@Composable
fun GiveUpConfirmSheet(
    dateLabel: String,
    onRestInstead: () -> Unit,  // 오늘은 쉬어가기(주 버튼) -> C23
    onGiveUp: () -> Unit,       // 그래도 포기하기(보조 버튼) -> B24
    onDismiss: () -> Unit,
) {
    TransitionBottomSheetShell(
        title = "오늘 미션을 포기할까요?",
        subtitle = dateLabel,
        restTicketValue = "2회 그대로 · 차감 없음",
        // ⚠️ 2026-09-08 반영: 포기하면 서버가 진행값을 0으로 지움(challenge_service.skip).
        // "다시 도전할 수 있다"만 적어두면 이어서 하는 것처럼 읽혀서, 지금까지 한 게
        // 사라진다는 걸 같이 알림.
        infoText = "지금까지 한 진행은 사라지고 연속 기록도 끊어집니다.",
        captionText = "자정 전이면 처음부터 다시 도전할 수 있어요.",
        buttons = listOf(
            SheetButton(SheetButtonStyle.FILLED, "오늘은 쉬어가기", onRestInstead),
            SheetButton(SheetButtonStyle.TEXT, "그래도 포기하기", onGiveUp),
        ),
        onDismiss = onDismiss,
    )
}

/**
 * Figma C27 · 쉬어가기 → 포기 전환 확인.
 * ⚠️ C26과 같은 원칙 — 연속 기록을 잃는 쪽(포기로 바꾸기)이 보조 버튼.
 */
@Composable
fun RestToGiveUpSheet(
    dateLabel: String,
    restTicketValue: String, // "1회 → 2회 · 1회 돌아옴"
    onKeepResting: () -> Unit,     // 쉬어가기 그대로 두기(주 버튼)
    onSwitchToGiveUp: () -> Unit,  // 포기로 바꾸기(보조 버튼) -> B24
    onDismiss: () -> Unit,
) {
    TransitionBottomSheetShell(
        title = "쉬어가기를 포기로 바꿀까요?",
        subtitle = dateLabel,
        restTicketValue = restTicketValue,
        infoText = "쓴 쉬어가기 1회가 돌아옵니다. 대신 오늘은 미완료가 되어 연속 기록이 끊기고, " +
            "지금까지 한 진행도 사라집니다.",
        captionText = "자정 전이면 처음부터 다시 도전할 수 있어요.",
        buttons = listOf(
            SheetButton(SheetButtonStyle.FILLED, "쉬어가기 그대로 두기", onKeepResting),
            SheetButton(SheetButtonStyle.TEXT, "포기로 바꾸기", onSwitchToGiveUp),
        ),
        onDismiss = onDismiss,
    )
}
