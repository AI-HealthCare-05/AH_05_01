@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
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
    // 손잡이 영역에서 아래로 끌면 시트가 같이 내려가고, 거리 또는 속도가 충분하면 닫힘.
    // 진입·복귀·퇴장 모션은 TmtnSheetDialog가 소유함. 드래그 감지는 손잡이 쪽으로만 한정해서
    // 아래 버튼들 클릭에는 영향 없게 함.
    com.tmtn.app.ui.common.TmtnSheetDialog(onDismiss = state::closeRestDaySheet, canDismiss = !state.isLoading.value) {
        val sheet = this
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .9f).dp)
                .tmtnSheetMotion()
                .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = with(sheet) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .tmtnSheetDragHandle(enabled = !state.isLoading.value)
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
            Text(state.displayDateLabel().toKoreanDateLabel(), style = TmtnType.bodyLarge, color = colors.onSurface)

            FlowRow(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ⚠️ 2026-09-07 반영: 상태전이 문서 G5 - 라벨은 "남은 쉼"인데 값은 쓴
                // 횟수(usedThisWeek)를 보여줘서 서로 모순됐음(QA N6). "이번 주 쉬어가기
                // N회 중 M회 남음" 형식으로 통일 - 이 값이 실제로 남은 횟수(remaining)임.
                Text("이번 주 쉬어가기", style = TmtnType.body, color = colors.onSurface)
                Text(
                    "2회 중 ${state.restDaysRemainingThisWeek.value}회 남음",
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
            com.tmtn.app.ui.onboarding.OnboardingErrorMessage(state.errorMessage.value)

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TmtnPrimaryButton(
                    text = if (state.isLoading.value) "쉼으로 저장 중…" else "오늘 쉬어가기",
                    onClick = { scope.launch { state.confirmRestDay() } },
                    enabled = state.restDaysRemainingThisWeek.value > 0 && !state.isLoading.value,
                )
                TmtnTextButton(text = "닫기", onClick = { sheet.dismiss(state::closeRestDaySheet) }, enabled = !state.isLoading.value)
            }
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
        Text(state.displayDateLabel().toKoreanDateLabel(), style = TmtnType.caption, color = colors.onSurfaceVariant)

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
