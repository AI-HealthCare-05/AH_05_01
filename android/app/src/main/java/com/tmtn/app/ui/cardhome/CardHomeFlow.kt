package com.tmtn.app.ui.cardhome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.launch

/**
 * B01~B09 "오늘의 카드" 흐름 전체를 관리하는 최상위 컴포저블.
 * 온보딩과 동일한 방식(Navigation Compose 없이 상태값으로 화면 전환).
 *
 * hasSensorPermissions/onStartSensorTracking/onStopSensorTracking/onOpenSettings:
 * 센서형(SENSOR_*) 챌린지는 실제 폰 센서(걸음수·GPS·기압계)를 써야 해서, 그 제어는
 * MainActivity(Context 필요)에 맡기고 여기서는 콜백만 받아씀. 실시간 값 자체는
 * SensorDataHolder(전역 StateFlow)를 여기서 직접 구독함.
 *
 * onImmersiveChange: HANDOFF.md §1 — B06(카드 공개)은 "하단 내비 없는 몰입 화면".
 * 이 흐름 안에서 지금 하단 내비를 보여줘도 되는 단계인지를 상위(MainActivity)에 알려줌.
 */
/** 시스템 뒤로가기(제스처/버튼) 눌렀을 때 어느 단계로 돌아갈지.
 * null이면 "여기서 더 뒤로 가면 이 화면(홈 탭) 자체를 벗어남" - 시스템 기본 동작 허용. */
private fun previousStepFor(step: CardHomeStep): CardHomeStep? = when (step) {
    CardHomeStep.LOADING -> null
    CardHomeStep.HOME -> null // 최상위 - 뒤로가면 다른 탭이나 앱 종료(시스템 기본 동작)
    CardHomeStep.DECK_PICK -> CardHomeStep.HOME
    CardHomeStep.REVEALED -> CardHomeStep.HOME
    CardHomeStep.COMPLETED -> CardHomeStep.HOME
    CardHomeStep.ERROR -> CardHomeStep.HOME
    CardHomeStep.REASON_DETAIL -> CardHomeStep.REVEALED
    CardHomeStep.ALTERNATIVE_REQUEST -> CardHomeStep.REASON_DETAIL
    CardHomeStep.ALTERNATIVE_APPLIED -> CardHomeStep.REVEALED
    CardHomeStep.NOTIFICATION_INBOX -> CardHomeStep.HOME
    CardHomeStep.REST_DAY_SHEET -> CardHomeStep.HOME
    CardHomeStep.REST_DAY_DONE -> CardHomeStep.HOME
    CardHomeStep.CHALLENGE_CHECK -> CardHomeStep.REVEALED
    CardHomeStep.CHALLENGE_CHECK_CONFIRM -> CardHomeStep.CHALLENGE_CHECK
    CardHomeStep.CHALLENGE_TIMER_START -> CardHomeStep.REVEALED
    CardHomeStep.CHALLENGE_TIMER_RUNNING -> CardHomeStep.REVEALED
    CardHomeStep.CHALLENGE_TIMER_PAUSED -> CardHomeStep.CHALLENGE_TIMER_RUNNING
    CardHomeStep.CHALLENGE_PROCESSING -> null // 처리 중엔 뒤로 못 가게(중간에 못 빠져나가도록)
    CardHomeStep.CHALLENGE_RETROSPECT -> null // 완료는 됐으니 뒤로가도 다시 진행화면 안 보여줌
    CardHomeStep.STAGE_UP -> null // 축하 화면은 "닫기" 버튼으로만 - 뒤로가기도 같은 동작
    CardHomeStep.SENSOR_INTRO -> CardHomeStep.REVEALED
    CardHomeStep.SENSOR_MEASURING -> CardHomeStep.REVEALED
    CardHomeStep.SENSOR_PERMISSION_FALLBACK -> CardHomeStep.REVEALED
    CardHomeStep.SENSOR_RESULT -> null
}

@Composable
fun CardHomeFlow(
    hasSensorPermissions: () -> Boolean,
    onStartSensorTracking: (challengeId: String, execType: String) -> Unit,
    onStopSensorTracking: () -> Unit,
    onOpenSettings: () -> Unit,
    onImmersiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalTmtnColors.current
    val state = remember { CardHomeState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        state.loadToday()
    }

    // 시스템 뒤로가기(제스처/버튼) - 화면 안의 "←" 버튼과 똑같이 동작하게.
    val previousStep = previousStepFor(state.step.value)
    if (state.step.value == CardHomeStep.STAGE_UP) {
        BackHandler { scope.launch { state.acknowledgeStageUp() } }
    } else {
        BackHandler(enabled = previousStep != null) {
            previousStep?.let { state.step.value = it }
        }
    }

    // B06/C그룹(챌린지 진행)은 몰입 화면(하단 내비 숨김), 나머지 단계는 하단 내비 유지
    val immersiveSteps = setOf(
        CardHomeStep.REVEALED, CardHomeStep.CHALLENGE_CHECK, CardHomeStep.CHALLENGE_CHECK_CONFIRM,
        CardHomeStep.CHALLENGE_TIMER_START,
        CardHomeStep.CHALLENGE_TIMER_RUNNING, CardHomeStep.CHALLENGE_TIMER_PAUSED,
        CardHomeStep.CHALLENGE_PROCESSING, CardHomeStep.CHALLENGE_RETROSPECT,
        CardHomeStep.SENSOR_INTRO, CardHomeStep.SENSOR_MEASURING,
        CardHomeStep.SENSOR_PERMISSION_FALLBACK, CardHomeStep.SENSOR_RESULT,
        CardHomeStep.STAGE_UP,
    )
    LaunchedEffect(state.step.value) {
        onImmersiveChange(state.step.value in immersiveSteps)
    }

    // 카드의 exec_type에 따라 "이 행동 시작하기"가 어디로 갈지 결정하는 공통 로직.
    // CHECK/TIMER는 새로 만든 C그룹 화면으로, 나머지(SENSOR_*)는 아직 전용 화면이 없어서
    // 기존 센서 테스트 화면(MissionTestScreen)으로 잠깐 다리 역할.
    val onStartAction: () -> Unit = {
        val card = state.revealedCard.value
        if (card != null) {
            when {
                // ⚠️ 이미 완료된 미션인데 "이 행동 시작하기"를 다시 누르면 서버가 재시작을
                // 거부해서 "측정을 시작하지 못했어요" 같은 혼란스러운 에러로 이어졌음.
                // 완료된 건 다시 볼 수만 있게 하고, 시작 동작 자체가 안 일어나게 막음.
                // SKIPPED(중단으로 끝낸 미션)도 COMPLETED와 동일하게 재시작 자체를 막음 —
                // 서버가 SKIPPED -> ACTIVE 전이를 허용하지 않아서(challenge_service.skip),
                // 여기서 안 막으면 TIMER_START까지 갔다가 "시작할 수 없는 상태입니다" 409로 이어짐.
                card.state == "COMPLETED" || card.state == "SKIPPED" -> state.step.value = CardHomeStep.COMPLETED
                card.exec_type == "CHECK" -> state.step.value = CardHomeStep.CHALLENGE_CHECK
                // ⚠️ TIMER형도 SENSOR형과 같은 이유로 "이미 진행 중인지" 확인이 필요함.
                // 뒤로가기로 REVEALED까지 나왔다가 다시 "이 행동 시작하기"를 누르면 서버는
                // 여전히 ACTIVE(또는 PAUSED)인데 매번 TIMER_START(0:00)로 보내서 startTimer()가
                // startChallenge를 또 호출 -> 이미 ACTIVE라 409("시작할 수 없는 상태입니다")로
                // 이어지던 버그. card.state는 startTimer/pauseTimer/resumeTimer가 성공할 때마다
                // 같이 갱신해두므로, 여기서 그 값을 보고 진행 중이던 화면으로 바로 이어줌.
                card.exec_type == "TIMER" -> state.step.value = when (card.state) {
                    "ACTIVE" -> CardHomeStep.CHALLENGE_TIMER_RUNNING
                    "PAUSED" -> CardHomeStep.CHALLENGE_TIMER_PAUSED
                    else -> CardHomeStep.CHALLENGE_TIMER_START
                }
                else -> {
                    // Figma C17(앱 복구)의 축소판: 같은 챌린지를 이미 측정 중이면(예: 화면
                    // 전환 중에 실수로 뒤로 갔다가 다시 시작 누른 경우) 처음부터 다시 시작하는
                    // 게 아니라 진행 중이던 화면으로 바로 이어줌.
                    // ⚠️ 앱이 완전히 꺼졌다가 재실행된 경우의 진짜 복구는 "이미 선택된 챌린지
                    // 상세 재조회 API"가 없어서 아직 안 됨 (B01b 만들 때 적어둔 것과 같은 제약).
                    val alreadyTracking =
                        com.tmtn.app.sensor.CurrentChallengeHolder.challengeId == card.challenge_id &&
                            com.tmtn.app.sensor.CurrentChallengeHolder.execType != null
                    state.step.value = if (alreadyTracking) CardHomeStep.SENSOR_MEASURING else CardHomeStep.SENSOR_INTRO
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (state.step.value) {
            CardHomeStep.LOADING -> LoadingScreen()
            CardHomeStep.HOME -> CardHomeScreen(state, scope)
            CardHomeStep.DECK_PICK -> DeckPickScreen(
                state, scope,
                onBack = { state.step.value = CardHomeStep.HOME; state.resetPick() },
            )
            CardHomeStep.REVEALED -> RevealScreen(state, scope, onStartAction)
            CardHomeStep.COMPLETED -> CompletedScreen(state)
            CardHomeStep.ERROR -> CardErrorScreen(state, scope)
            CardHomeStep.REASON_DETAIL -> ReasonDetailScreen(
                state, onBack = { state.step.value = CardHomeStep.REVEALED }, onStartAction = onStartAction,
            )
            CardHomeStep.ALTERNATIVE_REQUEST -> AlternativeRequestScreen(
                state, onBack = { state.step.value = CardHomeStep.REASON_DETAIL },
            )
            CardHomeStep.ALTERNATIVE_APPLIED -> AlternativeAppliedScreen(state, onStartAction = onStartAction)
            CardHomeStep.NOTIFICATION_INBOX -> NotificationInboxScreen(
                onBack = { state.step.value = CardHomeStep.HOME },
            )
            CardHomeStep.REST_DAY_SHEET -> RestDaySheetScreen(state, scope)
            CardHomeStep.REST_DAY_DONE -> RestDayDoneScreen(state, scope)
            CardHomeStep.CHALLENGE_CHECK -> CheckChallengeScreen(state, scope)
            CardHomeStep.CHALLENGE_CHECK_CONFIRM -> CheckCompleteConfirmScreen(state, scope)
            CardHomeStep.CHALLENGE_TIMER_START -> TimerStartScreen(state, scope)
            CardHomeStep.CHALLENGE_TIMER_RUNNING -> TimerRunningScreen(state, scope)
            CardHomeStep.CHALLENGE_TIMER_PAUSED -> TimerPausedScreen(state, scope)
            CardHomeStep.CHALLENGE_PROCESSING -> ChallengeProcessingScreen(state)
            CardHomeStep.CHALLENGE_RETROSPECT -> RetrospectScreen(state, scope)
            CardHomeStep.STAGE_UP -> {
                val pending = state.stageUpPending.value
                if (pending != null) {
                    com.tmtn.app.ui.dam.StageUpCelebrationScreen(
                        pending = pending,
                        onGoToDam = { scope.launch { state.acknowledgeStageUp() } },
                        onClose = { scope.launch { state.acknowledgeStageUp() } },
                    )
                }
            }
            CardHomeStep.SENSOR_INTRO -> SensorIntroScreen(state, scope, hasSensorPermissions, onStartSensorTracking)
            CardHomeStep.SENSOR_MEASURING -> SensorMeasuringScreen(state, onStopSensorTracking)
            CardHomeStep.SENSOR_PERMISSION_FALLBACK -> SensorPermissionFallbackScreen(state, onOpenSettings)
            CardHomeStep.SENSOR_RESULT -> SensorResultScreen(state, scope)
        }

        state.errorMessage.value?.let { message ->
            if (state.step.value != CardHomeStep.ERROR) {
                Surface(
                    color = colors.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                ) {
                    Text("⚠️ $message", style = TmtnType.caption, color = colors.error, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (state.isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        }
    }
}

/** Figma B08 · 덱 불러오는 중 */
@Composable
private fun LoadingScreen() {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("2026. 8. 27. 목요일", style = TmtnType.caption, color = colors.onSurfaceVariant)
        Box(
            modifier = Modifier.fillMaxWidth().height(32.dp)
                .background(colors.outlineVariant, RoundedCornerShape(8.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(240.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier.width(108.dp).fillMaxHeight()
                        .background(colors.outlineVariant, RoundedCornerShape(12.dp)),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(72.dp)
                .background(colors.outlineVariant, RoundedCornerShape(12.dp)),
        )
        Text("오늘의 카드를 가져오는 중입니다…", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}
