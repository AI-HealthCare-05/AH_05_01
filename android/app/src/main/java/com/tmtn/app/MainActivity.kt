package com.tmtn.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.SessionManager
import com.tmtn.app.network.TokenHolder
import com.tmtn.app.sensor.MissionSensorService
import com.tmtn.app.sensor.SensorDataHolder
import com.tmtn.app.ui.cardhome.CardHomeFlow
import com.tmtn.app.ui.common.SessionExpiredScreen
import com.tmtn.app.ui.dam.DamFlow
import com.tmtn.app.ui.nav.BottomNavBar
import com.tmtn.app.ui.nav.MainTab
import com.tmtn.app.ui.nav.PlaceholderTabScreen
import com.tmtn.app.ui.onboarding.OnboardingFlow
import com.tmtn.app.ui.profile.ProfileFlow
import com.tmtn.app.ui.profile.ProfileScreenKey
import com.tmtn.app.ui.record.RecordFlow
import com.tmtn.app.ui.reference.ReferenceFlow
import com.tmtn.app.ui.theme.AccessibilitySettingsHolder
import com.tmtn.app.ui.theme.TMTNv1Theme

/** 앱의 최상위 화면 흐름. 각 단계는 Navigation Compose 없이 상태값으로만 전환함. */
private enum class AppScreen { ONBOARDING, MAIN }

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            checkPermissionsAndStart()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⚠️ HANDOFF.md: "다크 모드 정의 없음, 항상 라이트" — 배경이 밝은 크림색이라
        // 상태바 아이콘(시계·배터리)도 어두운색으로 강제 지정 안 하면 흰 배경에 묻혀서 안 보임.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )

        checkPermissionsAndStart()

        // ⚠️ setContent보다 먼저 불러야 함 — 저장된 토큰이 있는지 이 시점에 확인해서
        // 초기 화면(screen)을 정할 때 바로 써야 하기 때문.
        TokenHolder.init(applicationContext)

        setContent {
            TMTNv1Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 저장된 토큰이 있으면(=예전에 로그인/온보딩 완료한 적 있으면) 온보딩을
                    // 건너뛰고 바로 메인으로 시작.
                    var screen by remember {
                        mutableStateOf(if (TokenHolder.accessToken != null) AppScreen.MAIN else AppScreen.ONBOARDING)
                    }
                    // ⚠️ 2026-09-04 QA(P0-6) 반영: 접근성 설정(글자 크기·고대비)이 서버엔 저장돼도
                    // 화면에 반영되는 코드가 없었음. 앱 시작 시 한 번 불러와서 AccessibilitySettingsHolder에
                    // 채워두면, TMTNv1Theme이 이걸 구독해서 전역에 반영함. 로그인 전(온보딩)이면
                    // 401이 나서 그냥 기본값(보통/고대비 없음)으로 남음 - 문제 없음.
                    LaunchedEffect(Unit) {
                        runCatching { ApiClient.profileApi.getAccessibility() }
                            .getOrNull()?.let { response ->
                                if (response.isSuccessful) {
                                    response.body()?.let {
                                        AccessibilitySettingsHolder.apply(
                                            it.large_controls, it.senior_mode, it.preferred_text_scale_hint,
                                        )
                                    }
                                }
                            }
                    }
                    var currentTab by remember { mutableStateOf(MainTab.HOME) }
                    // B06(카드 공개)·C그룹(챌린지 진행)처럼 하단 내비가 없어야 하는 몰입 단계인지
                    var isImmersive by remember { mutableStateOf(false) }
                    // H07(세션 만료)에서 "로그인하기" 눌러서 넘어온 경우 - 온보딩 처음(A01)이
                    // 아니라 A05(로그인)부터 시작해야 함.
                    var enterOnboardingAtLogin by remember { mutableStateOf(false) }
                    // ⚠️ 온보딩 막 끝내고 "오늘의 카드 보러 가기"를 누르면, 홈 화면 한 번 더
                    // 거치지 않고 카드 고르는 화면으로 바로 이어주기 위한 값. 한 번 쓰고 나면
                    // false로 되돌려서, 이후 홈 탭을 오갈 때는 원래대로 홈부터 보이게 함.
                    var justCompletedOnboarding by remember { mutableStateOf(false) }
                    // ⚠️ 참고 탭 "계산에 쓰인 값"에서 몸 정보/운동 정보 행을 눌렀을 때, 내 정보
                    // 탭으로 전환하면서 그 항목 편집 화면으로 바로 들어가게 하기 위한 값.
                    // 소비하고 나면 다시 HOME으로 되돌려서, 하단 탭에서 직접 "내 정보"를 눌렀을
                    // 때는 원래대로 홈부터 보이게 함.
                    var profileTargetScreen by remember { mutableStateOf(ProfileScreenKey.HOME) }

                    // H07: 어느 화면에서든 401(토큰 만료)이 감지되면 SessionManager가 신호를
                    // 켜고, 여기서 그걸 구독해서 세션만료 화면으로 강제 전환함.
                    val sessionExpired by SessionManager.sessionExpired.collectAsState()

                    if (sessionExpired) {
                        SessionExpiredScreen(
                            onLogin = {
                                SessionManager.clear()
                                enterOnboardingAtLogin = true
                                screen = AppScreen.ONBOARDING
                            },
                            // ⚠️ 다른 브랜치(ONBOARDING/MAIN)는 다 innerPadding을 적용하는데
                            // 이 화면만 빠져 있어서, 맨 아래 "다른 계정으로 로그인" 버튼이 폰
                            // 하단 내비게이션 바에 가려지던 버그. Scaffold의 innerPadding을 그대로 적용.
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else when (screen) {
                        AppScreen.ONBOARDING -> OnboardingFlow(
                            onOnboardingComplete = {
                                justCompletedOnboarding = true
                                screen = AppScreen.MAIN
                            },
                            hasSensorPermissions = { hasSensorPermissions() },
                            onRequestPermissions = { checkPermissionsAndStart() },
                            startAtLogin = enterOnboardingAtLogin,
                            modifier = Modifier.padding(innerPadding),
                        )
                        AppScreen.MAIN -> {
                            // 다른 탭에 있을 때 뒤로가기 누르면 홈 탭으로 먼저 오게.
                            // (홈 탭 안의 세부 화면 뒤로가기는 CardHomeFlow가 이미 처리하고,
                            // 거기서 더 뒤로 갈 데 없을 때만 이게 걸림 - 안쪽 BackHandler가 우선순위 높음)
                            BackHandler(enabled = currentTab != MainTab.HOME) {
                                currentTab = MainTab.HOME
                            }
                            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (currentTab) {
                                    MainTab.HOME -> {
                                        val deckPickOnEntry = remember(currentTab) { justCompletedOnboarding }
                                        CardHomeFlow(
                                            hasSensorPermissions = { hasSensorPermissions() },
                                            onStartSensorTracking = { challengeId, execType ->
                                                startTrackingChallenge(challengeId, execType)
                                            },
                                            onStopSensorTracking = { stopMissionService() },
                                            onOpenSettings = { openAppSettings() },
                                            onImmersiveChange = { isImmersive = it },
                                            startAtDeckPick = deckPickOnEntry,
                                        )
                                        LaunchedEffect(Unit) { justCompletedOnboarding = false }
                                    }
                                    MainTab.RECORD -> RecordFlow(
                                        onGoPickCard = { currentTab = MainTab.HOME },
                                    )
                                    MainTab.REFERENCE -> ReferenceFlow(
                                        onGoPickCard = { currentTab = MainTab.HOME },
                                        onOpenMyInfo = { currentTab = MainTab.MY },
                                        onOpenHealthInfo = {
                                            profileTargetScreen = ProfileScreenKey.HEALTH
                                            currentTab = MainTab.MY
                                        },
                                        onOpenExerciseInfo = {
                                            profileTargetScreen = ProfileScreenKey.EXERCISE
                                            currentTab = MainTab.MY
                                        },
                                        onImmersiveChange = { isImmersive = it },
                                    )
                                    MainTab.DAM -> DamFlow()
                                    MainTab.MY -> {
                                        // ⚠️ profileTargetScreen을 여기서 매번 그대로 읽으면, 아래
                                        // LaunchedEffect가 HOME으로 되돌리는 순간 재구성이 일어나서
                                        // initialScreen/onBackToOrigin이 전부 HOME 기준으로 다시
                                        // 계산돼버림(뒤로가기 대상이 사라짐). remember(currentTab)로
                                        // 이 탭에 들어온 시점의 값만 한 번 고정해서 씀.
                                        val targetScreen = remember(currentTab) { profileTargetScreen }
                                        ProfileFlow(
                                            initialScreen = targetScreen,
                                            onOpenDam = { currentTab = MainTab.DAM },
                                            onOpenSettings = { openAppSettings() },
                                            onLoggedOut = { screen = AppScreen.ONBOARDING },
                                            // TODO: A10(생활패턴 시간 선택)은 온보딩 흐름 안에서만 동작해서,
                                            // 내 정보 탭에서 재사용하려면 별도로 빼내는 작업이 필요함. 지금은 미연결.
                                            onEditWakeSleep = { },
                                            onSaveCsv = { fileName, content -> saveCsvToDownloads(fileName, content) },
                                            // ⚠️ 참고 탭에서 몸정보/운동정보로 딥링크해서 들어온 경우에만
                                            // 뒤로가기가 참고 탭으로 돌아가게 함(하단 탭에서 직접 들어왔으면 null).
                                            onBackToOrigin = if (targetScreen != ProfileScreenKey.HOME) {
                                                { currentTab = MainTab.REFERENCE }
                                            } else {
                                                null
                                            },
                                        )
                                        // ⚠️ ProfileFlow가 initialScreen을 remember{}로 한 번만 읽고 나면,
                                        // 이후엔 이 값을 다시 HOME으로 되돌려둬서 하단 탭에서 직접
                                        // "내 정보"를 다시 눌렀을 때 항상 홈부터 보이게 함.
                                        LaunchedEffect(Unit) { profileTargetScreen = ProfileScreenKey.HOME }
                                    }
                                    else -> PlaceholderTabScreen(currentTab)
                                }
                            }
                            // HANDOFF.md §1: B06 등 몰입 화면에서는 하단 내비 숨김
                            if (!isImmersive) {
                                BottomNavBar(
                                    currentTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }

    private fun requiredSensorPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    /** A16(권한 요청) 화면에서 "이미 권한이 있는지" 확인할 때 씀. */
    private fun hasSensorPermissions(): Boolean = requiredSensorPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissionsAndStart() {
        val permissions = requiredSensorPermissions()
        if (!hasSensorPermissions()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startTrackingChallenge(challengeId: String, execType: String) {
        val intent = Intent(this, MissionSensorService::class.java).apply {
            action = MissionSensorService.ACTION_START_TRACKING_CHALLENGE
            putExtra(MissionSensorService.EXTRA_CHALLENGE_ID, challengeId)
            putExtra(MissionSensorService.EXTRA_EXEC_TYPE, execType)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopMissionService() {
        val intent = Intent(this, MissionSensorService::class.java)
        stopService(intent)
        SensorDataHolder.resetAll()
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, MissionSensorService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(this, intent)
    }

    /** C16(권한 없음 화면)에서 "설정 열기" 눌렀을 때 - 이 앱의 시스템 설정 화면으로 이동. */
    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    /** F14: CSV를 시스템 다운로드 폴더에 저장(MediaStore, API 29+ 기준 - 별도 권한 불필요). */
    private fun saveCsvToDownloads(fileName: String, content: String) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { stream ->
                // CSV에 이미 UTF-8 BOM을 서버에서 붙여 보내주므로 그대로 씀(엑셀 한글 안 깨짐).
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
            android.widget.Toast.makeText(this, "다운로드 폴더에 저장했어요: $fileName", android.widget.Toast.LENGTH_LONG).show()
        } else {
            android.widget.Toast.makeText(this, "저장에 실패했어요.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

private val TEST_EXEC_TYPES = listOf(
    "SENSOR_STEPS",
    "SENSOR_FLOORS_CLIMBED",
    "SENSOR_STEPS_IN_PLACE",
    "SENSOR_RUNNING_DISTANCE",
    "SENSOR_RUNNING_DURATION",
    "SENSOR_WALKING_DURATION"
)

@Composable
fun MissionTestScreen(
    onStartTrackingChallenge: (challengeId: String, execType: String) -> Unit,
    onStop: () -> Unit,
    onStartStepInPlace: () -> Unit,
    onStopStepInPlace: () -> Unit,
    onStartStairInPlace: () -> Unit,
    onStopStairInPlace: () -> Unit,
    onStartRunningDistance: () -> Unit,
    onStartRunningDuration: () -> Unit,
    onStopRunning: () -> Unit,
    onStartWalking: () -> Unit,
    onStopWalking: () -> Unit,
    modifier: Modifier = Modifier,
    // ⚠️ B06(카드 공개)에서 "이 행동 시작하기" 눌러서 넘어온 경우에만 채워짐.
    // C그룹(진행 화면) 정식으로 생기면 이 다리 역할은 통째로 없어질 예정.
    initialChallengeId: String = "",
    initialExecType: String = "",
    onBackToHome: (() -> Unit)? = null,
) {
    val steps by SensorDataHolder.stepCount.collectAsState()
    val floors by SensorDataHolder.floorsClimbed.collectAsState()
    val isServiceRunning by SensorDataHolder.isServiceRunning.collectAsState()

    val stepInPlaceCount by SensorDataHolder.stepInPlaceCount.collectAsState()
    val isStepInPlaceActive by SensorDataHolder.isStepInPlaceActive.collectAsState()

    val stairInPlaceFloors by SensorDataHolder.stairInPlaceFloors.collectAsState()
    val isStairInPlaceActive by SensorDataHolder.isStairInPlaceActive.collectAsState()

    val distanceM by SensorDataHolder.runningDistanceM.collectAsState()
    val runningSeconds by SensorDataHolder.runningSeconds.collectAsState()
    val isRunningActive by SensorDataHolder.isRunningActive.collectAsState()

    val walkingSeconds by SensorDataHolder.walkingSeconds.collectAsState()
    val isWalkingActive by SensorDataHolder.isWalkingActive.collectAsState()

    var challengeIdInput by remember { mutableStateOf(initialChallengeId) }
    var execTypeExpanded by remember { mutableStateOf(false) }
    var selectedExecType by remember {
        mutableStateOf(initialExecType.ifBlank { TEST_EXEC_TYPES.first() })
    }

    Column(
        modifier = modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (onBackToHome != null) {
            Button(onClick = onBackToHome) { Text("← 오늘의 카드로 돌아가기") }
        }

        Text("⚠️ 테스트용 — 실제로는 카드 선택 화면에서 challenge_id를 받아와야 함")

        OutlinedTextField(
            value = challengeIdInput,
            onValueChange = { challengeIdInput = it },
            label = { Text("challenge_id (백엔드에서 카드 확정 후 받은 UUID 붙여넣기)") }
        )

        Button(onClick = { execTypeExpanded = true }) {
            Text("exec_type: $selectedExecType")
        }
        DropdownMenu(expanded = execTypeExpanded, onDismissRequest = { execTypeExpanded = false }) {
            TEST_EXEC_TYPES.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type) },
                    onClick = {
                        selectedExecType = type
                        execTypeExpanded = false
                    }
                )
            }
        }

        Button(
            onClick = { onStartTrackingChallenge(challengeIdInput, selectedExecType) }
        ) {
            Text("이 챌린지 추적 시작")
        }
        Button(onClick = onStop) { Text("측정 중지 및 초기화") }

        Text("측정 서비스 실행 중: $isServiceRunning")
        Text("걸음 수: $steps")
        Text("오른 층수(실제 계단): $floors")

        Text("제자리걸음: $stepInPlaceCount 걸음")
        if (!isStepInPlaceActive) {
            Button(onClick = onStartStepInPlace) { Text("제자리걸음 시작") }
        } else {
            Button(onClick = onStopStepInPlace) { Text("제자리걸음 완료") }
        }

        Text("제자리 계단운동: $stairInPlaceFloors 칸")
        if (!isStairInPlaceActive) {
            Button(onClick = onStartStairInPlace) { Text("제자리 계단운동 시작") }
        } else {
            Button(onClick = onStopStairInPlace) { Text("제자리 계단운동 완료") }
        }

        Text("달린 거리: ${distanceM.toInt()} m")
        Text("달린 시간: ${runningSeconds}초")
        if (!isRunningActive) {
            Button(onClick = onStartRunningDistance) { Text("달리기 시작 (거리 목표)") }
            Button(onClick = onStartRunningDuration) { Text("달리기 시작 (시간 목표)") }
        } else {
            Button(onClick = onStopRunning) { Text("달리기 완료") }
        }

        Text("걸은 시간: ${walkingSeconds}초")
        if (!isWalkingActive) {
            Button(onClick = onStartWalking) { Text("걷기 시작 (실외)") }
        } else {
            Button(onClick = onStopWalking) { Text("걷기 완료") }
        }
    }
}
