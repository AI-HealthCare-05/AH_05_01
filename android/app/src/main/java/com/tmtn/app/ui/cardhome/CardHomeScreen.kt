package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 확정 시안 C. 모든 미션 상태에서 동일한 홈 구조와 기존 상태 전이를 사용한다. */
@Composable
fun CardHomeScreen(state: CardHomeState, scope: CoroutineScope, onOpenTuntunScore: () -> Unit = {}, showDebugTools: Boolean = true, onOpenDam: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("home-c-scroll")
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("틈튼", style = TmtnType.title, color = TmtnHomeColor.Forest, modifier = Modifier.weight(1f))
            Text(state.displayDateLabel().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                style = TmtnType.caption, color = ColorOnSurfaceVariant)
            // 기존 알림함 진입은 보존한다. 카드·일보의 장식 화살표는 사용하지 않는다.
            IconButton(onClick = { state.step.value = CardHomeStep.NOTIFICATION_INBOX }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Notifications, "알림", tint = ColorOnSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        if (state.activeExerciseSession.value != null) {
            ActiveExerciseBanner(state)
            Spacer(Modifier.height(16.dp))
        }
        if (state.todayChallengeState.value == "COMPLETED") {
            HomeCompletionPanel(state, onViewCard = { scope.launch { state.continueTodayMission() } }, onOpenDam)
        } else HomeCardMission(state, scope)
        Spacer(Modifier.height(32.dp))
        ExtraExerciseHomeEntry(state)
        Spacer(Modifier.height(16.dp))
        TmtnIndexSummaryCard(state, onOpenTuntunScore)
        Spacer(Modifier.height(12.dp))
        HomeWaistPanel(state.waist)
    }
}

@Composable
internal fun ExtraExerciseHomeEntry(state: CardHomeState) {
    val completed = state.todayChallengeState.value == "COMPLETED"
    val allExtrasDone = completed && homeCelebration(state.cardServiceDate.value, state.homeExerciseProgress.value) == HomeCelebration.EXTRA_TWO
    // 목록 진입 때 기존 목록 화면이 조회한다. 홈 렌더링만으로 측정 화면으로 이동하지 않는다.
    Row(Modifier.fillMaxWidth().testTag("home-extra")
        .clip(RoundedCornerShape(20.dp))
        .background(if (completed) TmtnHomeColor.ExtraOpen else TmtnHomeColor.ExtraLocked)
        .then(if (completed) Modifier.tmtnClickable(onClick = { state.openExerciseMissionList() }) else Modifier.semantics(mergeDescendants = true) { disabled() })
        .heightIn(min = 96.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.home_extra_exercise_mat), null, Modifier.size(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("틈새 운동", style = TmtnType.missionName, color = ColorOnSurface, modifier = Modifier.semantics { heading() })
            Text(when { allExtrasDone -> "오늘 두 번 완료 · 운동 목록 보기"; completed -> "오늘의 운동 5가지 보기"; else -> "오늘의 카드 완료 후 열려요." }, style = TmtnType.caption, color = ColorOnSurface)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(if (completed) R.drawable.home_lock_open else R.drawable.home_lock_closed), null,
                Modifier.size(24.dp), tint = if (completed) TmtnHomeColor.Forest else ColorOnSurfaceVariant)
            Text(if (completed) "열림" else "잠김", style = TmtnType.caption,
                color = if (completed) TmtnHomeColor.Forest else ColorOnSurfaceVariant)
        }
    }
}

@Composable
private fun HomeCardMission(state: CardHomeState, scope: CoroutineScope) {
    val selected = state.drawState.value == "SELECTED"
    val completed = state.todayChallengeState.value == "COMPLETED"
    // 쉼을 포기보다 먼저 판정: 서버는 쉼으로 바꾸어도 SKIPPED 이력을 보존한다.
    val rest = !completed && state.isTodayRestDay.value
    val givenUp = !completed && !rest && (state.isTodayGivenUp.value || state.todayChallengeState.value == "SKIPPED")
    val paused = !completed && !rest && !givenUp && state.todayChallengeState.value == "PAUSED"
    val card = state.revealedCard.value.takeIf { selected && it?.challenge_id == state.todayChallengeId.value }
    val primary = when {
        completed -> "완료한 카드 보기"
        rest -> if (selected) "고른 미션 도전하기" else "오늘 미션 도전하기"
        givenUp -> if (selected) "고른 미션 도전하기" else "오늘의 카드 고르기"
        paused -> "미션 이어서 하기"
        !selected -> "오늘의 카드 고르기"
        state.todayChallengeState.value == "ACTIVE" -> "미션 이어서 하기"
        else -> "이 행동 시작하기"
    }
    val onPrimary: () -> Unit = {
        when {
            rest -> state.showRestCancelSheet.value = true
            !selected -> state.step.value = CardHomeStep.DECK_PICK
            givenUp -> scope.launch { state.restartFromGiveUp() }
            paused -> scope.launch { state.enterInProgressMission() }
            selected && !completed -> scope.launch { state.enterInProgressMission() }
            else -> scope.launch { state.continueTodayMission() }
        }
    }
    val actions: @Composable (Boolean) -> Unit = { onForest ->
        Row(Modifier.fillMaxWidth().testTag("home-mission-actions").heightIn(min = 58.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (completed) {
                Text("완료했어요", style = TmtnType.caption, color = TmtnHomeColor.Forest,
                    modifier = Modifier.weight(.3f), textAlign = TextAlign.Center)
            } else {
                TextButton(onClick = {
                    if (rest) state.showRestToGiveUpSheet.value = true else scope.launch { state.openRestDaySheet() }
                }, enabled = !state.isLoading.value, modifier = Modifier.weight(.3f).heightIn(min = 48.dp)) {
                    Text(if (rest) "오늘 포기" else "쉬어가기", style = TmtnType.label,
                        color = if (onForest) Color.White else TmtnHomeColor.Forest, textAlign = TextAlign.Center)
                }
            }
            Button(onClick = onPrimary, enabled = !state.isLoading.value, modifier = Modifier.weight(.7f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (onForest) TmtnHomeColor.Paper else TmtnHomeColor.Forest,
                    contentColor = if (onForest) ColorOnSurface else Color.White)) {
                Text(primary, style = TmtnType.label, textAlign = TextAlign.Center)
            }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("home-mission-group"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected) {
            if (card != null) HomeMissionCard(card, date = state.displayDateLabel()) else {
                // 조회 실패 때 예전 비버 요약이나 다른 카드로 대체하지 않는다.
                Column(Modifier.fillMaxWidth().heightIn(min = 418.dp).background(TmtnHomeColor.Paper, RoundedCornerShape(24.dp))
                    .padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("선택한 카드를 확인해 주세요", style = TmtnType.title, color = TmtnHomeColor.PaperInk)
                    Text("아래 버튼으로 카드 내용을 다시 불러올 수 있어요.", style = TmtnType.body, color = TmtnHomeColor.PaperMuted)
                }
            }
            if (rest || givenUp || paused) Text(when {
                rest -> "오늘은 쉬어가는 날이에요. 고른 카드는 남아 있어요."
                givenUp -> "오늘은 여기까지. 원하면 다시 도전할 수 있어요."
                else -> "잠시 멈췄어요. 이어서 실천할 수 있어요."
            }, style = TmtnType.caption, color = ColorOnSurfaceVariant)
            actions(false)
            if (rest) TextButton(onClick = { scope.launch { state.continueTodayMission() } }) {
                Text("오늘 카드 다시 보기", style = TmtnType.label, color = TmtnHomeColor.Forest)
            }
            if (paused) TextButton(onClick = { state.showGiveUpConfirmSheet.value = true }) {
                Text("오늘 포기", style = TmtnType.caption, color = ColorOnSurfaceVariant)
            }
        } else {
            HomeBeforeDrawCard(when { rest -> "편하게\n쉬어요."; givenUp -> "오늘은\n여기까지."; else -> "오늘도\n한 틈씩." },
                if (rest || givenUp) "원할 때 작은 실천을\n다시 골라봐요." else "작은 실천 하나를\n함께 골라봐요.") { actions(true) }
        }
        if (state.streakLoadFailed.value && (completed || rest)) TextButton(onClick = { scope.launch { state.loadStreak() } }) {
            Text("연속 기록을 새로 가져오지 못했어요 · 다시 시도", style = TmtnType.caption, color = ColorOnSurfaceVariant)
        }
    }
}

@Composable
private fun HomeBeforeDrawCard(title: String, subtitle: String, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("home-before-draw").heightIn(min = 418.dp)
        .background(TmtnHomeColor.Forest, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 카드", style = TmtnType.label, color = Color.White, modifier = Modifier.weight(1f))
            Text("하루 한 장", style = TmtnType.label, color = ColorOnSurface,
                modifier = Modifier.background(TmtnHomeColor.Paper, RoundedCornerShape(8.dp)).padding(8.dp))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 196.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = TmtnType.headline, color = Color.White)
                Text(subtitle, style = TmtnType.body, color = Color.White)
            }
            Image(painterResource(R.drawable.beaver_card), null, Modifier.size(130.dp))
        }
        Text("완료하면 댐을 쌓을 재료를 받아요", style = TmtnType.label, color = Color.White)
        actions()
    }
}

@Composable
internal fun StatusBadge(text: String) {
    val colors = LocalTmtnColors.current
    Box(Modifier.background(colors.surface, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text, style = TmtnType.caption, color = colors.onSurface)
    }
}
