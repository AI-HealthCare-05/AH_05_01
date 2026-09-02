package com.tmtn.app.ui.cardhome

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma B16 · 홈 · 쉬어가기 확인 (바텀시트 - 화면 전체로 단순화해서 구현) */
@Composable
fun RestDaySheetScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(colors.outline, RoundedCornerShape(2.dp)),
            )
            Text("오늘은 쉬어갈까요?", style = TmtnType.title, color = colors.onSurface)
            Text("2026. 8. 27. 목요일", style = TmtnType.bodyLarge, color = colors.onSurface)

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
        Text("2026. 8. 27. 목요일", style = TmtnType.caption, color = colors.onSurfaceVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(88.dp)) {
                Text(
                    "일러스트 자리 · 쉬는 비버", style = TmtnType.caption, color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center),
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
