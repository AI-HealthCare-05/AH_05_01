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
                text = "측정 시작",
                onClick = {
                    if (hasSensorPermissions()) {
                        // 서버에 "시작" 알린 뒤에만 실제 센서 서비스를 켬 - 순서 중요
                        // (서버 READY -> ACTIVE 전환 없이 GPS/센서부터 켜면 완료 시 목표 판정이 꼬임).
                        scope.launch {
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

/** Figma C10/C12/C14 통합 · 자동 측정 · 측정 중 (exec_type별로 주요 수치만 다르게 표시) */
@Composable
fun SensorMeasuringScreen(
    state: CardHomeState,
    onStopSensorTracking: () -> Unit,
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
    // 위 구독들이 recomposition을 트리거하도록 값만 참조해 둠(walkingSecondsState 등 직접 안 써도 무방)
    val display = computeSensorDisplay(card.exec_type, card.target_value)

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
                    if (display.isActive) "움직임을 확인했어요" else "멈춰 있어 기록을 잠시 쉬고 있어요",
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
                    if (display.progress >= 1f) "목표를 채웠어요. 끝내면 결과를 저장합니다." else "${(display.progress * 100).toInt()}%",
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

            TmtnTextButton(
                text = "측정 끝내기",
                onClick = {
                    onStopSensorTracking()
                    state.step.value = CardHomeStep.SENSOR_RESULT
                },
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
                        state.step.value = CardHomeStep.COMPLETED
                    }
                },
            )
        }
    }
}
