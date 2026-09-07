package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onStartSensorTracking: (challengeId: String, execType: String) -> Unit,
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
                    if (hasSensorPermissions()) {
                        // 서버에 "시작" 알린 뒤에만 실제 센서 서비스를 켬 - 순서 중요
                        // (서버 READY -> ACTIVE 전환 없이 GPS/센서부터 켜면 완료 시 목표 판정이 꼬임).
                        scope.launch {
                            // ⚠️ 2026-09-04 반영: 이 화면은 서비스가 꺼진 뒤(앱을 완전히
                            // 나갔다 들어오는 등) "이미 ACTIVE인 챌린지"를 다시 보여줄 때도
                            // 옴 - 그때 startChallenge()를 또 부르면 서버가 이미 ACTIVE인 걸
                            // 다시 시작시키려다 막아서(409) "측정을 시작하지 못했어요" 오류가
                            // 났음. 이미 ACTIVE면 서버 재호출 없이 로컬 추적만 다시 이어붙임.
                            if (card.state == "ACTIVE") {
                                onStartSensorTracking(card.challenge_id, card.exec_type)
                                state.step.value = CardHomeStep.SENSOR_MEASURING
                                return@launch
                            }
                            val response = ApiClient.cardHomeApi.startChallenge(card.challenge_id)
                            if (response.isSuccessful) {
                                onStartSensorTracking(card.challenge_id, card.exec_type)
                                state.step.value = CardHomeStep.SENSOR_MEASURING
                            } else {
                                state.errorMessage.value = "측정을 시작하지 못했어요."
                            }
                        }
                    } else {
                        state.step.value = CardHomeStep.SENSOR_PERMISSION_FALLBACK
                    }
                },
            )
            TmtnTextButton(text = "직접 체크로 할래요", onClick = { state.step.value = CardHomeStep.CHALLENGE_CHECK })
        }
    }
}

private fun computeSensorDisplay(execType: String, targetValue: Int): SensorDisplay {
    val steps = SensorDataHolder.stepCount.value
    val floors = SensorDataHolder.floorsClimbed.value
    val distanceM = SensorDataHolder.runningDistanceM.value
    val runningSeconds = SensorDataHolder.runningSeconds.value
    val isRunningActive = SensorDataHolder.isRunningActive.value
    val walkingSeconds = SensorDataHolder.walkingSeconds.value
    val isWalkingActive = SensorDataHolder.isWalkingActive.value

    return when (execType) {
        "SENSOR_WALKING_DURATION" -> {
            val target = targetValue * 60
            SensorDisplay(formatMmSs(walkingSeconds), "목표 ${formatMmSs(target)}", (walkingSeconds.toFloat() / target).coerceIn(0f, 1f), isWalkingActive)
        }
        "SENSOR_RUNNING_DISTANCE" -> {
            val targetKm = targetValue / 1000f
            val km = distanceM / 1000f
            SensorDisplay("%.2f km".format(km), "목표 %.2f km".format(targetKm), (km / targetKm).coerceIn(0f, 1f), isRunningActive)
        }
        "SENSOR_RUNNING_DURATION" -> {
            val target = targetValue * 60
            SensorDisplay(formatMmSs(runningSeconds), "목표 ${formatMmSs(target)}", (runningSeconds.toFloat() / target).coerceIn(0f, 1f), isRunningActive)
        }
        "SENSOR_FLOORS_CLIMBED" -> {
            SensorDisplay("$floors 계단", "목표 ${targetValue}계단", (floors.toFloat() / targetValue).coerceIn(0f, 1f), true)
        }
        else -> SensorDisplay("$steps 걸음", "목표 ${targetValue}보", 0f, true)
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
) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return

    // exec_type별 실제 센서 데이터 구독 (표시에 씀. computeSensorDisplay에서도 값 읽음)
    SensorDataHolder.stepCount.collectAsState()
    SensorDataHolder.floorsClimbed.collectAsState()
    SensorDataHolder.runningDistanceM.collectAsState()
    val runningSecondsState by SensorDataHolder.runningSeconds.collectAsState()
    SensorDataHolder.isRunningActive.collectAsState()
    val walkingSecondsState by SensorDataHolder.walkingSeconds.collectAsState()
    SensorDataHolder.isWalkingActive.collectAsState()
    val isPaused by SensorDataHolder.isSensorPaused.collectAsState()
    // 위 구독들이 recomposition을 트리거하도록 값만 참조해 둠(walkingSecondsState 등 직접 안 써도 무방)
    val display = computeSensorDisplay(card.exec_type, card.target_value)
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
                        else -> "멈춰 있어 기록을 잠시 쉬고 있어요"
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
            TmtnOutlinedButton(
                text = "측정 끝내기",
                onClick = {
                    onStopSensorTracking()
                    scope.launch { state.quitChallenge() }
                },
                enabled = isPaused,
            )
            // ⚠️ 요구사항 5: 목표치에 도달했을 때만 활성화.
            TmtnPrimaryButton(
                text = "완료하기",
                onClick = {
                    onStopSensorTracking()
                    scope.launch { state.completeTimerChallenge() }
                },
                enabled = targetReached,
            )
        }
    }
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
        "SENSOR_FLOORS_CLIMBED" -> "오늘 ${floors}계단 올랐어요"
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
