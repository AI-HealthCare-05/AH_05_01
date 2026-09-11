package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private data class SensorDisplay(val value: String, val target: String, val progress: Float, val isActive: Boolean)

/** Figma C09 · 자동 측정 · 공통 안내 */
@Composable
fun SensorIntroScreen(
    state: CardHomeState,
    scope: kotlinx.coroutines.CoroutineScope,
    hasSensorPermissions: () -> Boolean,
    onStartSensorTracking: (challengeId: String, execType: String, resumeCount: Int, targetValue: Int) -> Unit,
    // ⚠️ 2026-09-08 QA(N6) 반영: 여기서 처음으로 시스템 권한 다이얼로그를 띄움(앱 시작
    // 시점이 아니라).
    onRequestPermissions: () -> Unit = {},
) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 행동", onBack = { state.step.value = CardHomeStep.REVEALED })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(card.title, style = TmtnType.headline, color = colors.onSurface)
            // ⚠️ 2026-09-06 QA(P1-7) 반영: SENSOR형도 target_value/unit이 한 번도
            // 안 쓰였음 - "아침 산책하기"가 몇 분인지, 몇 걸음인지 안 보였음.
            Text("목표 ${card.target_value}${card.unit}", style = TmtnType.body, color = colors.onSurfaceVariant)

            // ⚠️ 2026-09-08 QA(9번) 반영: "위치정보 수집·이용 동의"(선택 동의)를 거부해도
            // 센서 미션이 그대로 활성화돼 있었음. 동의가 없으면 안내 배너를 먼저 보여주고,
            // 아래 "시작하기"도 시스템 권한 요청 없이 곧장 "직접 체크로" 안내로 감.
            if (!state.locationConsentGranted.value) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.disabledContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("위치정보 동의가 꺼져 있어요", style = TmtnType.label, color = colors.onSurface)
                    Text(
                        "자동 측정 대신 직접 체크로 완료할 수 있어요. 동의는 내 정보 > 약관·동의에서 다시 켤 수 있어요.",
                        style = TmtnType.caption, color = colors.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("이 미션은 자동으로 측정해요", style = TmtnType.label, color = colors.onSurface)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("무엇을 읽고 무엇을 읽지 않나요", style = TmtnType.label, color = colors.onSurface)
                Text("읽습니다: 움직인 시간 · 걸음 수", style = TmtnType.body, color = colors.onSurfaceVariant)
                Text("읽지 않습니다: 위치 기록 · 심박 · 연락처 · 사진", style = TmtnType.body, color = colors.onSurfaceVariant)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("이렇게 동작해요", style = TmtnType.label, color = colors.onSurface)
                Text(
                    "· 시작 버튼을 누른 뒤부터 측정하고, 끝내기를 누르면 멈춰요.\n" +
                        "· 움직임이 멈추면 시간이 자동으로 쉬고, 다시 움직이면 이어서 세요.\n" +
                        "· 앱을 닫아도 알림으로 상태를 보여드려요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.rewardContainer, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "절전 모드나 앱을 강제로 종료하면 측정이 멈출 수 있습니다. 배터리를 조금 더 씁니다.",
                    style = TmtnType.label, color = colors.onSurface,
                )
            }

            TmtnPrimaryButton(
                // ⚠️ 2026-09-04 반영: "측정 시작"이라 타이머형("시작하기")·체크형과 문구가
                // 달라서 통일감이 없었음 - 자가진단/타이머/센서 다 "시작하기"로 통일.
                text = "시작하기",
                onClick = {
                    if (!state.locationConsentGranted.value) {
                        // ⚠️ 2026-09-08 QA(9번) 반영: 동의가 없으면 시스템 권한 요청 자체를
                        // 안 하고 곧장 "직접 체크로" 폴백 안내로 보냄.
                        state.step.value = CardHomeStep.SENSOR_PERMISSION_FALLBACK
                    } else if (hasSensorPermissions()) {
                        // ⚠️ 2026-09-06 반영: exec_type에 따라 "이어서 셀" 기준값이 다름 -
                        // 걸음수·계단·거리(COUNT형)는 accumulated_count(개수), 걷기·뛰기
                        // 시간(DURATION형)은 elapsed_seconds(초)를 넘겨야 정확함.
                        val resumeValue = when (card.exec_type) {
                            "SENSOR_WALKING_DURATION", "SENSOR_RUNNING_DURATION" -> card.elapsed_seconds
                            else -> card.accumulated_count
                        }
                        // 서버에 "시작" 알린 뒤에만 실제 센서 서비스를 켬 - 순서 중요
                        // (서버 READY -> ACTIVE 전환 없이 GPS/센서부터 켜면 완료 시 목표 판정이 꼬임).
                        scope.launch {
                            // ⚠️ 2026-09-04 반영: 이 화면은 서비스가 꺼진 뒤(앱을 완전히
                            // 나갔다 들어오는 등) "이미 ACTIVE인 챌린지"를 다시 보여줄 때도
                            // 옴 - 그때 startChallenge()를 또 부르면 서버가 이미 ACTIVE인 걸
                            // 다시 시작시키려다 막아서(409) "측정을 시작하지 못했어요" 오류가
                            // 났음. 이미 ACTIVE면 서버 재호출 없이 로컬 추적만 다시 이어붙임.
                            if (card.state == "ACTIVE") {
                                // ⚠️ 2026-09-06 반영: 서버가 이미 갖고 있던 최신 진행값을
                                // 같이 넘겨서, 로컬 센서가 0부터 리셋되지 않고 그 값부터
                                // 이어서 세게 함 - "5초로 되돌아간 것처럼 보이던" 버그의 실제 수정.
                                onStartSensorTracking(card.challenge_id, card.exec_type, resumeValue, card.target_value)
                                state.step.value = CardHomeStep.SENSOR_MEASURING
                                return@launch
                            }
                            val response = ApiClient.cardHomeApi.startChallenge(card.challenge_id)
                            if (response.isSuccessful) {
                                onStartSensorTracking(card.challenge_id, card.exec_type, resumeValue, card.target_value)
                                state.step.value = CardHomeStep.SENSOR_MEASURING
                            } else {
                                state.errorMessage.value = "측정을 시작하지 못했어요."
                            }
                        }
                    } else if (!state.hasRequestedSensorPermissionsOnce.value) {
                        // ⚠️ 2026-09-08 QA(N6) 반영: 권한이 없다고 곧장 폴백 화면(직접
                        // 체크로 대체)으로 보내던 걸, 여기서 처음이면 먼저 시스템 권한
                        // 다이얼로그를 띄우도록 바꿈. 그래도 거부되면(사용자가 다시
                        // "시작하기"를 누를 때) 아래 분기로 폴백 화면을 보여줌.
                        state.hasRequestedSensorPermissionsOnce.value = true
                        onRequestPermissions()
                    } else {
                        state.step.value = CardHomeStep.SENSOR_PERMISSION_FALLBACK
                    }
                },
            )
            TmtnTextButton(text = "직접 체크로 할래요", onClick = { state.step.value = CardHomeStep.CHALLENGE_CHECK })
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
private fun computeSensorDisplay(
    execType: String,
    targetValue: Int,
    steps: Int,
    floors: Int,
    distanceM: Float,
    runningSeconds: Int,
    isRunningActive: Boolean,
    walkingSeconds: Int,
    isRunningDetectedNow: Boolean,
    isWalkingDetectedNow: Boolean,
    isFloorsClimbedDetectedNow: Boolean,
    isStepDetectedNow: Boolean,
): SensorDisplay {
    return when (execType) {
        "SENSOR_WALKING_DURATION" -> {
            val target = targetValue * 60
            SensorDisplay(formatMmSs(walkingSeconds), "목표 ${formatMmSs(target)}", (walkingSeconds.toFloat() / target).coerceIn(0f, 1f), isWalkingDetectedNow)
        }
        "SENSOR_RUNNING_DISTANCE" -> {
            val targetKm = targetValue / 1000f
            val km = distanceM / 1000f
            SensorDisplay("%.2f km".format(km), "목표 %.2f km".format(targetKm), (km / targetKm).coerceIn(0f, 1f), isRunningActive)
        }
        "SENSOR_RUNNING_DURATION" -> {
            val target = targetValue * 60
            SensorDisplay(formatMmSs(runningSeconds), "목표 ${formatMmSs(target)}", (runningSeconds.toFloat() / target).coerceIn(0f, 1f), isRunningDetectedNow)
        }
        // ⚠️ 2026-09-07 반영: isActive를 하드코딩 true로 둬서, 가만히 있어도 "움직임을
        // 확인했어요"가 계속 떴음(QA). 실시간 감지 여부로 교체.
        //
        // ⚠️ 2026-09-09 QA 반영: 기압 센서 배치 인정 방식 특성상 한 번에 여러 칸이
        // 몰아서 올라가면서 목표치를 훌쩍 넘어버리는 경우가 있었음("13/10칸" 식으로
        // 넘어가 보임) - 실제 측정값(floors, 서버 동기화·완료 판정용)은 그대로 두고,
        // 화면에 보여주는 숫자만 목표치에서 캡을 씌움.
        "SENSOR_FLOORS_CLIMBED" -> {
            val displayFloors = floors.coerceAtMost(targetValue)
            SensorDisplay("$displayFloors 계단", "목표 ${targetValue}계단", (floors.toFloat() / targetValue).coerceIn(0f, 1f), isFloorsClimbedDetectedNow)
        }
        else -> SensorDisplay("$steps 걸음", "목표 ${targetValue}보", 0f, isStepDetectedNow)
    }
}

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
    onForceSyncSensor: () -> Unit = {},
) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return

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
    val isRunningActive by SensorDataHolder.isRunningActive.collectAsState()
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
        isRunningActive = isRunningActive,
        walkingSeconds = walkingSeconds,
        isRunningDetectedNow = isRunningDetectedNow,
        isWalkingDetectedNow = isWalkingDetectedNow,
        isFloorsClimbedDetectedNow = isFloorsClimbedDetectedNow,
        isStepDetectedNow = isStepDetectedNow,
    )
    val targetReached = display.progress >= 1f

    // ⚠️ 요구사항 4: 목표치 도달하면 자동으로 측정을 멈춤. 이미 멈춰있으면(일시정지 상태)
    // 다시 안 부름 - isPaused를 키로 둬서 한 번만 실행되게 함.
    LaunchedEffect(targetReached, isPaused) {
        if (targetReached && !isPaused) {
            onPauseSensorTracking()
        }
    }

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
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    when {
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
                Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
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
                Text("앱을 닫아도 알림에서 상태를 볼 수 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }

            // ⚠️ 요구사항 2·4: 목표 도달 전까지는 눌러서 직접 일시정지/재개할 수 있고,
            // 목표에 도달하면(자동으로 이미 멈춘 상태) 더 이상 누를 이유가 없어 비활성화.
            TmtnTonalButton(
                text = if (isPaused) "이어서 측정" else "일시정지",
                onClick = { if (isPaused) onResumeSensorTracking() else onPauseSensorTracking() },
                enabled = !targetReached,
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
                enabled = isPaused,
            )
            if (state.showQuitDialog.value) {
                SensorGiveUpDialog(state, scope, onStopSensorTracking)
            }
            // ⚠️ 요구사항 5: 목표치에 도달했을 때만 활성화.
            TmtnPrimaryButton(
                text = "완료하기",
                onClick = {
                    // ⚠️ 2026-09-08 반영: 30초 배치 동기화 타이밍에만 기대다가, 계단을 다
                    // 오르고 곧바로 완료를 누르면 서버가 아직 오래된 값(예: 9칸)만 알고
                    // 있는 상태에서 "목표 미달성"으로 튕기는 문제가 있었음(로그로 확인 -
                    // 완료 시점엔 누적=0이었다가 한참 뒤에야 반영됨). 완료 직전에 서버로
                    // 즉시 동기화를 요청하고, 그게 처리될 시간을 준 다음에 완료를 시도함.
                    onForceSyncSensor()
                    onStopSensorTracking()
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        state.completeTimerChallenge()
                    }
                },
                enabled = targetReached,
            )
        }
    }
}

/** ⚠️ 2026-09-07 추가: "측정 끝내기"(포기) 확인 - 실수로 곧장 포기가 확정되지 않게,
 * 그리고 "자정 전이면 다시 도전 가능"이라는 정책 원칙을 여기서도 안내함. */
@Composable
private fun SensorGiveUpDialog(state: CardHomeState, scope: CoroutineScope, onStopSensorTracking: () -> Unit) {
    val colors = LocalTmtnColors.current
    AlertDialog(
        onDismissRequest = { state.showQuitDialog.value = false },
        containerColor = colors.surface,
        title = { Text("오늘 미션을 포기할까요?", style = TmtnType.bodyLarge, color = colors.onSurface) },
        text = {
            Text(
                "지금까지 잰 기록은 남습니다. 자정 전이면 언제든 다시 도전할 수 있어요. " +
                    "자정을 넘기면 미완료로 연속 기록이 끊깁니다.",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            Text(
                "오늘 미션 포기", style = TmtnType.label, color = colors.error,
                modifier = Modifier.clickable {
                    onStopSensorTracking()
                    scope.launch { state.quitChallenge() }
                }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center)
                    .padding(8.dp),
            )
        },
        dismissButton = {
            Text(
                "계속하기", style = TmtnType.label, color = colors.primary,
                modifier = Modifier.clickable { state.showQuitDialog.value = false }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center)
                    .padding(8.dp),
            )
        },
    )
}

/** Figma C16 · 자동 측정 · 권한 거부 · 미지원 - 직접 체크로 대체 진행 */
@Composable
fun SensorPermissionFallbackScreen(state: CardHomeState, onOpenSettings: () -> Unit) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    val material = MATERIAL_NAMES[card.five_element]

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
                Text("자동 측정에 필요한 권한이 없어요", style = TmtnType.label, color = colors.onSurface)
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
            TmtnOutlinedButton(text = "설정 열기", onClick = onOpenSettings)
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
        // ⚠️ 2026-09-09 QA 반영: 실제 측정값이 목표를 넘어가는 경우("13계단 올랐어요"인데
        // 목표는 10칸) 표시가 어색해서, 완료 요약도 목표치에서 캡을 씌움.
        "SENSOR_FLOORS_CLIMBED" -> "오늘 ${floors.coerceAtMost(card.target_value)}계단 올랐어요"
        else -> "오늘 ${steps}걸음 걸었어요"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘 완료", onBack = { })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
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
