package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.TmtnHomeHero
import com.tmtn.app.ui.theme.tmtnClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.withStyle
import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon

/** Figma B01·B01b · 홈 (오늘 카드 미선택/선택됨은 draw_state로 구분) */
@Composable
fun CardHomeScreen(state: CardHomeState, scope: CoroutineScope, onOpenTuntunScore: () -> Unit = {}, showDebugTools: Boolean = true, onOpenDam: () -> Unit = {}) {
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
            Text("홈", style = TmtnType.label, color = colors.onSurface)
            Text(
                "알림", style = TmtnType.label, color = colors.onSurfaceVariant,
                // ⚠️ 2026-09-06 QA(접근성) 반영: 패딩이 아예 없어서 터치 영역이 약 20dp였음.
                modifier = Modifier
                    .tmtnClickable { state.step.value = CardHomeStep.NOTIFICATION_INBOX }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center),
            )
        }
        Text(if (state.todayChallengeState.value == "COMPLETED") "오늘의 카드, 잘 마쳤어요." else if (isSelected) "오늘 고른 작은 행동." else "오늘도 한 틈씩.",
            style = TmtnType.editorialHeadline, color = colors.onSurface)
        Text(state.displayDateLabel().format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN)), style = TmtnType.caption, color = colors.onSurfaceVariant)

        // ⚠️ 2026-09-07 반영: 상태전이 정책 신규 홈 화면(B18~B26, HomeStateScreens.kt)에는
        // 이 배너가 아예 없어서, 쉼/포기/중단 상태에서 미션을 고르면 배너가 통째로 사라져
        // "비활성화됐다"는 QA로 이어졌음(팀원 계정이 예전 테스트로 쉼/포기 상태에 남아있던
        // 채로 다시 미션을 고르면 그 특수 화면으로 넘어가면서 배너가 사라졌던 것). 공용
        // 함수(DebugDayBanner)로 뽑아서 모든 홈 화면이 같이 쓰게 함.
        if (showDebugTools) DebugDayBanner(state, scope)

        MascotCard(state, isSelected, scope)
        ExtraExerciseHomeEntry(state)
        TmtnIndexSummaryCard(state, onOpenTuntunScore)
        HomeWaistPanel(state.waist)
    }
}

/** Latest extra-exercise feature remains reachable from the refreshed home. */
@Composable
internal fun ExtraExerciseHomeEntry(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    val completed = state.todayChallengeState.value == "COMPLETED"
    androidx.compose.runtime.LaunchedEffect(completed) {
        if (completed) state.loadExerciseMissionsToday()
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(colors.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("틈새 운동", style = TmtnType.label, color = colors.onSurface)
        if (!completed) Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
        }
        if (completed) {
            val today = state.exerciseMissionsToday.value
            Text(if (today != null) "오늘 ${today.used} / ${today.limit}회 · 추가로 받은 재료" else "조금 더 움직이고 싶은 날, 재료를 하나 더.",
                style = TmtnType.body, color = colors.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .tmtnClickable { state.openExerciseMissionList() }.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("틈새 운동 둘러보기", style = TmtnType.actionLabel, color = colors.onSurface, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
        } else {
            Text("오늘의 카드를 마치면 열려요.", style = TmtnType.body, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun MascotCard(state: CardHomeState, isSelected: Boolean, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val isRestDay = state.isTodayRestDay.value
    val isCompleted = state.todayChallengeState.value == "COMPLETED"
    val isGivenUp = state.todayChallengeState.value == "SKIPPED"
    val isPaused = state.todayChallengeState.value == "PAUSED"
    val isReady = state.todayChallengeState.value == "READY"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val selectedCard = state.revealedCard.value.takeIf { isSelected && it?.challenge_id == state.todayChallengeId.value }
        if (selectedCard != null) {
            HomeMissionCard(selectedCard, completed = isCompleted) {
                TmtnPrimaryButton(if (isCompleted || isGivenUp) "오늘 카드 다시 보기" else if (isPaused) "이어서 실천하기" else "이 카드 실천하기",
                    onClick = { scope.launch { state.continueTodayMission() } })
            }
            Spacer(Modifier.height(4.dp))
        } else {
        TmtnHomeHero(
            image = when {
                isCompleted -> com.tmtn.app.R.drawable.beaver_cheer
                isGivenUp -> com.tmtn.app.R.drawable.beaver_empty
                isPaused -> com.tmtn.app.R.drawable.beaver_tilt
                isSelected -> com.tmtn.app.R.drawable.beaver_cheer
                isRestDay -> com.tmtn.app.R.drawable.beaver_rest
                else -> com.tmtn.app.R.drawable.beaver_card
            },
            description = when {
                isCompleted -> "미션을 마치고 기뻐하는 비버"
                isGivenUp -> "오늘 미션을 마무리한 비버"
                isPaused -> "잠시 숨을 고르는 비버"
                isSelected -> "미션을 응원하는 비버"
                isRestDay -> "쉬고 있는 비버"
                else -> "오늘의 카드를 든 비버"
            },
            status = when {
                isCompleted -> "오늘 실천 완료"
                isGivenUp -> "오늘은 여기까지"
                isPaused -> "잠시 중단"
                isReady -> "카드 선택 완료"
                isSelected -> "미션 진행 중"
                isRestDay -> "쉬어가는 날"
                else -> ""
            },
            title = when {
                isCompleted -> "오늘도 해냈어요"
                isGivenUp -> "오늘은 여기까지"
                isPaused -> "이어서 해볼까요?"
                isReady -> "시작해 볼까요?"
                isSelected -> "한 곳씩 메우는 중"
                isRestDay -> "편하게 쉬어요"
                else -> ""
            },
        )
        }
        if (selectedCard == null) TmtnPrimaryButton(
            text = when {
                isCompleted || isGivenUp || isSelected -> "오늘 카드 다시 보기"
                isRestDay -> "오늘 미션 해보기"
                else -> "오늘의 카드 고르기"
            },
            onClick = {
                if (isSelected) scope.launch { state.continueTodayMission() }
                else state.step.value = CardHomeStep.DECK_PICK
            },
        )
        if (isCompleted || (isRestDay && !isSelected)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("연속 기록 ${state.currentStreak.value}일째", style = TmtnType.caption,
                    color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                // 2026-09-17 추가(QA H01/H02) - refreshHomeOnReturn()이 조용히 실패하면
                // 연속 기록이 예전 값에 멈춰 있는데도 사용자는 알 방법이 없었음. 실패했을
                // 때만 작게 안내하고, 눌렀을 때 loadStreak()만 다시 부른다(오늘의 카드
                // 재조회나 완료 API 재호출 없이 이 조회만 재시도 - H02 요건).
                if (state.streakLoadFailed.value) {
                    com.tmtn.app.ui.onboarding.TmtnTextButton(
                        "연속 기록을 새로 가져오지 못했어요 · 다시 시도",
                        onClick = { scope.launch { state.loadStreak() } },
                    )
                } else if (state.isHomeRefreshing.value) {
                    Text("새로 확인하는 중…", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }
        } else if (!isGivenUp) {
            com.tmtn.app.ui.onboarding.TmtnTextButton("오늘은 쉬어가기",
                onClick = { scope.launch { state.openRestDaySheet() } })
        }
    }
}

// ⚠️ 2026-09-07 반영: 테스트 전용 - 미션 10개를 이어서 테스트하려면 실제로 10일이 걸리니,
// 서버가 인식하는 "오늘"을 하루씩 앞당겨서 바로 다음 미션을 받을 수 있게 함. 디버그
// 빌드에서만 보임(release APK에는 안 보임 + 서버도 PROD면 404로 막아둠 - 이중 안전장치).
// 예전엔 CardHomeScreen(B01/B01b) 안에만 있어서, 상태전이 정책 신규 홈 화면(B18~B26)으로
// 넘어가면 배너가 통째로 사라졌음 - 공용 함수로 뽑아서 모든 홈 화면이 같이 씀.
@Composable
internal fun DebugDayBanner(state: CardHomeState, scope: CoroutineScope) {
    if (!com.tmtn.app.BuildConfig.DEBUG) return
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("테스트: 시뮬레이션 오늘 = ${state.debugSimulatedToday.value ?: "-"}", style = TmtnType.caption, color = colors.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "초기화", style = TmtnType.caption, color = colors.error,
                modifier = Modifier.clickable { scope.launch { state.resetDebugDay() } },
            )
            Text(
                "다음 날 ›", style = TmtnType.label, color = colors.error,
                modifier = Modifier.clickable { scope.launch { state.advanceDebugDay() } },
            )
        }
    }
}

@Composable
internal fun StatusBadge(text: String) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier
            .background(colors.surface, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, style = TmtnType.caption, color = colors.onSurface)
    }
}
