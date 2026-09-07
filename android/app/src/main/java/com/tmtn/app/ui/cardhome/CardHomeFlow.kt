package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    // ⚠️ 2026-09-04 추가: 센서 측정 일시정지/재개 - TIMER형과 같은 일시정지 개념을 센서형에도 적용.
    onPauseSensorTracking: () -> Unit = {},
    onResumeSensorTracking: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onImmersiveChange: (Boolean) -> Unit = {},
    // ⚠️ 온보딩 완료 직후 "오늘의 카드 보러 가기"를 누르면, 홈(마스코트 카드)에서
    // "오늘의 카드 고르기"를 한 번 더 누르게 하는 대신 카드 고르는 화면으로 바로 이어줌.
    startAtDeckPick: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTmtnColors.current
    val state = remember { CardHomeState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        state.loadToday()
        // ⚠️ 이미 오늘 카드를 고른 상태(isSelected/완료/쉼 등)면 건너뛰고 그대로 홈을 보여줌 -
        // 온보딩 막 끝낸 신규 계정에서만 실제로 의미가 있는 분기.
        if (startAtDeckPick && state.step.value == CardHomeStep.HOME && state.todayChallengeId.value == null) {
            state.step.value = CardHomeStep.DECK_PICK
        }
    }

    // ⚠️ 2026-09-04 QA(N-2) 반영: 미션을 완료(또는 쉬어가기)하고 홈으로 돌아와도 화면이
    // "진행 중"으로 그대로 남아있던 버그. completeChallenge()/completeTimerChallenge() 등이
    // 성공해도 로컬 revealedCard.value를 안 갱신해서, HOME으로 돌아왔을 때 예전 값을 그대로
    // 보여주고 있었음(앱을 강제 종료하고 재실행해야만 loadToday()가 다시 불려서 반영됐음).
    // step이 HOME으로 바뀔 때마다 서버 최신 상태를 다시 불러오게 함.
    //
    // ⚠️ 2026-09-04 추가 수정: "최초 진입은 LOADING이라 중복 걱정 없다"고 적어뒀었는데
    // 틀렸음 - loadToday() 자체가 성공하면서 LOADING -> HOME으로 바꾸는 그 순간에도 이
    // effect가 똑같이 걸려서, 방금 위에서 시작한 loadToday()가 다 끝나기도 전에 또 한 번
    // loadToday()를 불렀음. 두 호출의 네트워크 요청이 겹치면서 하나가 취소(Canceled)되고,
    // 그 취소가 "실패"로 처리되면서 카드 에러 화면으로 튕기던 버그. 바로 이전 단계가
    // LOADING(=최초 진입)이었을 때는 건너뛰도록 이전 단계를 같이 추적함.
    //
    // ⚠️ 2026-09-04 재발견: 위 수정으로도 "카드를 가져오지 못했습니다"가 계속 재현됐음.
    // 진짜 원인은 이거였음 - state.loadToday()를 이 LaunchedEffect(state.step.value) 안에서
    // "직접" 실행하고 있었는데, loadToday()의 첫 줄이 step.value = LOADING으로 바꿔버림.
    // 그 순간 이 effect의 키(state.step.value)가 또 바뀌어서, Compose가 지금 막 네트워크
    // 요청 중이던 이 코루틴 자체를 취소시켜버림. 그 취소(CancellationException)가
    // loadToday() 안의 runCatching에 "진짜 실패"로 잡혀서 에러 화면으로 튕겼던 것.
    // scope.launch{}로 별도 코루틴에 띄우면, 이 effect가 재시작되는 것과 무관하게
    // 끝까지 실행됨 - 스스로 자기 자신을 취소시키는 구조를 끊어냄.
    var stepBeforeCurrent by remember { mutableStateOf(state.step.value) }
    LaunchedEffect(state.step.value) {
        val prev = stepBeforeCurrent
        stepBeforeCurrent = state.step.value
        if (state.step.value == CardHomeStep.HOME && prev != CardHomeStep.LOADING && prev != CardHomeStep.HOME) {
            scope.launch { state.loadToday() }
        }
        // ⚠️ 2026-09-04 반영: REVEALED에 뒤로가기로 들어올 때 캐시된 카드 정보가 오래돼서
        // (예: 미션 시작 이후 뒤로가기 했는데 "이 행동 시작하기"가 다시 보이는 등) 실제
        // 상태와 화면이 어긋나는 문제가 반복됐음. 어디서 오든 REVEALED에 들어올 때마다
        // 항상 서버에서 다시 받아오게 통일 - 동작을 추가할 때마다 캐시 갱신을 빠뜨릴
        // 걱정이 없어짐. HOME과 같은 이유로 scope.launch{}에 태워서, loadToday()처럼
        // step.value를 직접 바꾸지는 않지만 일관되게 이 effect 자체의 취소와 무관하게
        // 끝까지 실행되도록 함.
        if (state.step.value == CardHomeStep.REVEALED && prev != CardHomeStep.LOADING) {
            scope.launch { state.refreshRevealedCard() }
        }
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
        // REST_DAY_DONE은 지금 코드상 도달할 일이 없는 화면이지만(HOME으로 즉시 대체됨),
        // 혹시 나중에 되살릴 경우를 대비해 그대로 몰입 처리해둠.
        CardHomeStep.REST_DAY_DONE,
    )
    LaunchedEffect(state.step.value) {
        onImmersiveChange(state.step.value in immersiveSteps)
    }

    // 카드의 exec_type에 따라 "이 행동 시작하기"가 어디로 갈지 결정하는 공통 로직.
    // ⚠️ CardHomeState.stepForRevealedCard()와 같은 판단 기준을 씀(continueTodayMission()도
    // 이걸 재사용해서, "이어하기"로 오든 여기서 "시작하기"를 누르든 결과가 항상 일치함).
    val onStartAction: () -> Unit = {
        val card = state.revealedCard.value
        if (card != null) {
            // ⚠️ 2026-09-04 반영: 진행 중(ACTIVE/PAUSED)인 미션은 REVEALED 화면에 머무는
            // 동안에도 실제로 시간이 계속 흐름. 로컬에 캐시된 revealedCard(직전 화면 그대로의
            // 값)를 그대로 쓰면 그 사이 흐른 시간이 안 반영된 옛 경과 시간으로 타이머 화면이
            // 열릴 수 있음. 이 경우엔 continueTodayMission()으로 서버에서 최신 상태를 다시
            // 받아온 뒤(=적용된 화면으로) 이동. 아직 시작 전(READY 등)이면 새로 받아올 것도
            // 없으니 그대로 즉시 전환.
            if (card.state == "ACTIVE" || card.state == "PAUSED") {
                scope.launch { state.refreshAndEnterInProgressMission() }
            } else {
                state.step.value = state.stepForRevealedCard(card)
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
            CardHomeStep.SENSOR_MEASURING -> SensorMeasuringScreen(
                state, scope, onStopSensorTracking, onPauseSensorTracking, onResumeSensorTracking,
            )
            CardHomeStep.SENSOR_PERMISSION_FALLBACK -> SensorPermissionFallbackScreen(state, onOpenSettings)
            CardHomeStep.SENSOR_RESULT -> SensorResultScreen(state, scope)
        }

        // ⚠️ B16(오늘 쉬어가기)은 진짜 바텀시트여야 함 — "화면"으로 취급해서 REST_DAY_SHEET라는
        // 별도 step으로 만들었더니, 지금 보고 있던 화면을 통째로 갈아치워버려서 "새 창처럼"
        // 보이고, "닫기"를 눌러도 원래 있던 화면이 아니라 무조건 홈으로 튕겼음. D그룹의
        // DayDetailSheet(RecordFlow.kt)와 같은 방식으로, 지금 화면 위에 반투명 스크림 +
        // 오버레이로 띄워서 "닫기"를 누르면 그냥 사라지고 원래 화면이 그대로 보이게 함.
        if (state.showRestDaySheet.value) {
            RestDaySheetScreen(state, scope)
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
        Text(LocalDate.now().toKoreanDateLabel(), style = TmtnType.caption, color = colors.onSurfaceVariant)
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
