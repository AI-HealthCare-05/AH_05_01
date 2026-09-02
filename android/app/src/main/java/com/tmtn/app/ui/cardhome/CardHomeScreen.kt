package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma B01·B01b · 홈 (오늘 카드 미선택/선택됨은 draw_state로 구분) */
@Composable
fun CardHomeScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val isSelected = state.drawState.value == "SELECTED"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("틈튼", style = TmtnType.title, color = colors.onSurface)
            Text(
                "알림", style = TmtnType.label, color = colors.onSurfaceVariant,
                modifier = Modifier.clickable { state.step.value = CardHomeStep.NOTIFICATION_INBOX },
            )
        }
        Text("2026. 8. 27. 목요일", style = TmtnType.caption, color = colors.onSurfaceVariant)

        MascotCard(state, isSelected, scope)
        TmtnIndexSummaryCard()
        RecentSummaryListCard(state)
    }
}

@Composable
private fun MascotCard(state: CardHomeState, isSelected: Boolean, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val isRestDay = state.isTodayRestDay.value
    val isCompleted = state.todayChallengeState.value == "COMPLETED"
    // ⚠️ "중단"으로 끝낸 미션(SKIPPED)도 draw_state는 계속 SELECTED라 isSelected가 true로
    // 남는데, 그대로 두면 "미션 이어하기"가 보여서 다시 시작할 수 있는 것처럼 보임(실제로는
    // 서버가 재시작을 막아 에러가 남). isCompleted 다음으로 먼저 체크해서 우선함.
    val isSkipped = state.todayChallengeState.value == "SKIPPED"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(88.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    isCompleted -> "일러스트 자리 · 뿌듯한 비버"
                    isSkipped -> "일러스트 자리 · 쉬는 비버"
                    // ⚠️ 카드를 실제로 골랐으면(=진짜 뭔가 하기로 함) 그게 "쉼" 표시보다
                    // 우선함. "쉼"은 아직 아무것도 안 골랐을 때만 보여주는 기본 상태.
                    isSelected -> "일러스트 자리 · 응원하는 비버"
                    isRestDay -> "일러스트 자리 · 쉬는 비버"
                    else -> "일러스트 자리 · 카드를 든 비버"
                },
                style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }

        if (isCompleted) {
            StatusBadge(text = "완료")
        } else if (isSkipped) {
            StatusBadge(text = "쉼")
        } else if (isSelected) {
            StatusBadge(text = "진행 중")
        } else if (isRestDay) {
            StatusBadge(text = "쉼")
        }

        Text(
            when {
                isCompleted -> "오늘 몫은 다 했어요. 잘했어요!"
                isSkipped -> "오늘은 쉬어가기로 했어요."
                isSelected -> "오늘 고른 미션이 기다리고 있어."
                isRestDay -> "오늘은 쉬어가는 날이에요."
                else -> "안녕! 오늘 카드 세 장 가져왔어."
            },
            style = TmtnType.bodyLarge, color = colors.onSurface, textAlign = TextAlign.Center,
        )

        TmtnPrimaryButton(
            text = when {
                isCompleted -> "오늘 카드 다시 보기"
                isSkipped -> "오늘 카드 다시 보기"
                isSelected -> "미션 이어하기"
                isRestDay -> "그래도 미션 해볼래요"
                else -> "오늘의 카드 고르기"
            },
            onClick = {
                if (isSelected) {
                    scope.launch { state.continueTodayMission() }
                } else {
                    state.step.value = CardHomeStep.DECK_PICK
                }
            },
        )

        // ⚠️ 오늘 몫을 이미 끝냈으면(완료든 쉬어가기든) "쉬어가기"를 보여줄 이유가 없음 -
        // 이미 끝났는데 쉬겠다는 게 의미상 안 맞음. 그 전(선택만 했거나 아직 안 골랐을 때)에만 노출.
        if (isCompleted) {
            Text(
                "연속 기록 ${state.currentStreak.value}일째 · 오늘도 이어졌어요",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else if (isSkipped) {
            // 별다른 캡션 없음 - 이미 위 상태 문구로 충분히 설명됨.
        } else if (!isRestDay || isSelected) {
            Text(
                "오늘은 쉬어가기", style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .clickable { scope.launch { state.openRestDaySheet() } },
            )
        } else {
            Text(
                "연속 기록 ${state.currentStreak.value}일째 · 그대로 이어져요",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusBadge(text: String) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier
            .background(colors.secondaryContainer, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, style = TmtnType.caption, color = colors.onSurface)
    }
}

/**
 * Figma "틈튼지수 요약" 카드.
 * ⚠️ 틈튼지수 계산 로직/API가 아직 없어서, 지금은 Figma 예시값(68, 보통 구간)을 그대로
 * 표시하는 자리만 만들어둠. 실제 계산 API 생기면 이 함수에 파라미터로 실제 값을 받아서
 * 교체할 것.
 */
@Composable
private fun TmtnIndexSummaryCard() {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("틈튼지수", style = TmtnType.label, color = colors.onSurface)
            Text("자세히 ›", style = TmtnType.caption, color = colors.onSurfaceVariant) // TODO: E02로 이동
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("68", style = TmtnType.display, color = colors.onSurface)
            Box(
                modifier = Modifier
                    .background(colors.surface, RoundedCornerShape(999.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("보통 구간", style = TmtnType.caption, color = colors.onSurface)
            }
        }
        // 0~100 구간 막대(관심/보통/양호 3분할)
        Row(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            Box(modifier = Modifier.weight(1f).height(10.dp).background(colors.disabledContainer, RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.weight(1f).height(10.dp).background(colors.secondary, RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.weight(1f).height(10.dp).background(colors.onSurface, RoundedCornerShape(4.dp)))
        }
        Text(
            "2026. 8. 20. ~ 8. 27. · 비진단용 참고 정보",
            style = TmtnType.caption, color = colors.onSurfaceVariant,
        )
    }
}

/**
 * Figma "최근 7일" + "댐" 목록 카드.
 * ⚠️ "최근 7일" 점 7개는 아직 전용 요약 API가 없어서 예시 표시만 함(기록 캘린더 주간 API로
 * 나중에 교체 가능). "댐"은 실제 GET /companion 데이터를 그대로 씀.
 */
@Composable
private fun RecentSummaryListCard(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("최근 7일", style = TmtnType.label, color = colors.onSurface)
                Text("이번 주 5일 실천했어요", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // 예시 패턴(완료/완료/미완료/완료/완료/미완료/오늘) - 실제 데이터로 교체 예정
                listOf(true, true, false, true, true, false, null).forEach { done ->
                    DayDot(done)
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("댐", style = TmtnType.label, color = colors.onSurface)
                val stage = state.companionStage.value
                val needed = state.companionMaterialsNeeded.value
                val nextLabel = state.companionNextStageLabel.value
                Text(
                    if (nextLabel != null && needed != null) {
                        "$stage 단계 · $nextLabel · 다음까지 ${needed}개"
                    } else {
                        "$stage 단계"
                    },
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayDot(done: Boolean?) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier
            .size(10.dp)
            .then(
                if (done == true) {
                    Modifier.background(colors.onSurface, CircleShape)
                } else if (done == false) {
                    Modifier.border(1.5.dp, colors.outline, CircleShape)
                } else {
                    Modifier.border(2.dp, colors.secondary, CircleShape) // 오늘
                }
            ),
    )
}
