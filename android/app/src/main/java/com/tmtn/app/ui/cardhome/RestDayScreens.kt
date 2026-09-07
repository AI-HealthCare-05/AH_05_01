package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Figma B16 · 홈 · 쉬어가기 확인 (바텀시트 - 화면 전체로 단순화해서 구현) */
@Composable
fun RestDaySheetScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    // ⚠️ 손잡이 막대만 있고 실제 드래그 동작이 없어서 "눌러도 안 움직인다"는 피드백을 받음.
    // 손잡이 영역에서 아래로 끌면 시트가 같이 내려가고, 일정 거리 넘으면 닫히게 함.
    // 드래그 감지 영역을 손잡이 쪽으로만 한정해서, 아래 버튼들 클릭에는 영향 없게 함.
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 지금 보고 있던 화면 위에 얹히는 오버레이라는 걸 보여주는 반투명 스크림.
        // D그룹 DayDetailSheet와 같은 처리.
        Box(modifier = Modifier.fillMaxSize().background(colors.onSurface.copy(alpha = 0.32f)))

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
                                if (dragOffsetPx > dismissThresholdPx) state.closeRestDaySheet()
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
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(colors.outline, RoundedCornerShape(2.dp)),
                )
            }
            Text("오늘은 쉬어갈까요?", style = TmtnType.title, color = colors.onSurface)
            Text(LocalDate.now().toKoreanDateLabel(), style = TmtnType.bodyLarge, color = colors.onSurface)

            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("이번 주 남은 쉼", style = TmtnType.body, color = colors.onSurface)
                Text(
                    "${state.restDaysUsedThisWeek.value}회 / 2회",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text("쉼으로 표시해도 연속 기록은 그대로 이어져요.", style = TmtnType.body, color = colors.onSurface)
            }
            Text("한 주에 두 번까지 쉴 수 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            TmtnPrimaryButton(
                text = "오늘 쉬어가기",
                onClick = { scope.launch { state.confirmRestDay() } },
                enabled = state.restDaysRemainingThisWeek.value > 0,
            )
            TmtnTextButton(text = "닫기", onClick = { state.closeRestDaySheet() })
        }
    }
}

/** Figma B17 · 홈 · 오늘은 쉼 */
@Composable
fun RestDayDoneScreen(state: CardHomeState, scope: kotlinx.coroutines.CoroutineScope) {
    val colors = LocalTmtnColors.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("틈튼", style = TmtnType.title, color = colors.onSurface)
        Text(LocalDate.now().toKoreanDateLabel(), style = TmtnType.caption, color = colors.onSurfaceVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                // ⚠️ 2026-09-06 반영: B16(홈 · 쉬어가기 확인) 배치표 그대로 - beaver_cheer.
                Image(
                    painter = painterResource(com.tmtn.app.R.drawable.beaver_cheer),
                    contentDescription = "쉬어가기를 응원하는 비버",
                    modifier = Modifier.height(88.dp),
                )
            }
            Box(
                modifier = Modifier
                    .background(colors.secondaryContainer, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("쉼", style = TmtnType.caption, color = colors.onSurface)
            }
            Text("오늘은 쉬어가는 날이에요.", style = TmtnType.bodyLarge, color = colors.onSurface)
            TmtnOutlinedButton(
                text = "그래도 미션 해볼래요",
                onClick = {
                    // ⚠️ 쉬어가기는 daily_record_notes만 건드리고 카드 확정 여부와는 무관함.
                    // 이미 오늘 카드를 골라둔 상태라면 새로 고르는 화면(DECK_PICK)이 아니라
                    // 이미 확정된 그 미션(REVEALED)으로 이어줘야 함 - 안 그러면 "이미 확정된
                    // 카드가 있습니다" 에러로 되돌아오는 문제가 생김.
                    if (state.drawState.value == "SELECTED" && state.todayChallengeId.value != null) {
                        scope.launch { state.continueTodayMission() }
                    } else {
                        state.step.value = CardHomeStep.DECK_PICK
                    }
                },
            )
            Text(
                "연속 기록 ${state.currentStreak.value}일째 · 그대로 이어져요",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )
        }
    }
}
