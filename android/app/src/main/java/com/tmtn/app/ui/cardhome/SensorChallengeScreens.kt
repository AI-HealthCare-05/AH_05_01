package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.ApiClient
import com.tmtn.app.sensor.SensorDataHolder
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTonalButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun formatMmSs(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}


/** Figma B24 1314:3862 / B48 1314:4518. Permissions follow the selected model. */
@Composable
fun SensorIntroScreen(
    state: CardHomeState,
    scope: CoroutineScope,
    hasSensorPermissions: (String) -> Boolean,
    onStartSensorTracking: (challengeId: String, execType: String, resumeCount: Int, targetValue: Int) -> Unit,
    onRequestPermissions: (String) -> Unit = {},
) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val distance = com.tmtn.app.sensor.SensorPermissions.needsLocation(card.exec_type)
    val duration = card.exec_type.endsWith("_DURATION")
    val permissionsGranted = hasSensorPermissions(card.exec_type)
    var starting by androidx.compose.runtime.remember(card.challenge_id) { androidx.compose.runtime.mutableStateOf(false) }
    var awaitingPermission by androidx.compose.runtime.remember(card.challenge_id) { androidx.compose.runtime.mutableStateOf(false) }
    var requestedOnce by androidx.compose.runtime.remember(card.challenge_id) { androidx.compose.runtime.mutableStateOf(false) }
    var startError by androidx.compose.runtime.remember(card.challenge_id) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val begin: () -> Unit = {
        if (!com.tmtn.app.ui.common.hasRequiredSensor(context, card.exec_type)) {
            state.sensorFallbackReason.value = "HARDWARE"
            state.step.value = CardHomeStep.SENSOR_PERMISSION_FALLBACK
        } else if (!starting) {
            starting = true
            startError = null
            scope.launch {
                try {
                    val resume = if (duration) card.elapsed_seconds else card.accumulated_count
                    if (card.state != "ACTIVE") {
                        val response = ApiClient.cardHomeApi.startChallenge(card.challenge_id)
                        if (!response.isSuccessful) error("미션을 시작하지 못했어요. 다시 시도해 주세요.")
                        state.revealedCard.value = card.copy(state = "ACTIVE")
                    }
                    onStartSensorTracking(card.challenge_id, card.exec_type, resume, card.target_value)
                    state.step.value = CardHomeStep.SENSOR_MEASURING
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { startError = "측정을 시작하지 못했어요. 연결을 확인하고 다시 눌러 주세요." }
                finally { starting = false }
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = starting) { }
    LaunchedEffect(permissionsGranted, awaitingPermission) {
        if (awaitingPermission && permissionsGranted) { awaitingPermission = false; begin() }
    }
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar(if (distance) "거리 측정 준비" else "움직임 측정 준비", { if (!starting) state.step.value = CardHomeStep.REVEALED })
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(when {
                distance -> "움직인 거리만\n차곡차곡 셀게요."
                card.exec_type == "SENSOR_RUNNING_DURATION" -> "뛸 때만\n시간이 쌓여요."
                duration -> "걸을 때만\n시간이 쌓여요."
                else -> "움직이는 만큼\n함께 셀게요."
            }, style = TmtnType.headline, color = colors.onSurface)
            Text("오늘의 카드 · ${card.title}", style = TmtnType.body, color = colors.onSurfaceVariant)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(24.dp))
                .border(1.5.dp, colors.secondary, RoundedCornerShape(24.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("틈튼 움직임 인식", style = TmtnType.label, color = colors.onSurface)
                Text("준비됐나요?", style = TmtnType.body, color = colors.onSurfaceVariant)
                Text(if (duration && card.unit == "분") formatMmSs(card.target_value * 60) else "${card.target_value} ${card.unit}", style = TmtnType.display, color = colors.onSurface)
                Text(if (duration) "목표 활동 시간" else "오늘의 목표", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text("완료하면 ${MATERIAL_NAMES[card.five_element]?.first ?: "재료"} 1개", style = TmtnType.label, color = colors.onSurface)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (distance) "1  실외의 안전한 곳에서 시작해요" else "1  휴대전화를 주머니에 넣어요", style = TmtnType.label, color = colors.onSurface)
                Text(if (distance) "정확한 위치와 달리는 움직임이 확인될 때 거리를 더해요." else "몸의 움직임을 잘 읽을 수 있는 곳에 넣어 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (distance) "2  신호가 약하면 잠시 기다려요" else "2  움직이기 시작하면 함께 셀게요", style = TmtnType.label, color = colors.onSurface)
                Text(if (duration) "멈추면 자동 대기, 다시 움직이면 이어서 측정해요." else if (distance) "신호가 돌아오면 기존 기록에서 이어서 측정해요." else "움직임이 인식되는 만큼 기록에 더해요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            if (!card.guide_text.isNullOrBlank()) {
                Text(card.guide_text, style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            if (card.exec_type == "SENSOR_STEPS_IN_PLACE") {
                Text("휴대전화를 주머니에 넣고 제자리에서 걸어 주세요. 움직임에 따라 인식에 차이가 있을 수 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }
            if (distance) {
                Text("거리 측정에는 위치정보 동의와 정확한 위치 권한이 필요해요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                if (!state.locationConsentLoaded.value) {
                    Text("위치정보 동의를 확인하지 못했어요.", style = TmtnType.label, color = colors.onSurface)
                    TmtnTonalButton("동의 상태 다시 확인", { scope.launch { state.loadLocationConsent() } })
                } else if (!state.locationConsentGranted.value) {
                    Text("내 정보 > 동의 관리에서 위치정보 동의를 켤 수 있어요. 지금은 직접 체크로 진행해도 돼요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                }
            } else Text("신체 활동 접근이 필요해요. 이 미션은 위치 없이 측정할 수 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            com.tmtn.app.ui.onboarding.OnboardingErrorMessage(startError)
            TmtnPrimaryButton(if (starting) "측정 준비 중…" else "움직임 측정 시작", {
                when {
                    distance && !state.locationConsentGranted.value -> { state.sensorFallbackReason.value = "PERMISSION"; state.step.value = CardHomeStep.SENSOR_PERMISSION_FALLBACK }
                    permissionsGranted -> begin()
                    !requestedOnce -> { requestedOnce = true; awaitingPermission = true; onRequestPermissions(card.exec_type) }
                    else -> { state.sensorFallbackReason.value = "PERMISSION"; state.step.value = CardHomeStep.SENSOR_PERMISSION_FALLBACK }
                }
            }, enabled = !starting && (!distance || state.locationConsentLoaded.value))
            TmtnTonalButton("직접 체크로 할래요", { state.step.value = CardHomeStep.CHALLENGE_CHECK }, enabled = !starting)
        }
    }
}

/**
 * ⚠️ 2026-09-08 QA 반영: 예전엔 이 함수가 SensorDataHolder의 StateFlow에서 .value를 직접
 * 읽었음. 그러면 호출한 컴포저블이 그 값을 "컴포지션 중에 읽은 것"으로 기록되지 않아서,
 * 센서값이 바뀌어도 화면이 다시 그려지지 않았음(측정 화면이 0에 멈춰 있던 실제 원인).
 * 이제 값은 전부 호출부에서 collectAsState()로 읽어 인자로 넘기고, 이 함수는 계산만 함.
 *
 * ⚠️ 2026-09-07 반영(유지): isActive("움직임을 확인했어요" 문구에 씀)는 "측정 세션이 켜져
 * 있나"가 아니라 "지금 이 순간 움직임이 감지되고 있나"를 봐야 함 - isWalkingActive/
 * isRunningActive는 세션 on/off 플래그라 멈춰 서 있어도 계속 true였음. 실시간 감지
 * 여부는 isWalkingDetectedNow/isRunningDetectedNow로 따로 봄.
 */
/** Figma C10/C12/C14 통합 · 자동 측정 · 측정 중 (exec_type별로 주요 수치만 다르게 표시)
 *
 * ⚠️ 2026-09-04 추가 요구사항 반영:
 * 1) 실제 움직임 있을 때만 시간이 흐름 - WalkingCadenceManager 등이 이미 그렇게 구현돼
 *    있었음(움직임 없으면 accumulatedXxxSeconds가 안 늘어남). 화면 쪽은 그 값을 그대로
 *    보여주기만 하면 됐음.
 * 2) "일시정지" 버튼 - 누르면 실제 측정(센서 리스너)이 멈춤.
 * 3) 일시정지하면 "측정 끝내기"가 눌리게 활성화됨(평소엔 비활성 - 한창 측정 중에 실수로
 *    눌러서 중간에 끝내버리는 걸 막기 위함, 먼저 일시정지부터 하라는 뜻).
 * 4) 목표치에 도달하면 자동으로 측정이 멈춤(TIMER형과 같은 패턴).
 * 5) 목표치에 도달하면 "완료하기" 버튼이 활성화됨.
 */
@Composable
fun SensorMeasuringScreen(
    state: CardHomeState,
    scope: CoroutineScope,
    onStopSensorTracking: () -> Unit,
    onPauseSensorTracking: () -> Unit,
    onResumeSensorTracking: () -> Unit,
    // ⚠️ 2026-09-08 반영: 완료 직전에 서버로 즉시 동기화를 요청하는 콜백. 30초 배치
    // 주기를 기다리지 않고, 지금 로컬에서 측정한 최신값을 서버가 알게 함.
    onForceSyncSensor: suspend () -> Boolean = { true },
) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    var finishing by androidx.compose.runtime.remember(card.challenge_id) { androidx.compose.runtime.mutableStateOf(false) }
    val serviceReady by SensorDataHolder.isServiceRunning.collectAsState()
    val serviceError by SensorDataHolder.serviceError.collectAsState()
    if (serviceError != null) {
        Column(Modifier.fillMaxSize()) {
            TmtnTopBar("측정 다시 시작", { state.step.value = CardHomeStep.REVEALED })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("잠깐 멈췄어요.\n다시 준비해 볼까요?", style = TmtnType.headline, color = colors.onSurface)
                Text(serviceError!!, style = TmtnType.body, color = colors.onSurfaceVariant)
                TmtnPrimaryButton("측정 준비로 돌아가기", { SensorDataHolder.setServiceError(null); state.step.value = CardHomeStep.SENSOR_INTRO })
                TmtnTonalButton("직접 체크로 할래요", { SensorDataHolder.setServiceError(null); state.step.value = CardHomeStep.CHALLENGE_CHECK })
            }
        }
        return
    }

    // ⚠️ 2026-09-08 QA 반영(측정 화면 숫자가 안 올라가던 버그의 실제 원인): 예전엔 여기서
    // collectAsState()를 부르기만 하고 그 값을 화면에서 안 읽었음("구독만 해두면 recomposition이
    // 걸린다"고 적어뒀는데 사실이 아님). Compose는 컴포지션 중에 State의 값을 **실제로 읽어야**
    // 그 컴포저블을 무효화 대상으로 기록함 - 값을 안 읽으면 흐름이 바뀌어도 화면은 그대로임.
    // 게다가 computeSensorDisplay()는 State가 아니라 원본 StateFlow의 .value를 직접 읽고
    // 있어서 구독과 완전히 무관했음.
    //
    // 그래서 실제로는 폰이 걸음을 정상적으로 세고 서버에도 올리고 있었는데 화면만 0에 멈춰
    // 있었고, "일시정지"를 눌렀을 때만 숫자가 갱신됐음 - isPaused 하나만 아래에서 실제로
    // 읽히고 있어서 그때만 recomposition이 걸렸기 때문(10초 -> 19초로 튀어 보인 이유).
    //
    // 이제 모든 값을 by로 읽어서 지역 변수에 담고, computeSensorDisplay에 인자로 넘김.
    // 컴포지션 중에 값을 읽는 게 보장되므로 센서값이 바뀔 때마다 화면이 즉시 갱신됨.
    val steps by SensorDataHolder.stepCount.collectAsState()
    val floors by SensorDataHolder.floorsClimbed.collectAsState()
    val distanceM by SensorDataHolder.runningDistanceM.collectAsState()
    val runningSeconds by SensorDataHolder.runningSeconds.collectAsState()
    val stepsInPlace by SensorDataHolder.stepInPlaceCount.collectAsState()
    val walkingSeconds by SensorDataHolder.walkingSeconds.collectAsState()
    val isRunningDetectedNow by SensorDataHolder.isRunningDetectedNow.collectAsState()
    val isWalkingDetectedNow by SensorDataHolder.isWalkingDetectedNow.collectAsState()
    val isFloorsClimbedDetectedNow by SensorDataHolder.isFloorsClimbedDetectedNow.collectAsState()
    val isStepDetectedNow by SensorDataHolder.isStepDetectedNow.collectAsState()
    val isPaused by SensorDataHolder.isSensorPaused.collectAsState()

    val display = computeSensorDisplay(
        execType = card.exec_type,
        targetValue = card.target_value,
        steps = steps,
        floors = floors,
        distanceM = distanceM,
        runningSeconds = runningSeconds,
        stepsInPlace = stepsInPlace,
        walkingSeconds = walkingSeconds,
        isRunningDetectedNow = isRunningDetectedNow,
        isWalkingDetectedNow = isWalkingDetectedNow,
        isFloorsClimbedDetectedNow = isFloorsClimbedDetectedNow,
        isStepDetectedNow = isStepDetectedNow,
    )
    val targetReached = display.progress >= 1f

    // ⚠️ 요구사항 4: 목표치 도달하면 자동으로 측정을 멈춤. 이미 멈춰있으면(일시정지 상태)
    // 다시 안 부름 - isPaused를 키로 둬서 한 번만 실행되게 함.
    LaunchedEffect(targetReached, isPaused, serviceReady) {
        if (targetReached && !isPaused && serviceReady) {
            onPauseSensorTracking()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 행동", onBack = { state.step.value = CardHomeStep.REVEALED })

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(card.title, style = TmtnType.headline, color = colors.onSurface)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    when {
                        !serviceReady -> "측정을 준비하고 있어요"
                        isPaused -> "일시정지됨"
                        display.isActive -> "움직임을 확인했어요"
                        else -> "움직임이 감지되지 않아요"
                    },
                    style = TmtnType.label, color = colors.onSurface,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(24.dp))
                    .border(2.dp, colors.secondary, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(display.value, style = TmtnType.display, color = colors.onSurface)
                Text(display.target, style = TmtnType.body, color = colors.onSurfaceVariant)
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                    contentDescription = "오늘 미션 진행"
                    progressBarRangeInfo = ProgressBarRangeInfo(display.progress, 0f..1f)
                }) {
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(colors.outline, RoundedCornerShape(4.dp)))
                    Box(
                        modifier = Modifier.fillMaxWidth(display.progress).height(8.dp)
                            .background(colors.onSurface, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    if (targetReached) "목표를 채웠어요. 완료 버튼을 눌러주세요." else "${(display.progress * 100).toInt()}%",
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
                Text("알림이 켜져 있으면 앱 밖에서도 측정 상태를 볼 수 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }

            // ⚠️ 요구사항 2·4: 목표 도달 전까지는 눌러서 직접 일시정지/재개할 수 있고,
            // 목표에 도달하면(자동으로 이미 멈춘 상태) 더 이상 누를 이유가 없어 비활성화.
            TmtnTonalButton(
                text = if (isPaused) "이어서 측정" else "일시정지",
                onClick = { if (isPaused) onResumeSensorTracking() else onPauseSensorTracking() },
                enabled = serviceReady && !targetReached && !finishing,
            )
            // ⚠️ 2026-09-04 재수정: 서버가 target_duration/target_count에 도달하기 전에는
            // 완료(complete) 자체를 아예 막아둠(challenge_service.py) - 그래서 "측정 끝내기"가
            // completeTimerChallenge()를 부르면 목표 전엔 항상 "목표에 도달하지 못했습니다"로
            // 튕기고, 실패 처리 화면(CHALLENGE_PROCESSING)에 갇히는 문제가 있었음. "측정
            // 끝내기"는 애초에 "목표 전에 그만두기"가 목적이라 완료가 아니라 중단(SKIP)이
            // 맞음 - quitChallenge()로 바꿈(다른 화면들의 "중단하기"와 같은 처리).
            //
            // ⚠️ 2026-09-07 반영: 바로 quitChallenge()(포기)를 확정해버려서, 실수로 누르면
            // 되돌릴 안내도 없이 곧장 오늘이 접혔음. "일시정지"가 이미 "나중에 이어서 하기"
            // 역할을 하고 있으니(이 버튼은 그거랑 별개로 "진짜 포기"만 의미) 확인 한 번은
            // 거치게 함.
            TmtnOutlinedButton(
                text = "측정 끝내기",
                onClick = { state.showQuitDialog.value = true },
                enabled = serviceReady && isPaused && !finishing,
            )
            if (state.showQuitDialog.value) {
                SensorGiveUpDialog(state, scope, onStopSensorTracking)
            }
            // ⚠️ 요구사항 5: 목표치에 도달했을 때만 활성화.
            TmtnPrimaryButton(
                text = if (finishing) "기록 저장 중…" else "완료하기",
                onClick = {
                    if (finishing) return@TmtnPrimaryButton
                    finishing = true
                    state.errorMessage.value = null
                    state.step.value = CardHomeStep.CHALLENGE_PROCESSING
                    // ⚠️ 2026-09-08 반영: 30초 배치 동기화 타이밍에만 기대다가, 계단을 다
                    // 오르고 곧바로 완료를 누르면 서버가 아직 오래된 값(예: 9칸)만 알고
                    // 있는 상태에서 "목표 미달성"으로 튕기는 문제가 있었음(로그로 확인 -
                    // 완료 시점엔 누적=0이었다가 한참 뒤에야 반영됨). 완료 직전에 서버로
                    // 즉시 동기화를 요청하고, 그게 처리될 시간을 준 다음에 완료를 시도함.
                    scope.launch {
                        try {
                            if (!onForceSyncSensor()) {
                                state.errorMessage.value = "기록을 아직 보내지 못했어요. 잰 값은 그대로예요. 연결을 확인하고 다시 완료해 주세요."
                                state.step.value = CardHomeStep.SENSOR_MEASURING
                                return@launch
                            }
                            state.completeTimerChallenge()
                            if (state.step.value == CardHomeStep.CHALLENGE_RETROSPECT) onStopSensorTracking()
                        } finally { finishing = false }
                    }
                },
                enabled = serviceReady && targetReached && !finishing,
            )
        }
    }
}

/** ⚠️ 2026-09-07 추가: "측정 끝내기"(포기) 확인 - 실수로 곧장 포기가 확정되지 않게,
 * 그리고 "자정 전이면 다시 도전 가능"이라는 정책 원칙을 여기서도 안내함. */
@Composable
private fun SensorGiveUpDialog(state: CardHomeState, scope: CoroutineScope, onStopSensorTracking: () -> Unit) {
    com.tmtn.app.ui.common.TmtnConfirmationDialog(
        title = "오늘 미션을 포기할까요?",
        message = "오늘 미션을 접으면 재료를 받지 않아요. 오늘 안에는 다시 도전할 수 있고, 측정은 처음부터 시작해요.",
        confirmLabel = "오늘 미션 포기", cancelLabel = "계속하기",
        busy = state.isLoading.value, error = state.errorMessage.value,
        onConfirm = { scope.launch { state.quitChallenge(onSaved = onStopSensorTracking) } },
        onDismiss = { state.showQuitDialog.value = false; state.errorMessage.value = null },
    )
}

/** Figma C16 · 자동 측정 · 권한 거부 · 미지원 - 직접 체크로 대체 진행 */
@Composable
fun SensorPermissionFallbackScreen(state: CardHomeState, onOpenSettings: () -> Unit) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    val material = MATERIAL_NAMES[card.five_element]
    // ⚠️ 2026-09-13 반영 - 지현님 팀 제안: 권한 거부(설정에서 복구 가능)와 하드웨어
    // 미지원(직접 체크만 가능)을 문구로 구분. "설정 열기" 버튼도 하드웨어 미지원일 땐
    // 눌러봐야 소용없으니 아예 숨김.
    val isPermissionIssue = state.sensorFallbackReason.value == "PERMISSION"

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 행동", onBack = { state.step.value = CardHomeStep.REVEALED })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(card.title, style = TmtnType.headline, color = colors.onSurface)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.rewardContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    if (isPermissionIssue) "자동 측정에 필요한 권한이 없어요" else "이 기기에서는 자동 측정을 할 수 없어요",
                    style = TmtnType.label, color = colors.onSurface,
                )
            }
            if (isPermissionIssue) {
                Text(
                    "설정에서 권한을 허용하면 다시 자동으로 측정할 수 있어요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("직접 체크로 진행할 수 있어요", style = TmtnType.label, color = colors.onSurface)
                Text(
                    "미션 내용과 받는 재료는 그대로입니다. 목표를 채운 뒤 완료 버튼을 눌러 주세요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
                if (material != null) {
                    Text("${material.first} 1개 · ${material.second}", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }

            TmtnPrimaryButton(text = "직접 체크로 진행하기", onClick = { state.step.value = CardHomeStep.CHALLENGE_CHECK })
            // ⚠️ 하드웨어 미지원이면 "설정 열기"를 눌러도 해결이 안 되니(권한 문제가
            // 아니므로) 버튼 자체를 안 보여줌 - 사용자가 괜히 눌렀다가 아무 변화가 없어서
            // 헷갈리는 걸 방지.
            if (isPermissionIssue) {
                TmtnOutlinedButton(text = "설정 열기", onClick = onOpenSettings)
            }
            TmtnTextButton(text = "다른 카드 고르기", onClick = { state.step.value = CardHomeStep.DECK_PICK })
        }
    }
}

/** Figma C18 · 자동 측정 · 완료 결과 */
@Composable
fun SensorResultScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    val material = MATERIAL_NAMES[card.five_element]

    val steps by SensorDataHolder.stepCount.collectAsState()
    val floors by SensorDataHolder.floorsClimbed.collectAsState()
    val distanceM by SensorDataHolder.runningDistanceM.collectAsState()
    val runningSeconds by SensorDataHolder.runningSeconds.collectAsState()
    val walkingSeconds by SensorDataHolder.walkingSeconds.collectAsState()

    val resultText = when (card.exec_type) {
        "SENSOR_WALKING_DURATION" -> "오늘 ${formatMmSs(walkingSeconds)} 움직였어요"
        "SENSOR_RUNNING_DISTANCE" -> "오늘 %.2fkm 달렸어요".format(distanceM / 1000f)
        "SENSOR_RUNNING_DURATION" -> "오늘 ${formatMmSs(runningSeconds)} 달렸어요"
        "SENSOR_FLOORS_CLIMBED" -> "오늘 ${floors}계단 올랐어요"
        else -> "오늘 ${steps}걸음 걸었어요"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘 완료", onBack = { })

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(resultText, style = TmtnType.headline, color = colors.onSurface)
                Text(card.title, style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            if (material != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text("${material.first} 1개를 모았어요", style = TmtnType.label, color = colors.onSurface)
                }
            }

            TmtnPrimaryButton(
                text = "메모 남기기",
                onClick = { scope.launch { state.completeTimerChallenge() } },
            )
            TmtnTextButton(
                text = "기록 저장하기",
                onClick = {
                    scope.launch {
                        state.completeTimerChallenge()
                        state.step.value = CardHomeStep.REVEALED
                    }
                },
            )
        }
    }
}
