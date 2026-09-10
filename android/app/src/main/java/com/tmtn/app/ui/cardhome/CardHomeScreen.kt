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

/** Figma B01·B01b · 홈 (오늘 카드 미선택/선택됨은 draw_state로 구분) */
@Composable
fun CardHomeScreen(state: CardHomeState, scope: CoroutineScope, onOpenTuntunScore: () -> Unit = {}, showDebugTools: Boolean = true) {
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
                // ⚠️ 2026-09-06 QA(접근성) 반영: 패딩이 아예 없어서 터치 영역이 약 20dp였음.
                modifier = Modifier
                    .tmtnClickable { state.step.value = CardHomeStep.NOTIFICATION_INBOX }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center),
            )
        }
        Text(state.displayDateLabel().format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN)), style = TmtnType.caption, color = colors.onSurfaceVariant)

        // ⚠️ 2026-09-07 반영: 상태전이 정책 신규 홈 화면(B18~B26, HomeStateScreens.kt)에는
        // 이 배너가 아예 없어서, 쉼/포기/중단 상태에서 미션을 고르면 배너가 통째로 사라져
        // "비활성화됐다"는 QA로 이어졌음(팀원 계정이 예전 테스트로 쉼/포기 상태에 남아있던
        // 채로 다시 미션을 고르면 그 특수 화면으로 넘어가면서 배너가 사라졌던 것). 공용
        // 함수(DebugDayBanner)로 뽑아서 모든 홈 화면이 같이 쓰게 함.
        if (showDebugTools) DebugDayBanner(state, scope)

        MascotCard(state, isSelected, scope)
        TmtnIndexSummaryCard(state, onOpenTuntunScore)
        RecentSummaryListCard(state)
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
                isGivenUp -> "오늘 미션 포기"
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
                isSelected -> "조금씩 쌓는 중"
                isRestDay -> "편하게 쉬어요"
                else -> ""
            },
        )
        TmtnPrimaryButton(
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
            Text("연속 기록 ${state.currentStreak.value}일째", style = TmtnType.caption,
                color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
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
