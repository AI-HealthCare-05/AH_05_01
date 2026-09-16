package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.ExerciseMissionOption
import com.tmtn.app.ui.common.ModelMissionBadge
import com.tmtn.app.ui.common.hasRequiredSensor
import com.tmtn.app.ui.common.isModelRecognitionExecType
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import com.tmtn.app.ui.theme.tmtnClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ⚠️ 2026-09-12 추가 - 지현님 팀 점검 결과(01_기존미션_타이머형_점검결과.md) 반영:
 * "센서 미지원 기기의 대체 안내 필요". 걸음 수 기반 센서형(제자리걸음)이 필요로 하는
 * TYPE_STEP_COUNTER는 일부 저가 기기에 없을 수 있음 - 이 경우 완전히 막지 않고
 * "직접 확인" 방식으로 대체해서 진행할 수 있게 함.
 */
/** Figma B31 · 틈새 운동 목록.
 * ⚠️ 2026-09-11 신규 - "오늘의 카드"를 완료해야만 실제로 시작할 수 있음. card_completed가
 * false면 목록 대신 안내만 보여줌(카드 완료 화면 B17로 다시 유도). */
@Composable
fun ExerciseMissionListScreen(state: CardHomeState, scope: CoroutineScope, loadToday: suspend () -> Unit = { state.loadExerciseMissionsToday() }) {
    val colors = LocalTmtnColors.current
    val today = state.exerciseMissionsToday.value

    LaunchedEffect(Unit) { loadToday() }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "틈새 운동", onBack = { state.step.value = CardHomeStep.COMPLETED })
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("조금 더 움직여볼까요?", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })

            if (today == null) {
                Text("불러오는 중이에요...", style = TmtnType.body, color = colors.onSurfaceVariant)
                return@Column
            }
            if (!today.card_completed) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("오늘의 카드를 먼저 완료해 주세요", style = TmtnType.label, color = colors.onSurface)
                    Text("틈새 운동은 오늘의 카드를 마친 뒤에 할 수 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
                return@Column
            }

            Text(
                "추가 운동은 하루 2회까지 재료를 받을 수 있어요.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("오늘 받을 수 있는 재료 ${today.remaining}개", style = TmtnType.label, color = colors.onSurface)
                Text("운동 하나를 마치면 해당 재료 1개를 받아요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            today.options.forEach { option ->
                ExerciseMissionOptionCard(
                    option = option,
                    enabled = today.remaining > 0 && !option.already_completed_today,
                    onClick = { state.selectExerciseOption(option) },
                )
            }

            Text(
                "하루 한 장의 카드만 해도 충분해요.\n추가 운동을 건너뛰어도 연속 기록은 유지돼요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ExerciseMissionOptionCard(option: ExerciseMissionOption, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    val isModel = isModelRecognitionExecType(option.exec_type)
    Column(
        modifier = Modifier.fillMaxWidth()
            .testTag("extra-mission-${option.catalog_entry_id}")
            .tmtnClickable(enabled = enabled, onClick = onClick)
            .background(colors.background, RoundedCornerShape(16.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            option.title,
            style = TmtnType.missionName,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        )
        Text(
            if (option.already_completed_today) "오늘 이미 완료했어요" else "${option.target_value}${option.unit} · ${option.material_name} 1개",
            style = TmtnType.caption, color = colors.onSurfaceVariant,
        )
        Text(if (isModel) "움직임 인식" else "직접 확인", style = TmtnType.navigationLabel, color = colors.onSurfaceVariant)
    }
}

/** Figma B32(움직임 인식형)/B43(직접 확인형) · 선택한 운동 상세 및 시작. */
@Composable
fun ExerciseMissionDetailScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val option = state.selectedExerciseOption.value ?: return
    val today = state.exerciseMissionsToday.value
    val isModel = isModelRecognitionExecType(option.exec_type)
    val context = LocalContext.current
    val sensorAvailable = hasRequiredSensor(context, option.exec_type)

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "틈새 운동", onBack = { state.step.value = CardHomeStep.EXTRA_LIST })
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isModel) ModelMissionBadge()
            Text(option.title, style = TmtnType.sectionHeading, color = colors.onSurface)
            Text("오늘 남은 추가 보상 ${today?.remaining ?: 0} / ${today?.limit ?: 2}회", style = TmtnType.body, color = colors.onSurfaceVariant)

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${option.material_name} 1개", style = TmtnType.label, color = colors.onSurface)
                Text(
                    if (isModel && sensorAvailable) "목표를 채우면 댐에 재료가 더해져요." else "${option.target_value}${option.unit}를 마치고 직접 완료를 확인해요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
            // ⚠️ 2026-09-13 추가(팀 QA 지적) - 제자리걸음은 제자리 여부를 구분 못 함.
            if (option.exec_type == "SENSOR_STEPS_IN_PLACE" && sensorAvailable) {
                Text(
                    "정확한 측정을 위해 제자리에서 걸어 주세요. 이동하며 걸으면 걸음이 더 세어질 수 있어요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
            // ⚠️ 이 기기에 필요한 센서가 없는 경우 - 완전히 막지 않고 "직접 확인" 방식으로
            // 안내함(지현님 팀 점검 결과: "센서 미지원 기기의 대체 안내 필요").
            if (isModel && !sensorAvailable) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("이 기기에서는 자동으로 측정하기 어려워요", style = TmtnType.label, color = colors.error)
                    Text("운동을 마친 뒤 직접 완료를 눌러 확인해 주세요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }
            Text(option.guide_text, style = TmtnType.body, color = colors.onSurfaceVariant)

            TmtnPrimaryButton(
                text = if (isModel && sensorAvailable) "이 운동 시작하기" else "이 운동 실천하기",
                onClick = { scope.launch { state.startExerciseMission() } },
            )
        }
    }
}

/** Figma B34~B37(움직임 인식형 측정) / B44(직접 확인형). exec_type이 TIMER/CHECK면 서버
 * 검증을 manual_check=true로 건너뛰고(문서: "직접 완료를 확인해요"), 그 외 센서형은
 * 목표 달성 여부를 서버가 검증함(이번 버전은 로컬 실측 대신 "직접 확인" 버튼으로 완료
 * 요청만 보내고, 서버가 목표 미달성이면 409로 거절 - 실제 센서 연동은 후속 작업). */
@Composable
fun ExerciseMissionRunningScreen(
    state: CardHomeState,
    scope: CoroutineScope,
    onStartStepInPlace: () -> Unit = {},
    onStopStepInPlace: () -> Unit = {},
    onStartWalking: () -> Unit = {},
    onStopWalking: () -> Unit = {},
    onStartRunningDistance: () -> Unit = {},
    onStartRunningDuration: () -> Unit = {},
    onStopRunning: () -> Unit = {},
    onStartStairs: () -> Unit = {},
    onStopStairs: () -> Unit = {},
) {
    val colors = LocalTmtnColors.current
    val session = state.activeExerciseSession.value ?: return
    val execType = session.exec_type
    val context = LocalContext.current
    // ⚠️ 2026-09-12 추가 - 세션 시작은 서버에서 이미 됐지만(B32에서 이미 안내는 했음),
    // 화면 진입 시점에도 다시 확인 - 센서 없는 기기는 아예 실제 측정 모드로 안 들어가고
    // "직접 확인"으로 자동 전환됨.
    val sensorAvailable = hasRequiredSensor(context, execType)

    // ⚠️ 2026-09-11 반영: 실제 센서로 측정되는 5가지(제자리걸음/걷기시간/달리기시간/
    // 달리기거리/계단) - 각각 독립 액션 재사용. 그 외(CHECK/TIMER)는 여전히 "직접 확인"만.
    val isStepInPlace = execType == "SENSOR_STEPS_IN_PLACE" && sensorAvailable
    val isWalkingDuration = execType == "SENSOR_WALKING_DURATION" && sensorAvailable
    val isRunningDuration = execType == "SENSOR_RUNNING_DURATION" && sensorAvailable
    val isRunningDistance = execType == "SENSOR_RUNNING_DISTANCE"
    val isStairs = execType == "SENSOR_FLOORS_CLIMBED" && sensorAvailable
    val isRealSensor = isStepInPlace || isWalkingDuration || isRunningDuration || isRunningDistance || isStairs

    // 화면 진입 시 해당 센서만 시작, 벗어나면(완료·취소·뒤로가기 전부) 반드시 정지.
    androidx.compose.runtime.DisposableEffect(execType) {
        when {
            isStepInPlace -> onStartStepInPlace()
            isWalkingDuration -> onStartWalking()
            isRunningDuration -> onStartRunningDuration()
            isRunningDistance -> onStartRunningDistance()
            isStairs -> onStartStairs()
        }
        onDispose {
            when {
                isStepInPlace -> onStopStepInPlace()
                isWalkingDuration -> onStopWalking()
                isRunningDuration || isRunningDistance -> onStopRunning()
                isStairs -> onStopStairs()
            }
        }
    }

    val liveStepCount by com.tmtn.app.sensor.SensorDataHolder.stepInPlaceCount.collectAsState()
    val liveWalkingSeconds by com.tmtn.app.sensor.SensorDataHolder.walkingSeconds.collectAsState()
    val liveRunningSeconds by com.tmtn.app.sensor.SensorDataHolder.runningSeconds.collectAsState()
    val liveRunningDistanceM by com.tmtn.app.sensor.SensorDataHolder.runningDistanceM.collectAsState()
    val liveFloorsClimbed by com.tmtn.app.sensor.SensorDataHolder.floorsClimbed.collectAsState()

    // 카운트형(제자리걸음/거리/계단)만 완료 요청에 실측값을 실어 보냄 - 시간형(걷기·달리기 시간)은
    // 서버가 세션 시작 시각(started_at)부터 자체 계산하므로 클라이언트가 값을 안 보내도 됨
    // (조작 불가능한 서버 시각 기준이라 오히려 더 안전함).
    val currentCount = when {
        isStepInPlace -> liveStepCount
        isRunningDistance -> liveRunningDistanceM.toInt()
        isStairs -> liveFloorsClimbed
        else -> session.accumulated_count
    }
    val currentSeconds = when {
        isWalkingDuration -> liveWalkingSeconds
        isRunningDuration -> liveRunningSeconds
        else -> session.accumulated_duration_seconds
    }
    val targetReached = when {
        isStepInPlace || isRunningDistance || isStairs -> session.target_count != null && currentCount >= session.target_count
        isWalkingDuration || isRunningDuration -> session.target_duration_seconds != null && currentSeconds >= session.target_duration_seconds
        else -> true // CHECK/TIMER, 또는 센서 없어서 "직접 확인"으로 전환된 경우 - 항상 완료 가능
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "틈새 운동", onBack = { state.step.value = CardHomeStep.EXTRA_DETAIL })
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isModelRecognitionExecType(execType)) ModelMissionBadge()
            Text(session.title, style = TmtnType.sectionHeading, color = colors.onSurface)

            when {
                execType == "CHECK" ->
                    Text("움직이는 동안 켜 두세요. 다 마쳤으면 아래에서 확인해 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                !isRealSensor && execType != "TIMER" ->
                    // ⚠️ 원래는 센서형인데 이 기기에 필요한 센서가 없어서 "직접 확인"으로 전환됨.
                    Text("이 기기에서는 자동으로 세어지지 않아요. 운동을 마친 뒤 완료를 눌러 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                isStepInPlace || isRunningDistance || isStairs ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            session.target_count?.let { "$currentCount / $it" } ?: "$currentCount",
                            style = TmtnType.display, color = colors.onSurface,
                        )
                        Text(
                            if (targetReached) "목표를 채웠어요! 완료를 눌러 주세요."
                            else if (isStepInPlace) "제자리에서 걸으면 걸음이 세어져요."
                            else if (isStairs) "계단을 오르면 칸 수가 세어져요."
                            else "이동하는 동안만 거리가 늘어나요.",
                            style = TmtnType.caption, color = colors.onSurfaceVariant,
                        )
                    }
                isWalkingDuration || isRunningDuration ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            session.target_duration_seconds?.let { "${currentSeconds}초 / ${it}초" } ?: "${currentSeconds}초",
                            style = TmtnType.display, color = colors.onSurface,
                        )
                        Text(
                            if (targetReached) "목표를 채웠어요! 완료를 눌러 주세요." else "멈추면 자동으로 기다리고, 다시 움직이면 이어서 측정해요.",
                            style = TmtnType.caption, color = colors.onSurfaceVariant,
                        )
                    }
                else ->
                    Text(
                        session.target_count?.let { "${session.accumulated_count} / $it" }
                            ?: session.target_duration_seconds?.let { "${session.accumulated_duration_seconds}초 / ${it}초" }
                            ?: "",
                        style = TmtnType.display, color = colors.onSurface,
                    )
            }

            TmtnPrimaryButton(
                text = if (isRealSensor) "완료" else "완료 확인",
                enabled = !isRealSensor || targetReached,
                onClick = {
                    scope.launch {
                        // ⚠️ 실제 센서형은 manual_check=false로 서버가 검증하게 함(목표
                        // 미달성이면 409로 정직하게 거절됨). 카운트형만 실측값을 같이 보냄 -
                        // 시간형은 서버가 자체 계산하므로 안 보내도 됨.
                        when {
                            isStepInPlace || isRunningDistance || isStairs ->
                                state.completeExerciseMission(manualCheck = false, accumulatedCount = currentCount)
                            isWalkingDuration || isRunningDuration ->
                                state.completeExerciseMission(manualCheck = false)
                            else -> state.completeExerciseMission(manualCheck = true)
                        }
                    }
                },
            )
            TmtnTextButton(
                text = "그만두기",
                onClick = { scope.launch { state.cancelExerciseMission() } },
            )
        }
    }
}

/** Figma B38(1회)/B41(2회) · 틈새 운동 완료 보상. */
@Composable
fun ExerciseMissionRewardScreen(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    val result = state.exerciseRewardResult.value ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "틈새 운동 완료", onBack = { })
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("댐에 한 조각,\n더해졌어요.", style = TmtnType.headline, color = colors.onSurface)
            Text("${result.material_name} +1", style = TmtnType.display, color = colors.onSurface)

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("오늘의 추가 보상 ${result.reward_slot} / 2회", style = TmtnType.label, color = colors.onSurface)
                Text(
                    if (result.remaining > 0) "한 번 더 받을 수 있어요. 오늘은 여기까지여도 충분해요." else "오늘의 추가 재료를 모두 받았어요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
            Text(
                "오늘의 카드 실천은 그대로 남아요.\n틈새 운동은 별도 기록으로 더해져요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )

            TmtnPrimaryButton(
                text = "홈에서 확인하기",
                onClick = {
                    state.activeExerciseSession.value = null
                    state.exerciseRewardResult.value = null
                    state.step.value = CardHomeStep.HOME
                },
            )
            TmtnTextButton(text = "내 댐 보기", onClick = { state.activeExerciseSession.value = null; state.exerciseRewardResult.value = null; state.step.value = CardHomeStep.HOME })
        }
    }
}
