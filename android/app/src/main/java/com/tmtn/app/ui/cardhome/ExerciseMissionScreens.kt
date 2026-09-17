package com.tmtn.app.ui.cardhome

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.ExerciseMissionOption
import com.tmtn.app.ui.common.ModelMissionBadge
import com.tmtn.app.ui.common.hasLocationPermission
import com.tmtn.app.ui.common.hasRequiredSensor
import com.tmtn.app.ui.common.isGpsProviderEnabled
import com.tmtn.app.ui.common.hasPreciseLocationPermission
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
fun ExerciseMissionDetailScreen(
    state: CardHomeState,
    scope: CoroutineScope,
    // ⚠️ 2026-09-17 추가(QA Q03/Q04) - "오늘의 카드" SensorIntroScreen과 같은 시그니처.
    // 기본값을 둬서(항상 허용으로 간주) 이 화면을 쓰는 기존 프리뷰/테스트가 안 깨지게 함.
    hasSensorPermissions: (String) -> Boolean = { true },
    onRequestSensorPermissions: (String) -> Unit = {},
) {
    val colors = LocalTmtnColors.current
    val option = state.selectedExerciseOption.value ?: return
    val today = state.exerciseMissionsToday.value
    val isModel = isModelRecognitionExecType(option.exec_type)
    val context = LocalContext.current
    val sensorAvailable = hasRequiredSensor(context, option.exec_type)

    // ⚠️ 2026-09-17 추가(QA Q03/Q04) - 이 기기에 필요한 하드웨어 센서가 있어서 실제
    // 자동 측정을 시도할 케이스에서만 신체 활동 권한을 요구한다(센서가 아예 없어서
    // "직접 확인"으로 대체되는 기기엔 이 권한이 필요 없음 - 요구하면 오히려 불필요한
    // 권한 요청으로 혼란만 줌).
    val needsActivityPermission = isModel && sensorAvailable
    val sensorPermissionGranted = if (needsActivityPermission) hasSensorPermissions(option.exec_type) else true
    var permissionRequestedOnce by remember(option.exec_type) { mutableStateOf(false) }

    // ⚠️ 2026-09-17 추가(QA F07/F12, 홍주님 회신) - 계단은 기압 센서가 없으면 자동
    // 측정이 원천적으로 불가능해서, 다른 센서형(제자리걸음 등)처럼 "직접 확인"으로
    // 슬쩍 넘기지 않고 아예 시작을 막고 다른 운동을 고르도록 안내하기로 함(기존
    // "직접 확인" 대체는 그대로 유지 - 계단만 예외). 세션 자체를 안 만드니 오늘의
    // 틈새운동 선택 가능 횟수(remaining)도 소모되지 않는다.
    val isStairsUnsupported = option.exec_type == "SENSOR_FLOORS_CLIMBED" && !sensorAvailable

    // ⚠️ 2026-09-17 추가(QA F07/F12) - "다시 확인" 버튼을 누르면(설정 화면을 다녀온 뒤)
    // 위치 권한·위치 서비스 상태를 다시 읽도록 재계산 트리거만 증가시킨다.
    var locationRecheckTrigger by remember { mutableIntStateOf(0) }
    val isGpsMission = option.exec_type == "SENSOR_RUNNING_DISTANCE"
    val locationPermissionGranted = remember(locationRecheckTrigger) {
        if (isGpsMission) hasLocationPermission(context) else true
    }
    val gpsProviderEnabled = remember(locationRecheckTrigger) {
        if (isGpsMission) isGpsProviderEnabled(context) else true
    }
    val isGpsBlocked = isGpsMission && (!locationPermissionGranted || !gpsProviderEnabled)
    val canStart = !isStairsUnsupported && !isGpsBlocked && sensorPermissionGranted

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
                // ⚠️ 2026-09-16 추가(QA) - TYPE_STEP_COUNTER 센서는 팔 흔들림 패턴으로
                // 걸음을 인식해서, 손에 들고 있으면 잘 인식이 안 되고 주머니에 넣으면
                // 더 잘 인식되는 경우가 실기기에서 확인됨(기기/센서 알고리즘 특성이라
                // 앱 코드로 직접 고칠 수 없음) - 미리 안내해서 헷갈리지 않게 함.
                Text(
                    "폰을 손에 들고 있으면 걸음이 잘 안 세어질 수 있어요. 주머니에 넣거나 허리에 차면 더 정확해요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
            // ⚠️ 이 기기에 필요한 센서가 없는 경우(계단 제외) - 완전히 막지 않고 "직접 확인"
            // 방식으로 안내함(지현님 팀 점검 결과: "센서 미지원 기기의 대체 안내 필요").
            // 계단은 위 isStairsUnsupported 분기(아래)에서 별도로 막는다.
            if (isModel && !sensorAvailable && !isStairsUnsupported) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("이 기기에서는 자동으로 측정하기 어려워요", style = TmtnType.label, color = colors.error)
                    Text("운동을 마친 뒤 직접 완료를 눌러 확인해 주세요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }

            // ⚠️ 2026-09-17 추가(QA F07/F12) - 계단: 기압 센서가 없으면 시작 자체를 막고
            // 다른 운동을 고르도록 안내(홍주님 지정 문구).
            if (isStairsUnsupported) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "이 휴대폰에서는 계단 운동을 자동 측정할 수 없어요. 다른 틈새운동을 골라볼까요?",
                        style = TmtnType.label, color = colors.error,
                    )
                }
            }

            // ⚠️ 2026-09-17 추가(QA Q03/Q04) - 신체 활동 권한이 없으면(허용 안 했거나
            // 설정에서 껐음) 시작 자체를 막는다 - 이 배너 없이 그냥 넘어가면
            // ExerciseMissionRunningScreen에서 "측정 중"처럼 보이는 화면으로 들어가놓고
            // 실제로는 센서가 값을 전혀 못 받는 상태가 됨(Q03 지적 그대로).
            if (needsActivityPermission && !sensorPermissionGranted) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("신체 활동 권한이 필요해요", style = TmtnType.label, color = colors.error)
                    Text(
                        "움직임을 자동으로 세려면 신체 활동 권한을 허용해 주세요.",
                        style = TmtnType.caption, color = colors.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TmtnTextButton(
                            // ⚠️ QA Q04 - 처음 거부됐을 땐 OS 권한 다이얼로그를 다시 띄우고
                            // (한 번 더 요청하면 시스템이 "다시 묻지 않음" 체크 여부에 따라
                            // 다이얼로그를 보여주거나 곧바로 거부 콜백만 줌), 이미 한 번
                            // 더 요청했는데도 안 됐으면(영구 거부로 추정) 앱 설정으로 보낸다.
                            text = if (!permissionRequestedOnce) "권한 허용" else "설정에서 권한 허용",
                            onClick = {
                                if (!permissionRequestedOnce) {
                                    permissionRequestedOnce = true
                                    onRequestSensorPermissions(option.exec_type)
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // ⚠️ 2026-09-17 추가(QA F07/F12) - 달리기(거리): 위치 권한 거부/위치 서비스
            // 꺼짐을 원인별로 구분해 안내하고, 설정 화면으로 보내 고친 뒤 "다시 확인"으로
            // 재시도할 수 있게 함.
            if (isGpsBlocked) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "이동 거리를 기록하려면 위치 기능을 켜주세요.",
                        style = TmtnType.label, color = colors.error,
                    )
                    Text(
                        if (!locationPermissionGranted) "위치 권한이 꺼져 있어요. 권한 설정에서 허용해 주세요."
                        else "휴대폰의 위치(GPS) 기능이 꺼져 있어요. 설정에서 켜주세요.",
                        style = TmtnType.caption, color = colors.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TmtnTextButton(
                            text = if (!locationPermissionGranted) "권한 설정으로 이동" else "위치 설정으로 이동",
                            onClick = {
                                val intent = if (!locationPermissionGranted) {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                } else {
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                }
                                context.startActivity(intent)
                            },
                        )
                        TmtnTextButton(text = "다시 확인", onClick = { locationRecheckTrigger++ })
                    }
                }
            }

            // ⚠️ 2026-09-17 추가(QA 리뷰 #5) - 위치 권한은 있지만(coarse) 정확한 위치는
            // 아닌 경우: 시작은 막지 않되, 거리가 잘 안 늘어날 수 있다는 걸 미리
            // 안내하고 다른 운동으로 바꿀 수 있게 한다.
            val hasPreciseLocation = remember(locationRecheckTrigger) {
                if (isGpsMission) hasPreciseLocationPermission(context) else true
            }
            if (isGpsMission && !isGpsBlocked && !hasPreciseLocation) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "정확한 위치 권한이 아니어서 거리가 잘 안 늘어날 수 있어요.",
                        style = TmtnType.caption, color = colors.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TmtnTextButton(
                            text = "정확한 위치로 변경",
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            },
                        )
                        TmtnTextButton(
                            text = "다른 운동 고르기",
                            onClick = {
                                state.errorMessage.value = null
                                state.selectedExerciseOption.value = null
                                state.step.value = CardHomeStep.EXTRA_LIST
                            },
                        )
                    }
                }
            }

            if (!isStairsUnsupported && !isGpsBlocked) {
                Text(option.guide_text, style = TmtnType.body, color = colors.onSurfaceVariant)
            }

            // ⚠️ 2026-09-16 버그 수정(QA 영상) - startExerciseMission() 실패(서버 409 등)를
            // 그냥 무시하고 있어서, 사용자 눈엔 버튼을 눌러도 "다음 화면으로 안 넘어가는"
            // 것처럼 보였음(에러 메시지가 전혀 없었음). 이제 실패하면 이유를 보여줌.
            state.errorMessage.value?.let { message ->
                Text(message, style = TmtnType.caption, color = colors.error)
            }

            if (isStairsUnsupported) {
                // ⚠️ 세션을 만들지 않고 목록으로 돌려보낸다 - 오늘 남은 추가 보상
                // 횟수(remaining)가 소모되지 않는다.
                TmtnPrimaryButton(
                    text = "다른 운동 고르기",
                    onClick = {
                        state.errorMessage.value = null
                        state.selectedExerciseOption.value = null
                        state.step.value = CardHomeStep.EXTRA_LIST
                    },
                )
            } else {
                TmtnPrimaryButton(
                    text = if (isModel && sensorAvailable) "이 운동 시작하기" else "이 운동 실천하기",
                    enabled = canStart,
                    onClick = {
                        scope.launch {
                            state.errorMessage.value = null
                            val started = state.startExerciseMission()
                            if (!started) {
                                state.errorMessage.value =
                                    "지금은 시작할 수 없어요. 이미 진행 중인 틈새 운동이 있거나, 오늘 보상을 다 받았을 수 있어요."
                            }
                        }
                    },
                )
            }
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
    // ⚠️ 2026-09-17 추가(QA Q03/Q07) - ExerciseMissionDetailScreen과 같은 이유. 여기서도
    // 다시 확인하는 이유: 시작 화면을 통과한 뒤에도(설정 화면에서 권한을 끄고 돌아오는
    // 등) 권한이 바뀔 수 있고, "이어하기"로 곧바로 이 화면에 진입하는 경로도 있어서
    // Detail 화면을 거치지 않을 수 있음.
    hasSensorPermissions: (String) -> Boolean = { true },
    onStartStepInPlace: () -> Unit = {},
    onStopStepInPlace: () -> Unit = {},
    onStartWalking: () -> Unit = {},
    onStopWalking: () -> Unit = {},
    onStartRunningDistance: () -> Unit = {},
    onStartRunningDuration: () -> Unit = {},
    onStopRunning: () -> Unit = {},
    onStartStairs: () -> Unit = {},
    onStopStairs: () -> Unit = {},
    // ⚠️ 2026-09-16 추가(QA) - "이어하기" 진입 시(화면을 나갔다 돌아온 경우) 서버가
    // 갖고 있던 마지막 걸음수부터 이어서 셈. 기본 onStartStepInPlace()(항상 0부터)와
    // 시그니처가 달라서 별도 파라미터로 추가 - 기존 콜백(오늘의 카드, 테스트 화면)은
    // 안 건드림.
    onStartStepInPlaceResume: (Int) -> Unit = {},
    // ⚠️ 2026-09-16 추가(QA) - "천천히 걷기" 이어하기용, 위와 같은 이유.
    onStartWalkingResume: (Int) -> Unit = {},
    // ⚠️ 2026-09-16 추가(QA F01) - 계단·달리기 이어하기용, 위와 같은 이유.
    onStartRunningDistanceResume: (Int) -> Unit = {},
    onStartRunningDurationResume: (Int) -> Unit = {},
    onStartStairsResume: (Int) -> Unit = {},
) {
    val colors = LocalTmtnColors.current
    val session = state.activeExerciseSession.value ?: return
    val execType = session.exec_type
    val context = LocalContext.current
    // ⚠️ 2026-09-12 추가 - 세션 시작은 서버에서 이미 됐지만(B32에서 이미 안내는 했음),
    // 화면 진입 시점에도 다시 확인 - 센서 없는 기기는 아예 실제 측정 모드로 안 들어가고
    // "직접 확인"으로 자동 전환됨.
    val sensorAvailable = hasRequiredSensor(context, execType)
    // ⚠️ 2026-09-17 추가(QA Q03) - 하드웨어는 있어도 신체 활동 권한이 없으면
    // TYPE_STEP_COUNTER/TYPE_STEP_DETECTOR 리스너 등록이 SecurityException으로
    // 실패한다(MissionSensorService가 잡아서 SensorDataHolder.serviceError로 알림 -
    // 아래 serviceError 배너 참고). 그 실패를 기다리지 않고 애초에 "측정 중"처럼
    // 보이는 실시간 카운터 화면으로 들어가지 않도록 여기서 미리 걸러낸다.
    val sensorPermissionGranted = hasSensorPermissions(execType)

    // ⚠️ 2026-09-11 반영: 실제 센서로 측정되는 5가지(제자리걸음/걷기시간/달리기시간/
    // 달리기거리/계단) - 각각 독립 액션 재사용. 그 외(CHECK/TIMER)는 여전히 "직접 확인"만.
    val isStepInPlace = execType == "SENSOR_STEPS_IN_PLACE" && sensorAvailable && sensorPermissionGranted
    val isWalkingDuration = execType == "SENSOR_WALKING_DURATION" && sensorAvailable && sensorPermissionGranted
    val isRunningDuration = execType == "SENSOR_RUNNING_DURATION" && sensorAvailable && sensorPermissionGranted
    val isRunningDistance = execType == "SENSOR_RUNNING_DISTANCE" && sensorPermissionGranted
    val isStairs = execType == "SENSOR_FLOORS_CLIMBED" && sensorAvailable && sensorPermissionGranted
    val isRealSensor = isStepInPlace || isWalkingDuration || isRunningDuration || isRunningDistance || isStairs
    // ⚠️ QA Q03 - "센서가 없는 기기"(직접 확인으로 안내)와 "권한이 없는 기기"(권한부터
    // 요청해야 함)를 같은 문구로 뭉개지 않기 위한 구분 플래그. 하드웨어는 있는데
    // 권한만 없는 센서형 운동일 때만 true.
    val isSensorExecType = execType == "SENSOR_STEPS_IN_PLACE" || execType == "SENSOR_WALKING_DURATION" ||
        execType == "SENSOR_RUNNING_DURATION" || execType == "SENSOR_RUNNING_DISTANCE" ||
        execType == "SENSOR_FLOORS_CLIMBED"
    val blockedByMissingPermission = isSensorExecType && sensorAvailable && !sensorPermissionGranted

    // ⚠️ 2026-09-17 수정(QA 리뷰 #3) - 예전엔 DisposableEffect라서 화면이 사라지기만
    // 하면(다른 탭으로 이동 포함) onDispose가 무조건 정지를 보냈음. 근데 제품 의도는
    // "다른 탭 갔다 와도 측정은 계속"(F08과 같은 방향)이라, 탭 이동일 땐 멈추면 안 됨.
    // 실제 정지는 완료 확정·그만두기 확정처럼 사용자가 명시적으로 끝낼 때만 일어나야
    // 해서, 그 지점(아래 submitCompletion/그만두기 버튼)에서 직접 정지를 호출하고 여긴
    // "시작만" 책임진다. 또한:
    //  - 이미 이 세션을 추적 중이면(CurrentExerciseSessionHolder) 다시 시작하지 않는다 -
    //    안 그러면 화면 재진입마다 서버의 오래된 누적값으로 리셋되어 그 사이 백그라운드
    //    측정분이 날아간다(리뷰 #3의 핵심 증상).
    //  - 세션이 PAUSED면 자동으로 다시 시작하지 않는다 - 사용자가 직접 "이어하기"를
    //    눌러야 한다(아래 일시정지 버튼과 짝).
    val exerciseSessionKey = session.id.toString()
    LaunchedEffect(exerciseSessionKey, session.state) {
        val alreadyTracking = com.tmtn.app.sensor.CurrentExerciseSessionHolder.sessionId == exerciseSessionKey
        if (session.state == "PAUSED" || alreadyTracking) return@LaunchedEffect
        com.tmtn.app.sensor.CurrentExerciseSessionHolder.sessionId = exerciseSessionKey
        com.tmtn.app.sensor.CurrentExerciseSessionHolder.execType = execType
        when {
            isStepInPlace -> {
                if (session.accumulated_count > 0) onStartStepInPlaceResume(session.accumulated_count)
                else onStartStepInPlace()
            }
            isWalkingDuration -> {
                if (session.accumulated_duration_seconds > 0) onStartWalkingResume(session.accumulated_duration_seconds)
                else onStartWalking()
            }
            isRunningDuration -> {
                if (session.accumulated_duration_seconds > 0) onStartRunningDurationResume(session.accumulated_duration_seconds)
                else onStartRunningDuration()
            }
            isRunningDistance -> {
                if (session.accumulated_count > 0) onStartRunningDistanceResume(session.accumulated_count)
                else onStartRunningDistance()
            }
            isStairs -> {
                if (session.accumulated_count > 0) onStartStairsResume(session.accumulated_count)
                else onStartStairs()
            }
        }
    }

    // ⚠️ 2026-09-17 추가(QA 리뷰 #3/#4) - 완료·그만두기가 실제로 로컬 측정을 멈출 때
    // 쓰는 공통 함수(위 LaunchedEffect의 "시작"과 짝). CurrentExerciseSessionHolder도
    // 같이 비워서 서비스의 주기 동기화 대상에서 뺀다.
    val stopCurrentMeasurement: () -> Unit = {
        when {
            isStepInPlace -> onStopStepInPlace()
            isWalkingDuration -> onStopWalking()
            isRunningDuration || isRunningDistance -> onStopRunning()
            isStairs -> onStopStairs()
        }
    }

    val liveStepCount by com.tmtn.app.sensor.SensorDataHolder.stepInPlaceCount.collectAsState()
    val liveWalkingSeconds by com.tmtn.app.sensor.SensorDataHolder.walkingSeconds.collectAsState()
    val liveRunningSeconds by com.tmtn.app.sensor.SensorDataHolder.runningSeconds.collectAsState()
    val liveRunningDistanceM by com.tmtn.app.sensor.SensorDataHolder.runningDistanceM.collectAsState()
    val liveFloorsClimbed by com.tmtn.app.sensor.SensorDataHolder.floorsClimbed.collectAsState()
    // ⚠️ 2026-09-17 추가(QA Q06) - 권한·GPS는 켜져 있지만 아직 첫 위치 신호을 못 받은 구간을 구분해서 안내.
    val runningSignalAcquired by com.tmtn.app.sensor.SensorDataHolder.runningSignalAcquired.collectAsState()

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
        TmtnTopBar(title = "틈새 운동", onBack = { state.errorMessage.value = null; state.step.value = CardHomeStep.EXTRA_DETAIL })
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isModelRecognitionExecType(execType)) ModelMissionBadge()
            Text(session.title, style = TmtnType.sectionHeading, color = colors.onSurface)

            when {
                execType == "CHECK" ->
                    Text("움직이는 동안 켜 두세요. 다 마쳤으면 아래에서 확인해 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                // ⚠️ 2026-09-17 추가(QA Q03) - 하드웨어 센서는 있지만 권한이 없는 경우를
                // "이 기기는 센서가 없다"는 문구로 뭉개지 않는다 - 사용자가 설정에서
                // 권한만 켜면 바로 측정되는 상황인데 "안 되는 기기"로 오해하게 두면 안 됨.
                blockedByMissingPermission ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("신체 활동 권한이 꺼져 있어요", style = TmtnType.body, color = colors.error)
                        Text("설정에서 권한을 허용하면 자동으로 세어져요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                        TmtnTextButton(
                            text = "설정에서 권한 허용",
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            },
                        )
                    }
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
                            // QA Q06 - 권한·GPS는 켜져 있는데 아직 첫 신호를 못 받은 구간은
                            // "이동해도 안 늘어나는 버그"처럼 보이던 문제를 구분해서 안내.
                            else if (isRunningDistance && !runningSignalAcquired) "GPS 신호를 찾는 중이에요. 하늘이 트인 곳으로 이동해 보세요."
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

            // ⚠️ 2026-09-17 추가(QA 리뷰 #5) - 서비스가 권한 거부 등으로 SensorDataHolder에
            // 남긴 오류(MissionSensorService의 SecurityException 처리)가 지금까지 진행
            // 화면 어디에서도 구독되지 않고 있었음 - 측정이 조용히 실패해도 사용자는
            // 이유를 알 수 없었음.
            val serviceError by com.tmtn.app.sensor.SensorDataHolder.serviceError.collectAsState()
            serviceError?.let { message ->
                Text(message, style = TmtnType.caption, color = colors.error)
            }

            // ⚠️ 2026-09-17 추가(QA 리뷰 #3) - PAUSED 상태에선 완료할 수 없고, 명시적으로
            // "이어하기"를 눌러야 다시 측정이 시작된다(자동 재개 금지 - 리뷰 요구사항).
            val isPaused = session.state == "PAUSED"

            // ⚠️ 2026-09-17 추가(QA 리뷰 #3) - 세션 완료가 확정된 순간(보상 결과가
            // 채워짐) 이 화면이 아직 떠 있다면 로컬 측정도 바로 멈춘다. 화면이 이미
            // 다른 탭으로 가 있었다면(백그라운드에서 완료된 경우) 이 효과 자체가 안
            // 돌지만, CardHomeState.completeExerciseMission()의 성공 처리에서 이미
            // CurrentExerciseSessionHolder는 비워둬서 불필요한 주기 동기화는 멈춘다.
            LaunchedEffect(state.exerciseRewardResult.value) {
                if (state.exerciseRewardResult.value != null) stopCurrentMeasurement()
            }

            // ⚠️ 2026-09-17 추가(QA F13) - "완료" 버튼과 "다시 저장하기"(실패 후 재시도)가
            // 같은 완료 요청을 보내야 해서 하나의 람다로 공유한다.
            val saveState = state.exerciseSaveState.value
            val submitCompletion: () -> Unit = {
                // ⚠️ 실제 센서형은 manual_check=false로 서버가 검증하게 함(목표
                // 미달성이면 409로 정직하게 거절됨).
                // ⚠️ 2026-09-16 버그 수정(QA F05) - "시간형은 서버가 자체 계산하므로
                // 안 보내도 됨"이라는 예전 전제가 틀렸음. 서버는 "시작~완료 경과
                // 시각"으로 대신 계산하고 있었고, 이러면 센서가 전혀 못 재도(0초여도)
                // 시간만 지나면 목표 달성으로 잘못 판정됨(원인분석 문서 재현 사례).
                // 이제 서버가 시간형은 클라이언트 확정값만 신뢰하도록 고쳤으니, 화면이
                // 실제로 보여주고 있던 currentSeconds를 반드시 실어 보내야 함.
                // ⚠️ 2026-09-17 수정(QA 리뷰 #2) - completeExerciseMission()이 이제
                // suspend가 아니라(화면 생명주기와 분리된 exerciseScope에서 직접
                // launch) scope.launch로 감쌀 필요가 없다.
                when {
                    isStepInPlace || isRunningDistance || isStairs ->
                        state.completeExerciseMission(manualCheck = false, accumulatedCount = currentCount)
                    isWalkingDuration || isRunningDuration ->
                        state.completeExerciseMission(manualCheck = false, accumulatedDurationSeconds = currentSeconds)
                    else -> state.completeExerciseMission(manualCheck = true)
                }
            }

            // ⚠️ 2026-09-17 수정(디자인 통합 요청) - "완료"와 "다시 저장하기"를 동시에
            // 주 버튼 두 개로 보여주지 않는다 - 상태에 맞는 주 버튼 하나만 표시.
            when {
                isPaused ->
                    TmtnPrimaryButton(
                        text = "이어하기",
                        onClick = { state.resumeExerciseMission() },
                    )
                saveState == ExerciseSaveState.FAILED ->
                    TmtnPrimaryButton(text = "다시 저장하기", onClick = submitCompletion)
                else ->
                    TmtnPrimaryButton(
                        text = if (isRealSensor) "완료" else "완료 확인",
                        enabled = (!isRealSensor || targetReached) && saveState != ExerciseSaveState.SAVING,
                        loading = saveState == ExerciseSaveState.SAVING,
                        onClick = submitCompletion,
                    )
            }
            // ⚠️ 2026-09-16 추가(QA F09) - 완료/취소 실패 메시지를 진행 화면에서도
            // 보여줌(예전엔 상세 화면에만 있었음) - cancelExerciseMission()이 실패
            // 시 세션을 그대로 유지하도록 고쳤으니, 그 실패 사유도 사용자에게 보여야 함.
            state.errorMessage.value?.let { message ->
                Text(message, style = TmtnType.caption, color = colors.error)
            }
            // ⚠️ 2026-09-17 추가(QA 리뷰 #3) - 실측 센서형이고 아직 측정 중(ACTIVE)일
            // 때만 일시정지 가능. pauseExerciseMission()에 지금까지의 확정값을 실어
            // 보내야 "이어하기"가 정확한 지점부터 시작된다.
            if (isRealSensor && !isPaused && saveState != ExerciseSaveState.SAVING) {
                TmtnTextButton(
                    text = "일시정지",
                    onClick = {
                        stopCurrentMeasurement()
                        // ⚠️ 2026-09-17 - 홀더를 비워야 "이어하기" 때 alreadyTracking이
                        // false가 되어 LaunchedEffect가 실제로 onStart*Resume을 다시
                        // 호출한다(안 비우면 "이미 추적 중"으로 오인해 정지된 센서를
                        // 영영 다시 시작 안 함).
                        com.tmtn.app.sensor.CurrentExerciseSessionHolder.clear()
                        when {
                            isStepInPlace || isRunningDistance || isStairs ->
                                state.pauseExerciseMission(accumulatedCount = currentCount)
                            isWalkingDuration || isRunningDuration ->
                                state.pauseExerciseMission(accumulatedDurationSeconds = currentSeconds)
                            else -> state.pauseExerciseMission()
                        }
                    },
                )
            }
            // ⚠️ 2026-09-17 수정(QA 리뷰 #4) - "사용자가 종료를 확정하면 로컬 측정은
            // 즉시 중지하고, 서버 취소 실패만 취소 대기로 보존"해야 한다는 지적 대응.
            // 예전엔 로컬 정지가 화면 onDispose(=컴포저블이 사라질 때)에만 걸려 있어서,
            // 그만두기가 서버 응답을 기다리는 동안에도(그리고 실패해서 화면이 그대로
            // 남아 있어도) 로컬 측정은 계속 돌고 있었음. 이제 버튼을 누른 즉시(서버
            // 응답과 무관하게) 로컬 측정부터 멈춘다.
            TmtnTextButton(
                text = "그만두기",
                enabled = saveState != ExerciseSaveState.SAVING,
                onClick = {
                    stopCurrentMeasurement()
                    state.errorMessage.value = null
                    state.cancelExerciseMission()
                },
            )
        }
    }
}

/** Figma B38(1회)/B41(2회) · 틈새 운동 완료 보상. */
@Composable
fun ExerciseMissionRewardScreen(state: CardHomeState, onOpenDam: () -> Unit = {}) {
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
            // ⚠️ 2026-09-17 수정(디자인 통합 요청) - "내 댐 보기"가 실제로는 홈으로만
            // 이동하고 있었음(review 커밋 기준 잔여 문제) - 댐 탭으로 보낸다.
            TmtnTextButton(
                text = "내 댐 보기",
                onClick = {
                    state.activeExerciseSession.value = null
                    state.exerciseRewardResult.value = null
                    state.step.value = CardHomeStep.HOME
                    onOpenDam()
                },
            )
        }
    }
}
