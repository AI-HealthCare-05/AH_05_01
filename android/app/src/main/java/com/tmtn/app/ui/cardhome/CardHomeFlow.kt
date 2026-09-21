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
import kotlinx.coroutines.CoroutineScope
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
internal fun previousStepFor(step: CardHomeStep, extraListOrigin: CardHomeStep = CardHomeStep.HOME): CardHomeStep? = when (step) {
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
    // ⚠️ 2026-09-08 반영: 예전엔 여기가 CHALLENGE_TIMER_RUNNING이었음 - 일시정지 화면에서
    // 뒤로가기를 누르면 "이어하기"가 돼서 방금 멈춘 타이머가 다시 흐르기 시작했음. 사용자가
    // 일부러 멈춰둔 걸 뒤로가기로 되돌려버리는 셈이라 놀라는 동작이었음. 뒤로가기는 "이 화면에서
    // 나간다"는 뜻이어야 하므로 진행 중 화면(CHALLENGE_TIMER_RUNNING)과 똑같이 카드 화면으로
    // 나가고, 일시정지 상태는 서버·로컬 모두 그대로 유지됨(정책상 PAUSED = 중단, 진행값 보존).
    CardHomeStep.CHALLENGE_TIMER_PAUSED -> CardHomeStep.REVEALED
    CardHomeStep.CHALLENGE_PROCESSING -> null // 처리 중엔 뒤로 못 가게(중간에 못 빠져나가도록)
    CardHomeStep.CHALLENGE_RETROSPECT -> null // 완료는 됐으니 뒤로가도 다시 진행화면 안 보여줌
    CardHomeStep.STAGE_UP -> null // 축하 화면은 "닫기" 버튼으로만 - 뒤로가기도 같은 동작
    CardHomeStep.SENSOR_INTRO -> CardHomeStep.REVEALED
    CardHomeStep.SENSOR_MEASURING -> CardHomeStep.REVEALED
    CardHomeStep.SENSOR_PERMISSION_FALLBACK -> CardHomeStep.REVEALED
    CardHomeStep.SENSOR_RESULT -> null

    // 목록은 들어온 화면으로 돌아간다. 측정 화면은 홈으로 나가며 세션은 유지한다.
    // 재실행으로 복원한 세션에는 selectedExerciseOption이 없으므로 상세 화면으로 보내지 않는다.
    CardHomeStep.EXTRA_LIST -> extraListOrigin
    CardHomeStep.EXTRA_DETAIL -> CardHomeStep.EXTRA_LIST
    CardHomeStep.EXTRA_RUNNING -> CardHomeStep.HOME
    CardHomeStep.EXTRA_REWARD -> null
}

@Composable
fun CardHomeFlow(
    // ⚠️ 2026-09-07 반영(타이머 "1초 리셋" 버그의 실제 원인 중 하나): 예전엔 이 안에서
    // remember { CardHomeState() }로 직접 만들었음. MainActivity가 하단 탭을
    // when(currentTab) { HOME -> CardHomeFlow(...) ... }로 갈라서 그리고 있는데, Compose는
    // when의 각 분기를 서로 다른 컴포지션으로 취급함 - "홈" 탭에서 다른 탭(기록/참고/댐/
    // 내 정보)으로 갔다가 "홈" 탭으로 돌아오면 그 사이 CardHomeFlow 전체가 한 번 완전히
    // 해체(dispose)됐다가 다시 생성돼서, remember로 들고 있던 CardHomeState(진행 중이던
    // 타이머의 timerElapsedSeconds 포함)가 통째로 사라지고 새 CardHomeState()로 바뀜.
    // 돌아오면 곧바로 최초 진입과 똑같이 loadToday()가 다시 불려서 화면이 홈으로
    // 리셋되고, 그 뒤 진행 화면으로 다시 들어가는 과정에서 서버 값과 어긋나 보이는 게
    // "다른 화면 갔다 오면 1초로 바뀐다"는 재현 보고의 실제 경로였음. state를 여기서
    // 만들지 않고 MainActivity(탭 전환과 무관하게 살아있는 상위 컴포저블)가 만들어서
    // 넘겨주는 방식으로 바꿔서, 탭을 오가도 같은 CardHomeState 인스턴스가 그대로 유지되게 함.
    state: CardHomeState,
    hasSensorPermissions: (String) -> Boolean,
    // ⚠️ 2026-09-08 QA(N6) 반영: SensorIntroScreen 참고.
    onRequestSensorPermissions: (String) -> Unit = {},
    onStartSensorTracking: (challengeId: String, execType: String, resumeCount: Int, targetValue: Int) -> Unit,
    onStopSensorTracking: () -> Unit,
    // ⚠️ 2026-09-11 추가 - 틈새 운동(제자리걸음) 전용. 오늘의 카드 센서 추적과 독립적.
    onStartStepInPlace: () -> Unit = {},
    onStopStepInPlace: () -> Unit = {},
    // ⚠️ 2026-09-16 추가(QA) - "이어하기"용, 위 onStartStepInPlace와 별개 콜백.
    onStartStepInPlaceResume: (Int) -> Unit = {},
    onStartWalking: () -> Unit = {},
    // ⚠️ 2026-09-16 추가(QA) - "천천히 걷기" 이어하기용.
    onStartWalkingResume: (Int) -> Unit = {},
    onStopWalking: () -> Unit = {},
    onStartRunningDistance: () -> Unit = {},
    onStartRunningDistanceResume: (Int) -> Unit = {},
    onStartRunningDuration: () -> Unit = {},
    onStartRunningDurationResume: (Int) -> Unit = {},
    onStopRunning: () -> Unit = {},
    onStartStairs: () -> Unit = {},
    onStartStairsResume: (Int) -> Unit = {},
    onStopStairs: () -> Unit = {},
    // ⚠️ 2026-09-04 추가: 센서 측정 일시정지/재개 - TIMER형과 같은 일시정지 개념을 센서형에도 적용.
    onPauseSensorTracking: () -> Unit = {},
    onResumeSensorTracking: () -> Unit = {},
    // ⚠️ 2026-09-08 추가: 완료 직전 강제 동기화 - SensorMeasuringScreen 참고.
    onForceSyncSensor: suspend () -> Boolean = { true },
    onOpenSettings: () -> Unit,
    onImmersiveChange: (Boolean) -> Unit = {},
    // ⚠️ 2026-09-08 QA 반영: 홈의 "틈튼지수 자세히" 캡션이 그냥 Text라 클릭 자체가 안
    // 되고 있었음(TODO만 남아있던 미구현). 틈튼지수 탭(MainTab.REFERENCE)으로 전환.
    onOpenTuntunScore: () -> Unit = {},
    onOpenDam: () -> Unit = {},
    // ⚠️ 온보딩 완료 직후 "오늘의 카드 보러 가기"를 누르면, 홈(마스코트 카드)에서
    // "오늘의 카드 고르기"를 한 번 더 누르게 하는 대신 카드 고르는 화면으로 바로 이어줌.
    startAtDeckPick: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTmtnColors.current
    val scope = rememberCoroutineScope()
    com.tmtn.app.ui.common.OnAppForeground {
        scope.launch { state.refreshHomeOnReturn() }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { onImmersiveChange(false) }
    }

    LaunchedEffect(Unit) {
        if (state.cardEntryRequested.value) {
            state.cardEntryRequested.value = false
            state.openCardFromJournal()
            return@LaunchedEffect
        }
        // ⚠️ 2026-09-07 반영(위 state 파라미터 주석과 짝): state가 이제 MainActivity에서
        // 넘어와 탭을 오가도 살아있으므로, 이 LaunchedEffect(Unit) 자체는 (다른 탭 갔다가
        // 돌아올 때마다) 매번 다시 실행되더라도 "최초 진입"과 "탭에서 돌아옴"을 구분해야
        // 함 - 안 그러면 이미 타이머가 흐르고 있던 화면인데도 매번 loadToday()가 다시
        // step을 LOADING -> HOME으로 되돌려버려서, 방금 전까지 보고 있던 진행 화면이
        // 사라지고 홈으로 튕겨 보임(setId가 null인지로 "이 CardHomeState로 한 번이라도
        // 로딩한 적 있는지" 판단 - 앱 켜고 이 탭에 처음 들어올 때만 null).
        if (state.setId.value == null) {
            state.loadToday()
            // ⚠️ 2026-09-16 추가(QA F06) - "프로세스 종료·재부팅을 복구할 영속 세션이
            // 없다"는 지적 대응. 예전엔 사용자가 "틈새 운동 둘러보기"를 직접 눌러야만
            // active_session을 확인했음(ExerciseMissionListScreen의 LaunchedEffect) -
            // 앱이 강제 종료됐다가 재시작되면, 진행 중이던 틈새 운동이 있어도 사용자가
            // 스스로 그 화면을 다시 찾아 들어가야만 이어하기가 발동했음. 별도 Room
            // 로컬 저장까지는 안 만들고(서버가 이미 ACTIVE/PAUSED의 authoritative 값을
            // 갖고 있으니), 앱 최초 진입 시 이 조회를 여기서도 하게 해서 서버 상태와
            // 항상 맞춰지게 함 - loadExerciseMissionsToday() 자체가 이미 active_session
            // 발견 시 자동으로 EXTRA_RUNNING까지 이어주는 로직을 갖고 있음.
            state.loadExerciseMissionsToday()
            // ⚠️ 이미 오늘 카드를 고른 상태(isSelected/완료/쉼 등)면 건너뛰고 그대로 홈을 보여줌 -
            // 온보딩 막 끝낸 신규 계정에서만 실제로 의미가 있는 분기.
            if (startAtDeckPick && state.step.value == CardHomeStep.HOME && state.todayChallengeId.value == null) {
                state.step.value = CardHomeStep.DECK_PICK
            }
        } else if (
            state.step.value == CardHomeStep.CHALLENGE_TIMER_RUNNING ||
            state.step.value == CardHomeStep.CHALLENGE_TIMER_PAUSED
        ) {
            // ⚠️ 다른 탭에 있는 동안은 이 화면(TimerRunningScreen)의 1초마다 더하는
            // LaunchedEffect 자체가 해체돼 있어서 로컬 카운트가 멈춰 있었음 - 돌아오자마자
            // 서버가 계산한 진짜 경과 시간으로 맞춰서, 탭에 가 있던 동안 실제로 흐른
            // 시간이 반영되게 함(syncTimerElapsedFromServer()는 실패해도 조용히 로컬 값을
            // 유지하니 화면이 깨질 걱정은 없음).
            state.syncTimerElapsedFromServer()
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
    val previousStep = previousStepFor(state.step.value, state.extraListOrigin.value)
    androidx.activity.compose.BackHandler(enabled = state.step.value == CardHomeStep.CHALLENGE_PROCESSING) { }
    // ⚠️ 2026-09-07 반영: 쉬어가기·전환 시트들(showRestDaySheet 등)은 step과 별개인
    // 오버레이라서, 시트가 열려 있어도 아래 when(state.step.value)는 그걸 전혀 모름 -
    // 홈(HOME)에서 시트를 열면 previousStepFor(HOME)이 null이라 뒤로가기가 그대로
    // 시스템 기본 동작(앱 종료)으로 흘러갔음. 열려 있는 시트가 있으면 그것부터 닫는 걸
    // 최우선으로 처리(화면 안의 "닫기"/스크림 클릭과 동일한 동작).
    if (state.showRestDaySheet.value) {
        BackHandler { state.closeRestDaySheet() }
    } else if (state.showRestCancelSheet.value) {
        BackHandler { if (!state.isLoading.value) { state.showRestCancelSheet.value = false; state.errorMessage.value = null } }
    } else if (state.showRestToGiveUpSheet.value) {
        BackHandler { if (!state.isLoading.value) { state.showRestToGiveUpSheet.value = false; state.errorMessage.value = null } }
    } else if (state.showGiveUpConfirmSheet.value) {
        BackHandler { if (!state.isLoading.value) { state.showGiveUpConfirmSheet.value = false; state.errorMessage.value = null } }
    } else if (state.showCheckGiveUpDialog.value) {
        BackHandler { if (!state.isLoading.value) { state.showCheckGiveUpDialog.value = false; state.errorMessage.value = null } }
    } else {
        when (state.step.value) {
            CardHomeStep.STAGE_UP -> BackHandler { scope.launch { state.acknowledgeStageUp() } }
            // ⚠️ 2026-09-08 반영: 여기 있던 CHALLENGE_TIMER_PAUSED 전용 처리(뒤로가기 =
            // resumeTimer())를 없앰. 원래는 "뒤로가기가 step만 진행 화면으로 바꿔서 서버는
            // PAUSED인데 화면만 진행 중으로 보이던" 어긋남을 막으려고 넣은 것이었는데, 그
            // 방식은 사용자가 일부러 멈춰둔 타이머를 뒤로가기로 다시 흐르게 만드는 부작용이
            // 있었음. 이제 previousStepFor(CHALLENGE_TIMER_PAUSED)가 진행 화면이 아니라
            // 카드 화면(REVEALED)을 가리키므로 "화면만 진행 중" 상태 자체가 생기지 않음 -
            // 서버도 로컬도 PAUSED 그대로 유지되고, 다시 들어오면 일시정지 화면으로 복귀함.
            else -> BackHandler(enabled = previousStep != null) {
                previousStep?.let { state.step.value = it }
            }
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

    Column(modifier = modifier.fillMaxSize()) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (state.step.value) {
            CardHomeStep.LOADING -> LoadingScreen()
            CardHomeStep.HOME -> HomeStepDispatch(state, scope, onOpenTuntunScore, onOpenDam)
            CardHomeStep.DECK_PICK -> DeckPickScreen(
                state, scope,
                onBack = { state.step.value = CardHomeStep.HOME; state.resetPick() },
            )
            CardHomeStep.REVEALED -> RevealScreen(
                state, scope, onStartAction,
                onRestartFromGiveUp = { scope.launch { state.restartFromGiveUp() } },
            )
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
                        onGoToDam = { scope.launch { state.acknowledgeStageUp(onOpenDam) } },
                        onClose = { scope.launch { state.acknowledgeStageUp() } },
                        busy = state.isLoading.value,
                    )
                }
            }
            CardHomeStep.SENSOR_INTRO -> SensorIntroScreen(
                state, scope, hasSensorPermissions, onStartSensorTracking, onRequestSensorPermissions,
            )
            CardHomeStep.SENSOR_MEASURING -> SensorMeasuringScreen(
                state, scope, onStopSensorTracking, onPauseSensorTracking, onResumeSensorTracking,
                onForceSyncSensor,
            )
            CardHomeStep.SENSOR_PERMISSION_FALLBACK -> SensorPermissionFallbackScreen(state, onOpenSettings)
            CardHomeStep.SENSOR_RESULT -> SensorResultScreen(state, scope)
            CardHomeStep.EXTRA_LIST -> ExerciseMissionListScreen(state, scope)
            // ⚠️ 2026-09-17 추가(QA Q03/Q04 - PR #21 패키지) - "오늘의 카드"
            // SensorIntroScreen과 같은 hasSensorPermissions/onRequestSensorPermissions를
            // 틈새운동 쪽에도 넘긴다. 예전엔 이 두 화면이 권한을 아예 확인하지 않아서,
            // 신체 활동 권한이 없어도 "측정 중" 화면으로 그냥 들어갔었음.
            CardHomeStep.EXTRA_DETAIL -> ExerciseMissionDetailScreen(
                state, scope, hasSensorPermissions, onRequestSensorPermissions,
            )
            CardHomeStep.EXTRA_RUNNING -> ExerciseMissionRunningScreen(
                state, scope, hasSensorPermissions,
                onStartStepInPlace, onStopStepInPlace,
                onStartWalking, onStopWalking, onStartRunningDistance, onStartRunningDuration, onStopRunning,
                onStartStairs, onStopStairs, onStartStepInPlaceResume, onStartWalkingResume,
                onStartRunningDistanceResume, onStartRunningDurationResume, onStartStairsResume,
            )
            CardHomeStep.EXTRA_REWARD -> ExerciseMissionRewardScreen(state, onOpenDam)
        }

        // ⚠️ B16(오늘 쉬어가기)은 진짜 바텀시트여야 함 — "화면"으로 취급해서 REST_DAY_SHEET라는
        // 별도 step으로 만들었더니, 지금 보고 있던 화면을 통째로 갈아치워버려서 "새 창처럼"
        // 보이고, "닫기"를 눌러도 원래 있던 화면이 아니라 무조건 홈으로 튕겼음. D그룹의
        // DayDetailSheet(RecordFlow.kt)와 같은 방식으로, 지금 화면 위에 반투명 스크림 +
        // 오버레이로 띄워서 "닫기"를 누르면 그냥 사라지고 원래 화면이 그대로 보이게 함.
        if (state.showRestDaySheet.value) {
            RestDaySheetScreen(state, scope)
        }

        // ⚠️ 2026-09-07 반영: 상태전이 정책 REST<->GIVE_UP 전환 시트 2종(TransitionSheets.kt).
        // 같은 오버레이 방식 - showRestDaySheet 오버레이와 동일한 자리.
        if (state.showRestCancelSheet.value) {
            RestCancelSheet(
                busy = state.isLoading.value,
                errorMessage = state.errorMessage.value,
                dateLabel = state.displayDateLabel().toKoreanDateLabel(),
                restTicketValue = "${state.restDaysRemainingThisWeek.value}회 → " +
                    "${(state.restDaysRemainingThisWeek.value + 1).coerceAtMost(2)}회",
                onCancelRestAndChallenge = {
                    scope.launch {
                        if (!state.cancelRestDay()) return@launch
                        state.showRestCancelSheet.value = false
                        // ⚠️ 2026-09-07 반영: B18/B19(카드 미선택)에서 열렸으면 고를 카드가
                        // 아직 없으니 DECK_PICK으로, B20/B23(카드 이미 뽑음)에서 열렸으면
                        // 그 카드로 곧장 이어서 진행 화면까지 들어가야 함 - 하나의 시트를
                        // 여러 홈 상태가 같이 쓰므로 여기서 drawState로 분기함.
                        if (state.drawState.value == "SELECTED") {
                            state.enterInProgressMission(restartSkipped = true)
                        } else {
                            state.step.value = CardHomeStep.DECK_PICK
                        }
                    }
                },
                onKeepResting = { state.showRestCancelSheet.value = false; state.errorMessage.value = null },
                onDismiss = { state.showRestCancelSheet.value = false; state.errorMessage.value = null },
            )
        }
        if (state.showRestToGiveUpSheet.value) {
            RestToGiveUpSheet(
                busy = state.isLoading.value,
                errorMessage = state.errorMessage.value,
                dateLabel = state.displayDateLabel().toKoreanDateLabel(),
                // ⚠️ 2026-09-08 QA(N1, 배포 차단) 반영: 여기만 "쓴 횟수"(restDaysUsedThisWeek)
                // 기준으로 계산해서 "1회 → 0회"(복구인데 숫자가 줄어듦)로 보였음. 바로 위
                // RestCancelSheet는 "남은 횟수"(restDaysRemainingThisWeek) 기준으로 "1회 → 2회"
                // 였는데 여기만 기준이 달라서 반대로 보인 것 - 실제 서버 값·최종 결과는 항상
                // 맞았고(리포트에서도 확인됨), 이 문자열 조립부만 기준이 안 맞았음. 통일함.
                restTicketValue = "${state.restDaysRemainingThisWeek.value}회 → " +
                    "${(state.restDaysRemainingThisWeek.value + 1).coerceAtMost(2)}회 · 1회 돌아옴",
                onKeepResting = { state.showRestToGiveUpSheet.value = false; state.errorMessage.value = null },
                onSwitchToGiveUp = {
                    scope.launch {
                        if (!state.switchRestToGiveUp()) return@launch
                        state.showRestToGiveUpSheet.value = false
                    }
                },
                onDismiss = { state.showRestToGiveUpSheet.value = false; state.errorMessage.value = null },
            )
        }
        // ⚠️ 2026-09-07 반영: C26(오늘 포기 확인) - HomePausedScreen(B22)의 "오늘 포기"에서 씀.
        // 이미 쉬는 중은 아니므로(그랬으면 위 RestToGiveUpSheet 쪽) 여기서는 그냥 기존
        // quitChallenge()(포기 = SKIPPED 기록)를 그대로 재사용함.
        if (state.showGiveUpConfirmSheet.value) {
            GiveUpConfirmSheet(
                busy = state.isLoading.value, errorMessage = state.errorMessage.value,
                dateLabel = state.displayDateLabel().toKoreanDateLabel(),
                restDaysRemaining = state.restDaysRemainingThisWeek.value,
                onRestInstead = {
                    state.showGiveUpConfirmSheet.value = false
                    state.errorMessage.value = null
                    scope.launch { state.openRestDaySheet() }
                },
                onGiveUp = {
                    scope.launch { state.quitChallenge() }
                },
                onDismiss = { state.showGiveUpConfirmSheet.value = false; state.errorMessage.value = null },
            )
        }

      }
        val modalOwnsFeedback = (state.step.value == CardHomeStep.DECK_PICK && state.showConfirmDialog.value) ||
            state.showRestDaySheet.value || state.showRestCancelSheet.value || state.showRestToGiveUpSheet.value || state.showGiveUpConfirmSheet.value ||
            state.showQuitDialog.value || state.showCheckGiveUpDialog.value
        if (state.step.value != CardHomeStep.ERROR && !modalOwnsFeedback) {
            com.tmtn.app.ui.onboarding.OnboardingErrorMessage(state.errorMessage.value)
        }

        if (state.isLoading.value && state.step.value != CardHomeStep.CHALLENGE_PROCESSING && !modalOwnsFeedback) {
            com.tmtn.app.ui.onboarding.OnboardingSavingDialog()
        }
    }
}

/** 확정 시안 C를 쉼·포기·일시정지 상태에도 공통 적용한다. 상태 전이 콜백은 CardHomeScreen에 유지한다. */
@Composable
private fun HomeStepDispatch(state: CardHomeState, scope: CoroutineScope, onOpenTuntunScore: () -> Unit = {}, onOpenDam: () -> Unit = {}) {
    CardHomeScreen(state, scope, onOpenTuntunScore, onOpenDam = onOpenDam)
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


