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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
import com.tmtn.app.network.PersistentCookieJar
import com.tmtn.app.network.SessionManager
import com.tmtn.app.network.TokenHolder
import com.tmtn.app.sensor.MissionSensorService
import com.tmtn.app.sensor.SensorDataHolder
import com.tmtn.app.ui.cardhome.CardHomeFlow
import com.tmtn.app.ui.cardhome.CardHomeState
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

        // ⚠️ 2026-09-08 QA(N6) 반영: 여기서(앱을 처음 열자마자, 가입도 온보딩도 시작하기
        // 전) 위치·신체활동·알림 3개를 설명 없이 연속으로 물어봤음(리포트: "시니어 사용자는
        // 여기서 전부 거부하기 쉽다"). 권한은 그 기능을 실제로 처음 쓰는 시점(센서 미션
        // "시작하기"를 눌렀을 때 - SensorIntroScreen)으로 미룸. 여기서의 호출 자체를 제거.

        // ⚠️ setContent보다 먼저 불러야 함 — 저장된 토큰이 있는지 이 시점에 확인해서
        // 초기 화면(screen)을 정할 때 바로 써야 하기 때문.
        TokenHolder.init(applicationContext)
        // ⚠️ 2026-09-08 추가: refresh_token 쿠키도 암호화 저장소에서 복원함. 이걸 안 부르면
        // 쿠키가 메모리 전용으로만 동작해서, 앱을 껐다 켜면 액세스 토큰 만료(1시간) 뒤에
        // 재로그인해야 함(PersistentCookieJar 주석 참고).
        PersistentCookieJar.init(applicationContext)

        setContent {
            TMTNv1Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 저장된 토큰이 있으면(=예전에 로그인/온보딩 완료한 적 있으면) 온보딩을
                    // 건너뛰고 바로 메인으로 시작.
                    var screen by remember {
                        mutableStateOf(if (TokenHolder.accessToken != null) AppScreen.MAIN else AppScreen.ONBOARDING)
                    }
                    // ⚠️ 2026-09-04 QA(P0-6) 반영: 접근성 설정(글자 크기·고대비)이 서버엔 저장돼도
                    // 화면에 반영되는 코드가 없었음. 불러와서 AccessibilitySettingsHolder에 채워두면
                    // TMTNv1Theme이 이걸 구독해서 전역에 반영함.
                    //
                    // ⚠️ 2026-09-08 QA 반영(회원가입 중 "다시 로그인해주세요"로 튕기던 버그):
                    // 예전엔 LaunchedEffect(Unit)으로 앱 시작 시 무조건 호출하면서 "로그인 전이면
                    // 401 나고 기본값으로 남으니 문제 없다"고 적어뒀는데, 그 401을 SessionInterceptor가
                    // 세션 만료로 처리해서 온보딩 화면 위에 세션 만료 화면이 덮여버렸음. 계정 삭제 후
                    // 재가입할 때(로그인 안 된 상태로 앱을 켤 때) 정확히 이 경로를 탐.
                    // 이제 로그인된 뒤에만 호출함. 키를 screen으로 둬서 로그인을 마치고 MAIN으로
                    // 들어오는 순간에도 불려짐 - 예전에는 앱 시작 때 딱 한 번이라, 그 세션에서
                    // 로그인한 사용자에게는 접근성 설정이 아예 반영되지 않는 문제도 같이 있었음.
                    LaunchedEffect(screen) {
                        if (screen != AppScreen.MAIN || TokenHolder.accessToken == null) return@LaunchedEffect
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
                        // ⚠️ 2026-09-08 요구사항(8·10·11번) 반영: 로그인 상태로 앱을 열 때마다
                        // 서버에 저장된 알림 슬롯을 불러와서 기기의 로컬 알림 예약도 그 값과
                        // 맞춰둠 - 다른 기기에서 설정을 바꿨거나, 이 기기가 한동안 안 켜져서
                        // 예약이 밀렸던 경우에도 앱을 열면 항상 최신 상태로 복구됨.
                        // NOTIFICATION 동의가 없으면(consent 확인은 Worker 실행 시점에도
                        // 한 번 더 함) 여기서 아예 예약 자체를 안 함.
                        runCatching { ApiClient.profileApi.listConsents() }.getOrNull()?.body()
                            ?.any { it.purpose == "NOTIFICATION" && it.status == "AGREED" }
                            ?.let { notificationConsented ->
                                if (!notificationConsented) {
                                    com.tmtn.app.notification.NotificationScheduler.cancelAll(applicationContext)
                                    return@let
                                }
                                runCatching { ApiClient.profileApi.getNotificationSettings() }
                                    .getOrNull()?.body()?.let { setting ->
                                        com.tmtn.app.notification.NotificationScheduler
                                            .scheduleAllExact(applicationContext, setting.slots)
                                    }
                            }
                    }
                    var currentTab by remember { mutableStateOf(MainTab.HOME) }
                    // ⚠️ 2026-09-07 반영(타이머 "1초 리셋" 버그 수정): 예전엔 CardHomeFlow
                    // 내부에서 remember { CardHomeState() }로 만들었음. 바로 아래 when(currentTab)이
                    // 탭마다 다른 컴포지션 분기라서, "홈" 탭에서 다른 탭으로 갔다가 돌아오면
                    // CardHomeFlow가 통째로 dispose됐다 다시 만들어지면서 그 CardHomeState(진행
                    // 중이던 타이머 값 포함)가 매번 새로 생겨 사라졌음 - 여기(탭 전환과 무관하게
                    // 계속 살아있는 자리)로 끌어올려서 탭을 오가도 같은 인스턴스가 유지되게 함.
                    val cardHomeState = remember { CardHomeState() }
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
                                            state = cardHomeState,
                                            hasSensorPermissions = { hasSensorPermissions() },
                                            onRequestSensorPermissions = { checkPermissionsAndStart() },
                                            onStartSensorTracking = { challengeId, execType, resumeCount ->
                                                startTrackingChallenge(challengeId, execType, resumeCount)
                                            },
                                            onStopSensorTracking = { stopMissionService() },
                                            onPauseSensorTracking = { pauseMissionService() },
                                            onResumeSensorTracking = { resumeMissionService() },
                                            onForceSyncSensor = { forceSyncSensorNow() },
                                            onOpenSettings = { openAppSettings() },
                                            onOpenTuntunScore = { currentTab = MainTab.REFERENCE },
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
                                            // ⚠️ 2026-09-08 QA 반영: 여기 있던 onEditWakeSleep = { }(빈 람다)
                                            // 때문에 "자고 일어나는 시각"이 눌러도 아무 반응이 없었음. 이제
                                            // ProfileFlow 안의 WAKE_SLEEP 화면으로 직접 이동해서 파라미터 자체가
                                            // 없어짐(ProfileScreens4.WakeSleepEditScreen).
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

    // ⚠️ 2026-09-07 추가: 앱 안 어디에도 사용자 키를 캐싱해두는 곳이 없어서, 걷기/조깅
    // 케이던스 임계값(신장 구간표)이 항상 기본값(170cm/176cm)으로만 계산되고 있었음.
    // 이 화면(Activity)이 살아있는 동안만 캐싱 - 매번 시작할 때마다 다시 물어보지 않음.
    private var cachedHeightCm: Float? = null
    // ⚠️ 2026-09-07 추가(신장×연령 이중 보정): birth_year/birth_month는 이미 getMe()
    // 응답에 있어서 키처럼 새로 배선할 필요 없이 같이 계산.
    private var cachedAgeYears: Int? = null

    private fun startTrackingChallenge(challengeId: String, execType: String, resumeCount: Int = 0) {
        // ⚠️ 2026-09-07 QA(걷기 미감지) 임시 진단 로그 - 이 함수 자체가 몇 번 호출되는지
        // 확인용(WalkingCadenceManager.start()가 반복 호출되던 문제의 호출부 추적).
        android.util.Log.w(
            "WalkingCadence",
            "MainActivity.startTrackingChallenge() called: execType=$execType resumeCount=$resumeCount"
        )
        lifecycleScope.launch {
            var heightCm = cachedHeightCm
            var ageYears = cachedAgeYears
            if (heightCm == null || ageYears == null) {
                runCatching {
                    val response = ApiClient.profileApi.getMe()
                    if (response.isSuccessful) response.body() else null
                }.getOrNull()?.let { info ->
                    heightCm = info.height_cm?.also { cachedHeightCm = it }
                    val birthYear = info.birth_year
                    val birthMonth = info.birth_month
                    if (birthYear != null && birthMonth != null) {
                        val now = java.util.Calendar.getInstance()
                        var age = now.get(java.util.Calendar.YEAR) - birthYear
                        // 아직 생일이 안 지났으면 만 나이 -1 (birthMonth만 갖고 있어 일자는
                        // 못 따지니 월 단위로만 근사 - 케이던스 보정 용도로는 이 정도면 충분).
                        if (now.get(java.util.Calendar.MONTH) + 1 < birthMonth) age -= 1
                        ageYears = age.also { cachedAgeYears = it }
                    }
                }
            }

            val intent = Intent(this@MainActivity, MissionSensorService::class.java).apply {
                action = MissionSensorService.ACTION_START_TRACKING_CHALLENGE
                putExtra(MissionSensorService.EXTRA_CHALLENGE_ID, challengeId)
                putExtra(MissionSensorService.EXTRA_EXEC_TYPE, execType)
                // ⚠️ 2026-09-06 추가: 서버가 이미 배치 동기화로 갖고 있던 누적치. 0이면
                // 새로 시작하는 것과 동일(리셋), 0보다 크면 그 값부터 이어서 세게 함.
                putExtra(MissionSensorService.EXTRA_RESUME_COUNT, resumeCount)
                // ⚠️ 2026-09-07 추가: 못 가져왔으면(신규 가입 등 아직 키 입력 전) extra
                // 자체를 안 실어서, 서비스 쪽 매니저가 기본값을 쓰게 함.
                if (heightCm != null) putExtra(MissionSensorService.EXTRA_HEIGHT_CM, heightCm!!)
                if (ageYears != null) putExtra(MissionSensorService.EXTRA_AGE_YEARS, ageYears!!)
            }
            ContextCompat.startForegroundService(this@MainActivity, intent)
        }
    }

    private fun stopMissionService() {
        val intent = Intent(this, MissionSensorService::class.java)
        stopService(intent)
        SensorDataHolder.resetAll()
    }

    // ⚠️ 2026-09-04 추가: 센서 측정 일시정지/재개 - sendServiceAction()을 그대로 재사용.
    private fun pauseMissionService() {
        sendServiceAction(MissionSensorService.ACTION_PAUSE_TRACKING)
    }

    // ⚠️ 2026-09-08 반영: "완료하기" 직전에 호출 - 30초 배치 주기를 기다리지 않고
    // 지금 이 순간의 값을 즉시 저장+동기화해달라고 서비스에 요청.
    private fun forceSyncSensorNow() {
        sendServiceAction(MissionSensorService.ACTION_FORCE_SYNC_NOW)
    }

    private fun resumeMissionService() {
        sendServiceAction(MissionSensorService.ACTION_RESUME_TRACKING)
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
