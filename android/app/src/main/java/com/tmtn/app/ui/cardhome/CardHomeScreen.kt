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
        TmtnIndexSummaryCard(state)
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
                    // ⚠️ 2026-09-03 리뷰 반영: SKIPPED(중단)를 REST(쉼)랑 같은 "쉬는 비버"로
                    // 보여주고 있었는데, 서버 기준으로 완전히 다른 상태임(아래 문구 수정 참고).
                    // 그림도 "쉬는" 포즈 대신 다른 포즈로 바꿔야 함 - 지금은 전용 에셋이 없어서
                    // 자리 표시 문구만 구분해둠(에셋 준비되면 교체).
                    isSkipped -> "일러스트 자리 · 카드를 내려놓은 비버"
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
            // ⚠️ 2026-09-03 리뷰 반영: SKIPPED(중단)를 REST(쉼)와 같은 "쉼" 뱃지로 보여주고
            // 있었음. 서버 기준(record_service.py)으로 REST는 연속 기록이 안 끊기고 주 2회
            // 한도가 차감되지만, SKIPPED는 COMPLETED가 아니라서 그대로 INCOMPLETE로 집계되고
            // 연속 기록이 끊김. 홈에서는 "쉼"이라 안심시켜놓고 기록 탭 가면 연속 기록이 끊겨
            // 있는 모순이라, "중단"으로 명확히 구분함.
            StatusBadge(text = "중단")
        } else if (isSelected) {
            StatusBadge(text = "진행 중")
        } else if (isRestDay) {
            StatusBadge(text = "쉼")
        }

        Text(
            when {
                isCompleted -> "오늘 몫은 다 했어요. 잘했어요!"
                isSkipped -> "오늘 카드는 여기서 멈췄어요."
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
 * ⚠️ 예전엔 "68"/"보통 구간"/날짜가 전부 하드코딩된 예시값이었음. /tuntun-score/v2를
 * 그대로 써서(참고 탭 CardHomeState.loadTuntunIndexSummary() 참고) 실제 값으로 표시함.
 * 아직 계산할 수 없는 상태(신체정보·운동습관 미입력 등)면 "68" 대신 안내 문구를 보여줌.
 */
@Composable
private fun TmtnIndexSummaryCard(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    val value = state.tuntunIndexValue.value
    val band = state.tuntunIndexBand.value
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
            Text("자세히 ›", style = TmtnType.caption, color = colors.onSurfaceVariant) // TODO: 참고 탭으로 이동(탭 전환 콜백 필요)
        }
        if (value == null || band == null) {
            Text(
                "아직 계산할 수 없어요 · 신체정보나 운동습관을 입력해 보세요",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$value", style = TmtnType.display, color = colors.onSurface)
                Box(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(999.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("$band 구간", style = TmtnType.caption, color = colors.onSurface)
                }
            }
            // 0~100 구간 막대(관심/보통/양호 3분할) - 실제 구간만 강조색으로 표시
            Row(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                listOf("관심", "보통", "양호").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.weight(1f).height(10.dp)
                            .background(
                                if (label == band) colors.secondary else colors.disabledContainer,
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    if (index < 2) Spacer(modifier = Modifier.width(2.dp))
                }
            }
            Text(
                "${state.tuntunIndexPeriodLabel.value ?: ""} · 비진단용 참고 정보",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Figma "최근 7일" + "댐" 목록 카드.
 * ⚠️ 예전엔 "최근 7일" 점 7개가 listOf(true, true, false, true, true, false, null) 하드코딩
 * 예시 패턴으로 항상 똑같이 표시됐음. 기록 탭과 같은 주간 리포트 API(CardHomeState.
 * loadRecentWeek() 참고)를 그대로 써서 실제 최근 7일 상태로 표시함. "댐"은 실제 GET
 * /companion 데이터를 그대로 씀.
 */
@Composable
private fun RecentSummaryListCard(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    val recentWeek = state.recentWeek.value
    val completedCount = recentWeek.count { it.status == "COMPLETED" }
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
                Text("이번 주 ${completedCount}일 실천했어요", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val today = java.time.LocalDate.now().toString()
                recentWeek.forEach { day -> DayDot(status = day.status, isToday = day.date == today) }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
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
            // ⚠️ 이 행 오른쪽에 빈 공간이 있길래, 오행별 재료 개수를 여기 보여주기로 함.
            // "댐" 탭(G01)에서 이미 쓰는 companion.materials를 홈에서도 재사용 - 새 API 없음.
            if (state.companionMaterials.value.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MATERIAL_NAMES.keys.forEach { element ->
                        val count = state.companionMaterials.value.firstOrNull { it.element == element }?.count ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            MaterialIcon(element = element, size = 18.dp)
                            Text("$count", style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDot(status: String, isToday: Boolean) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier
            .size(10.dp)
            // ⚠️ 예전엔 done==null(=예시 데이터의 마지막 자리)일 때만 "오늘" 링이 보였는데,
            // 이제 실제 데이터는 최근 7일이 전부 실제 상태(COMPLETED/INCOMPLETE/REST)를 가져서
            // null인 경우가 없음. MonthlyCalendarScreen.kt와 같은 방식으로 "오늘"을 상태와
            // 무관하게 바깥 링으로 항상 같이 표시함.
            .then(if (isToday) Modifier.border(2.dp, colors.secondary, CircleShape).padding(2.dp) else Modifier)
            .then(
                when (status) {
                    "COMPLETED" -> Modifier.background(colors.onSurface, CircleShape)
                    "REST" -> Modifier.background(colors.disabledContainer, CircleShape).border(1.dp, colors.onSurface, CircleShape)
                    else -> Modifier.border(1.5.dp, colors.outline, CircleShape) // INCOMPLETE
                },
            ),
    )
}
