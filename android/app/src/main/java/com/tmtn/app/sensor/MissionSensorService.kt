package com.tmtn.app.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tmtn.app.MainActivity
import com.tmtn.app.R
import com.tmtn.app.data.local.AppDatabase
import com.tmtn.app.data.local.MissionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 화면이 꺼지거나 다른 앱을 써도 계속 측정을 이어가는 백그라운드 서비스.
 *
 * v2 변경사항: currentDailyCardId(하드코딩 0L) 제거.
 * ACTION_START_TRACKING_CHALLENGE로 challengeId·execType을 받아서
 * CurrentChallengeHolder에 저장하고, 해당 exec_type에 맞는 매니저만 reset() 후 시작한다.
 * 저장 직후 서버 동기화도 같이 시도한다.
 */
class MissionSensorService : Service() {

    private lateinit var stepCounterManager: StepCounterManager
    private lateinit var stairClimbManager: StairClimbManager

    private lateinit var stepInPlaceManager: StepCounterManager
    private lateinit var stairInPlaceManager: StairInPlaceManager
    private lateinit var runningManager: RunningManager
    private lateinit var runningCadenceManager: RunningCadenceManager
    private lateinit var walkingCadenceManager: WalkingCadenceManager

    private var updateJob: Job? = null
    // ⚠️ 2026-09-08 반영: ACTION_FORCE_SYNC_NOW(완료 직전 강제 동기화)에서도 접근해야
    // 해서, startUpdateLoop() 안의 지역 변수였던 걸 클래스 필드로 옮김.
    private var lastSavedAt = 0L
    private val saveIntervalMs = 30_000L
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private val db by lazy { AppDatabase.getInstance(this) }

    private val channelId = "mission_tracking_channel"
    private val notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        stepCounterManager = StepCounterManager(this, missionContext = "STEP")
        stairClimbManager = StairClimbManager(this)
        stepInPlaceManager = StepCounterManager(this, missionContext = "STEP_IN_PLACE")
        stairInPlaceManager = StairInPlaceManager(this)
        runningManager = RunningManager(this)
        runningCadenceManager = RunningCadenceManager(this)
        walkingCadenceManager = WalkingCadenceManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(notificationId, buildNotification())

        // ⚠️ 2026-09-07 QA(걷기 미감지) 임시 진단 로그 - onStartCommand 자체가 몇 번,
        // 어떤 action으로 불리는지 확인용. 원인 확정되면 지워도 됨.
        android.util.Log.w("WalkingCadence", "onStartCommand: action=${intent?.action} startId=$startId")

        when (intent?.action) {

            ACTION_START_TRACKING_CHALLENGE -> {
                val challengeId = intent.getStringExtra(EXTRA_CHALLENGE_ID)
                val execType = intent.getStringExtra(EXTRA_EXEC_TYPE)
                val resumeCount = intent.getIntExtra(EXTRA_RESUME_COUNT, 0)
                // ⚠️ 2026-09-09 QA 반영: 백그라운드 알림 문구가 목표를 넘겨서 표시되던
                // 문제 - 서비스가 목표값 자체를 몰랐음. 없으면(옛 버전 호출 등) null로 두고
                // 알림에서는 캡 없이(그대로) 표시함.
                val targetValue = if (intent.hasExtra(EXTRA_TARGET_VALUE)) {
                    intent.getIntExtra(EXTRA_TARGET_VALUE, 0)
                } else {
                    null
                }
                val heightCm = if (intent.hasExtra(EXTRA_HEIGHT_CM)) {
                    intent.getFloatExtra(EXTRA_HEIGHT_CM, 0f)
                } else {
                    null
                }
                // ⚠️ 2026-09-07 추가(신장×연령 이중 보정): 없으면 매니저가 만들어질 때
                // 쓰는 기본값(보정 없음, ×1.0)으로 유지.
                val ageYears = if (intent.hasExtra(EXTRA_AGE_YEARS)) {
                    intent.getIntExtra(EXTRA_AGE_YEARS, 0)
                } else {
                    null
                }
                // ⚠️ 2026-09-07 추가(중복 START 방어): 지금까지는 START 요청이 짧은
                // 시간에 여러 번 들어오면(원인은 별도 조사 중 - 화면 재구성/버튼 중복 클릭
                // 등 가능성) 매번 센서를 stop→reset→start로 리셋해서, 실제로 걷고 있어도
                // 걸음이 쌓일 새 없이 계속 0으로 되돌아갔음. 이미 같은 챌린지를 같은
                // exec_type으로, 일시정지 아닌 상태로 추적 중이면 재시작 없이 조용히 무시.
                val alreadyTracking = CurrentChallengeHolder.challengeId == challengeId &&
                    CurrentChallengeHolder.execType == execType &&
                    SensorDataHolder.isServiceRunning.value &&
                    !SensorDataHolder.isSensorPaused.value
                if (alreadyTracking) {
                    android.util.Log.w(
                        "WalkingCadence",
                        "duplicate START ignored: challengeId=$challengeId execType=$execType"
                    )
                } else if (challengeId != null && execType != null) {
                    startTrackingChallenge(challengeId, execType, resumeCount, heightCm, ageYears, targetValue)
                }
            }

            // ⚠️ 2026-09-04 추가: 센서 측정 "일시정지" - 자가타이머형과 같은 개념. 지금 돌고
            // 있는 exec_type의 매니저만 멈춤(다른 매니저는 안 건드림). reset()은 안 부르므로
            // 그동안 쌓인 값은 그대로 유지되고, 새로 움직이기 전까지만 안 늘어남.
            ACTION_PAUSE_TRACKING -> {
                when (CurrentChallengeHolder.execType) {
                    "SENSOR_RUNNING_DISTANCE", "SENSOR_RUNNING_DURATION" -> {
                        runningManager.stop(); runningCadenceManager.stop()
                        // ⚠️ 2026-09-07 추가: stop()만으로는 isCurrentlyRunning/시작시각이 안 지워져서
                        // checkTimeout()이 뒤늦게(최대 ~3초) 정산하기 전까지 "일시정지 직후 값이 확 뜀"
                        // 현상이 생김. settleOngoing()으로 즉시 정산.
                        runningCadenceManager.settleOngoing()
                        // ⚠️ 2026-09-07(추가 수정): settleOngoing()이 매니저 내부값만 갱신하고
                        // 화면이 실제로 구독하는 SensorDataHolder는 안 건드려서, 반영이 다음
                        // 업데이트 루프(0.5초 주기)까지 지연됐음 - "일시정지 누르는 순간 갑자기
                        // 확 올라간 것처럼" 보였던 원인. 여기서 바로 최신값을 밀어줌.
                        SensorDataHolder.updateRunningSeconds(runningCadenceManager.getCurrentTotalSeconds())
                        SensorDataHolder.setRunningActive(false)
                        SensorDataHolder.updateRunningDetectedNow(false)
                    }
                    "SENSOR_WALKING_DURATION" -> {
                        walkingCadenceManager.stop()
                        walkingCadenceManager.settleOngoing()
                        SensorDataHolder.updateWalkingSeconds(walkingCadenceManager.getCurrentTotalSeconds())
                        SensorDataHolder.setWalkingActive(false)
                        SensorDataHolder.updateWalkingDetectedNow(false)
                    }
                    "SENSOR_FLOORS_CLIMBED" -> {
                        stairClimbManager.stop()
                        SensorDataHolder.updateFloorsClimbed(stairClimbManager.floorsClimbed)
                        SensorDataHolder.updateFloorsClimbedDetectedNow(false)
                    }
                    "SENSOR_STEPS" -> {
                        stepCounterManager.stop()
                        SensorDataHolder.updateStepCount(stepCounterManager.stepCount)
                        SensorDataHolder.updateStepDetectedNow(false)
                    }
                }
                SensorDataHolder.setSensorPaused(true)
            }

            // ⚠️ 2026-09-04 추가: 센서 측정 "이어서 측정". start()만 다시 부르고 reset()은
            // 안 불러서, 일시정지 전까지 쌓인 값에 이어서 계속 잼.
            ACTION_RESUME_TRACKING -> {
                when (CurrentChallengeHolder.execType) {
                    "SENSOR_RUNNING_DISTANCE" -> {
                        runningManager.start()
                        SensorDataHolder.setRunningActive(true)
                    }
                    "SENSOR_RUNNING_DURATION" -> {
                        runningCadenceManager.start()
                        SensorDataHolder.setRunningActive(true)
                    }
                    "SENSOR_WALKING_DURATION" -> {
                        walkingCadenceManager.start()
                        SensorDataHolder.setWalkingActive(true)
                    }
                    "SENSOR_FLOORS_CLIMBED" -> stairClimbManager.start()
                    "SENSOR_STEPS" -> stepCounterManager.start()
                }
                SensorDataHolder.setSensorPaused(false)
            }

            // ⚠️ 2026-09-08 반영: "완료하기" 직전에 UI가 호출 - 30초 배치 주기를 기다리지
            // 않고 지금 이 순간의 값으로 즉시 저장+동기화. 서버가 최신 진행값을 모른 채
            // 완료 판정을 내리는 문제(로그로 확인됨)를 없앰.
            ACTION_FORCE_SYNC_NOW -> {
                val now = System.currentTimeMillis()
                saveToLocalDbAndSync(
                    steps = stepCounterManager.stepCount,
                    floors = stairClimbManager.floorsClimbed,
                    stepInPlace = stepInPlaceManager.stepCount,
                    stairInPlace = stairInPlaceManager.floorsClimbed,
                    runDistanceM = runningManager.totalDistanceMeters,
                    runSeconds = runningCadenceManager.getCurrentTotalSeconds(),
                    walkSeconds = walkingCadenceManager.getCurrentTotalSeconds(),
                    timestamp = now
                )
                lastSavedAt = now
            }

            // Figma C09~C18: "측정 끝내기" 화면 버튼 + C15(백그라운드 알림)의 "끝내기" 액션이
            // 공통으로 이걸 씀 — 지금 CurrentChallengeHolder에 어떤 exec_type이 돌고 있는지
            // 보고 그것만 정확히 멈춤 (다른 매니저는 안 건드림).
            ACTION_STOP_TRACKING -> {
                when (CurrentChallengeHolder.execType) {
                    "SENSOR_STEPS" -> stepCounterManager.stop()
                    "SENSOR_FLOORS_CLIMBED" -> stairClimbManager.stop()
                    "SENSOR_STEPS_IN_PLACE" -> {
                        stepInPlaceManager.stop()
                        SensorDataHolder.setStepInPlaceActive(false)
                    }
                    "SENSOR_RUNNING_DISTANCE", "SENSOR_RUNNING_DURATION" -> {
                        runningManager.stop(); runningCadenceManager.stop()
                        SensorDataHolder.setRunningActive(false)
                    }
                    "SENSOR_WALKING_DURATION" -> {
                        walkingCadenceManager.stop()
                        SensorDataHolder.setWalkingActive(false)
                    }
                }
                updateJob?.cancel()
                SensorDataHolder.setServiceRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_START_STEP_IN_PLACE -> {
                stepInPlaceManager.reset()
                stepInPlaceManager.start()
                SensorDataHolder.setStepInPlaceActive(true)
            }
            ACTION_STOP_STEP_IN_PLACE -> {
                stepInPlaceManager.stop()
                SensorDataHolder.setStepInPlaceActive(false)
            }

            ACTION_START_STAIR_IN_PLACE -> {
                stairInPlaceManager.reset()
                stairInPlaceManager.start()
                SensorDataHolder.setStairInPlaceActive(true)
            }
            ACTION_STOP_STAIR_IN_PLACE -> {
                stairInPlaceManager.stop()
                SensorDataHolder.setStairInPlaceActive(false)
            }

            ACTION_START_RUNNING_DISTANCE -> {
                runningManager.reset()
                runningManager.start()
                SensorDataHolder.setRunningActive(true)
            }
            ACTION_START_RUNNING_DURATION -> {
                runningCadenceManager.reset()
                runningCadenceManager.start()
                SensorDataHolder.setRunningActive(true)
            }
            ACTION_STOP_RUNNING -> {
                runningManager.stop()
                runningCadenceManager.stop()
                SensorDataHolder.setRunningActive(false)
            }

            ACTION_START_WALKING -> {
                walkingCadenceManager.reset()
                walkingCadenceManager.start()
                SensorDataHolder.setWalkingActive(true)
            }
            ACTION_STOP_WALKING -> {
                walkingCadenceManager.stop()
                SensorDataHolder.setWalkingActive(false)
            }

            else -> {
                stepCounterManager.stop()
                stairClimbManager.stop()
                stepCounterManager.reset()
                stairClimbManager.reset()
                stepCounterManager.start()
                stairClimbManager.start()

                SensorDataHolder.setServiceRunning(true)
                startUpdateLoop()
            }
        }

        return START_STICKY
    }

    /**
     * exec_type에 맞는 매니저만 reset() 후 시작. 이래야 이전 챌린지에서 세던 값이
     * 이번 챌린지로 넘어오지 않는다 (질문하셨던 "중복 안 되냐"의 실제 대비책).
     */
    private fun startTrackingChallenge(
        challengeId: String,
        execType: String,
        resumeCount: Int = 0,
        heightCm: Float? = null,
        ageYears: Int? = null,
        targetValue: Int? = null,
    ) {
        CurrentChallengeHolder.challengeId = challengeId
        CurrentChallengeHolder.execType = execType
        CurrentChallengeHolder.targetValue = targetValue

        // ⚠️ 2026-09-07 반영(신장×연령 이중 보정): 걷기/조깅은 신장에 따라 케이던스
        // 임계값이 달라지고, 나이가 들수록 더 낮게 잡아야 함(연구 데이터: 60대 이상부터
        // peak 케이던스가 뚜렷하게 낮아짐). 실제 사용자 키·나이 중 하나라도 왔으면
        // 그 값(없는 쪽은 기본값)으로 매니저를 새로 만들어서 정확한 임계값을 씀
        // (onCreate() 시점엔 아직 몰라서 기본값 170cm/176cm·보정 없음으로 만들어져 있었음).
        if (heightCm != null || ageYears != null) {
            if (execType == "SENSOR_WALKING_DURATION") {
                walkingCadenceManager.stop()
                walkingCadenceManager = WalkingCadenceManager(this, heightCm ?: 170f, ageYears ?: 0)
            } else if (execType == "SENSOR_RUNNING_DURATION") {
                runningCadenceManager.stop()
                runningCadenceManager = RunningCadenceManager(this, heightCm ?: 176f, ageYears ?: 0)
            }
        }

        // ⚠️ 2026-09-06 반영: 예전엔 여기서 무조건 reset()을 불러서, 뒤로가기 등으로
        // 화면을 벗어났다가 "진행 중인 미션 확인"으로 돌아오면 로컬 센서가 0부터 다시
        // 셌음(서버는 30초마다 배치 동기화로 이미 정확한 누적치를 갖고 있었는데도).
        // resumeCount가 0보다 크면 "이어서 세기"(resumeFrom), 0이면 원래대로 새로 시작.
        when (execType) {
            "SENSOR_STEPS" -> {
                stepCounterManager.stop()
                if (resumeCount > 0) stepCounterManager.resumeFrom(resumeCount) else stepCounterManager.reset()
                stepCounterManager.start()
            }
            "SENSOR_FLOORS_CLIMBED" -> {
                stairClimbManager.stop()
                if (resumeCount > 0) stairClimbManager.resumeFrom(resumeCount) else stairClimbManager.reset()
                stairClimbManager.start()
            }
            "SENSOR_STEPS_IN_PLACE" -> {
                stepInPlaceManager.stop()
                if (resumeCount > 0) stepInPlaceManager.resumeFrom(resumeCount) else stepInPlaceManager.reset()
                stepInPlaceManager.start()
                SensorDataHolder.setStepInPlaceActive(true)
            }
            "SENSOR_RUNNING_DISTANCE" -> {
                runningManager.stop()
                if (resumeCount > 0) runningManager.resumeFrom(resumeCount) else runningManager.reset()
                runningManager.start()
                SensorDataHolder.setRunningActive(true)
            }
            "SENSOR_RUNNING_DURATION" -> {
                // ⚠️ 2026-09-06 반영: 이제 걷기/뛰기(시간)도 COUNT형과 같은 원칙으로
                // 통일 - 서버가 계산해서 준 실제 경과 시간(resumeCount, 여기선 초 단위)부터
                // 이어서 셈.
                runningCadenceManager.stop()
                if (resumeCount > 0) runningCadenceManager.resumeFrom(resumeCount) else runningCadenceManager.reset()
                runningCadenceManager.start()
                SensorDataHolder.setRunningActive(true)
            }
            "SENSOR_WALKING_DURATION" -> {
                walkingCadenceManager.stop()
                if (resumeCount > 0) walkingCadenceManager.resumeFrom(resumeCount) else walkingCadenceManager.reset()
                walkingCadenceManager.start()
                SensorDataHolder.setWalkingActive(true)
            }
        }

        SensorDataHolder.setServiceRunning(true)
        startUpdateLoop()
    }

    /** 0.5초마다 화면에 값 반영, 30초마다 로컬 DB 저장 + 서버 동기화 시도.
     * 4초(8틱)마다 C15(백그라운드 알림)도 실제 값으로 다시 그림. */
    private fun startUpdateLoop() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            var tick = 0

            while (isActive) {
                val steps = stepCounterManager.stepCount
                val floors = stairClimbManager.floorsClimbed

                SensorDataHolder.updateStepCount(steps)
                SensorDataHolder.updateFloorsClimbed(floors)
                SensorDataHolder.updateStepInPlaceCount(stepInPlaceManager.stepCount)
                SensorDataHolder.updateStairInPlaceFloors(stairInPlaceManager.floorsClimbed)
                SensorDataHolder.updateRunningDistanceM(runningManager.totalDistanceMeters)
                SensorDataHolder.updateRunningSeconds(runningCadenceManager.getCurrentTotalSeconds())
                SensorDataHolder.updateWalkingSeconds(walkingCadenceManager.getCurrentTotalSeconds())
                // ⚠️ 2026-09-07 QA 반영: "지금 이 순간 실제로 케이던스가 감지되는지"를
                // 0.5초마다 같이 publish함 - isRunningActive/isWalkingActive(세션 on/off)와
                // 구분해서 화면이 진짜 움직임 여부를 실시간으로 보여줄 수 있게 함.
                SensorDataHolder.updateRunningDetectedNow(runningCadenceManager.isCurrentlyRunning)
                SensorDataHolder.updateWalkingDetectedNow(walkingCadenceManager.isCurrentlyWalking)
                // ⚠️ 2026-09-07 반영: 계단/걸음수형은 이 실시간 감지 publish 자체가 없어서
                // 화면(computeSensorDisplay)이 항상 true로 하드코딩돼 있었음(QA - 가만히
                // 있어도 "움직임을 확인했어요").
                SensorDataHolder.updateFloorsClimbedDetectedNow(stairClimbManager.isRecentlyActive())
                SensorDataHolder.updateStepDetectedNow(stepCounterManager.isRecentlyActive())

                walkingCadenceManager.checkTimeout()
                runningCadenceManager.checkTimeout()

                val now = System.currentTimeMillis()
                if (now - lastSavedAt >= saveIntervalMs) {
                    saveToLocalDbAndSync(
                        steps = steps,
                        floors = floors,
                        stepInPlace = stepInPlaceManager.stepCount,
                        stairInPlace = stairInPlaceManager.floorsClimbed,
                        runDistanceM = runningManager.totalDistanceMeters,
                        runSeconds = runningCadenceManager.getCurrentTotalSeconds(),
                        walkSeconds = walkingCadenceManager.getCurrentTotalSeconds(),
                        timestamp = now
                    )
                    lastSavedAt = now
                }

                tick++
                if (tick % 8 == 0) { // 0.5초 * 8 = 4초마다
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(notificationId, buildNotification())
                }

                delay(500)
            }
        }
    }

    /**
     * challengeId가 아직 없으면(카드 선택 전이거나 테스트 화면에서 그냥 눌러본 경우)
     * 저장 자체를 하지 않는다 — 예전처럼 0으로 저장해서 나중에 헷갈리는 것보다 안전함.
     *
     * ⚠️ 2026-09-08 반영: 예전엔 지금 activeExecType이 뭐든 상관없이 6개 타입(STEP·STAIR·
     * STEP_IN_PLACE·RUN_DISTANCE_M·RUN_DURATION·WALK_DURATION)을 매번 다 저장했음. 서버는
     * 챌린지의 exec_type과 안 맞는 measurement_type을 거부하므로(sensor_service.py
     * ingest_batch), 계단 미션 하나 진행하면서 매 배치마다 "1건 승인·5건 거부"가 반복
     * 찍히고 있었음 - 기능상 계단값 자체는 정상 전송됐지만, 불필요한 DB 쓰기·네트워크
     * 페이로드·로그 노이즈였음. 지금 CurrentChallengeHolder.execType에 해당하는 것만 저장.
     */
    private fun saveToLocalDbAndSync(
        steps: Int,
        floors: Int,
        stepInPlace: Int,
        stairInPlace: Int,
        runDistanceM: Float,
        runSeconds: Int,
        walkSeconds: Int,
        timestamp: Long
    ): kotlinx.coroutines.Job? {
        val challengeId = CurrentChallengeHolder.challengeId
        if (challengeId == null) {
            return null
        }

        val record = when (CurrentChallengeHolder.execType) {
            "SENSOR_STEPS" -> MissionRecord(challengeId = challengeId, measurementType = "STEP", value = steps, recordedAt = timestamp)
            "SENSOR_FLOORS_CLIMBED" -> MissionRecord(challengeId = challengeId, measurementType = "STAIR", value = floors, recordedAt = timestamp)
            "SENSOR_STEPS_IN_PLACE" -> MissionRecord(challengeId = challengeId, measurementType = "STEP_IN_PLACE", value = stepInPlace, recordedAt = timestamp)
            "SENSOR_RUNNING_DISTANCE" -> MissionRecord(challengeId = challengeId, measurementType = "RUN_DISTANCE_M", value = runDistanceM.toInt(), recordedAt = timestamp)
            "SENSOR_RUNNING_DURATION" -> MissionRecord(challengeId = challengeId, measurementType = "RUN_DURATION", value = runSeconds, recordedAt = timestamp)
            "SENSOR_WALKING_DURATION" -> MissionRecord(challengeId = challengeId, measurementType = "WALK_DURATION", value = walkSeconds, recordedAt = timestamp)
            else -> null
        } ?: return null

        return serviceScope.launch {
            db.missionRecordDao().insert(record)
            // 저장 직후 바로 동기화 시도. 실패해도 isSynced=false로 남아 다음 저장 때 재시도됨.
            MissionSyncManager.syncUnsyncedRecords(this@MissionSensorService)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        updateJob?.cancel()

        // ⚠️ 2026-09-04 희주조교님 피드백(P0 ③, "끝내기 → 기록이 날아감") 반영: 예전엔 위
        // saveToLocalDbAndSync()가 코루틴을 띄우자마자(=아직 DB insert도 안 끝난 상태에서)
        // 바로 아래줄 serviceScope.cancel()이 그 코루틴을 취소시켜버렸음. 그래서 "끝내기"를
        // 누른 그 순간의 마지막 증가분(정확히 사용자가 지금까지 채운 값)이 거의 항상
        // 저장·동기화되기 전에 날아갔음. 이 마지막 저장+동기화 코루틴이 실제로 끝난 뒤에만
        // 스코프를 정리하도록 순서를 바꿈.
        val finalSyncJob = saveToLocalDbAndSync(
            steps = stepCounterManager.stepCount,
            floors = stairClimbManager.floorsClimbed,
            stepInPlace = stepInPlaceManager.stepCount,
            stairInPlace = stairInPlaceManager.floorsClimbed,
            runDistanceM = runningManager.totalDistanceMeters,
            runSeconds = runningCadenceManager.getCurrentTotalSeconds(),
            walkSeconds = walkingCadenceManager.getCurrentTotalSeconds(),
            timestamp = System.currentTimeMillis()
        )
        if (finalSyncJob != null) {
            finalSyncJob.invokeOnCompletion { serviceScope.cancel() }
        } else {
            serviceScope.cancel()
        }

        stepCounterManager.stop()
        stairClimbManager.stop()
        stepInPlaceManager.stop()
        stairInPlaceManager.stop()
        runningManager.stop()
        runningCadenceManager.stop()
        walkingCadenceManager.stop()

        SensorDataHolder.setServiceRunning(false)
        SensorDataHolder.setStepInPlaceActive(false)
        SensorDataHolder.setStairInPlaceActive(false)
        SensorDataHolder.setRunningActive(false)
        SensorDataHolder.setWalkingActive(false)

        CurrentChallengeHolder.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "미션 측정 중",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /** Figma C15 · 백그라운드 알림. 실제 진행 상황(exec_type별)과 "끝내기"/"앱 열기" 액션 포함.
     * ⚠️ "일시정지" 버튼은 Figma에 있지만, 지금 센서 매니저들에 "수동 일시정지" 기능 자체가
     * 없어서(자동 일시정지만 있음) 넣지 않음. 필요하면 매니저 쪽에 pause()를 먼저 만들어야 함. */
    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MissionSensorService::class.java).setAction(ACTION_STOP_TRACKING),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("틈튼이 활동을 측정하고 있어요")
            .setContentText(buildContentText())
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "끝내기", stopIntent)
            .addAction(0, "앱 열기", openAppIntent)
            .build()
    }

    /** Figma C15 본문 텍스트 - CurrentChallengeHolder.execType 기준으로 지금 값을 사람이 읽을 문구로. */
    private fun buildContentText(): String {
        fun mmss(totalSeconds: Int): String = "%d분 %02d초".format(totalSeconds / 60, totalSeconds % 60)
        return when (CurrentChallengeHolder.execType) {
            "SENSOR_WALKING_DURATION" -> "${mmss(walkingCadenceManager.getCurrentTotalSeconds())} · 자동 측정 중"
            "SENSOR_RUNNING_DISTANCE" -> "%.2fkm · 자동 측정 중".format(runningManager.totalDistanceMeters / 1000f)
            "SENSOR_RUNNING_DURATION" -> "${mmss(runningCadenceManager.getCurrentTotalSeconds())} · 자동 측정 중"
            "SENSOR_FLOORS_CLIMBED" -> {
                val target = CurrentChallengeHolder.targetValue
                val floors = if (target != null) stairClimbManager.floorsClimbed.coerceAtMost(target) else stairClimbManager.floorsClimbed
                "${floors}계단 · 자동 측정 중"
            }
            else -> "오늘의 미션을 측정하고 있어요"
        }
    }

    companion object {
        const val ACTION_START_TRACKING_CHALLENGE = "com.tmtn.app.ACTION_START_TRACKING_CHALLENGE"
        const val ACTION_STOP_TRACKING = "com.tmtn.app.ACTION_STOP_TRACKING"
        const val ACTION_PAUSE_TRACKING = "com.tmtn.app.ACTION_PAUSE_TRACKING"
        const val ACTION_RESUME_TRACKING = "com.tmtn.app.ACTION_RESUME_TRACKING"
        // ⚠️ 2026-09-08 반영: "완료하기"가 서버의 30초 배치 동기화 타이밍에 그대로 기대고
        // 있어서, 계단을 다 오르고 곧바로 완료를 누르면 서버가 아직 오래된 값(예: 9칸)만
        // 알고 있는 상태로 목표 미달성 처리되는 문제가 있었음(로그로 확인 - 완료 시점엔
        // 누적=0이었다가 한참 뒤에야 9->12로 반영됨). 완료 버튼을 누르는 순간 "지금 이
        // 값으로 당장 저장+동기화해라"를 서비스에 직접 요청하는 액션.
        const val ACTION_FORCE_SYNC_NOW = "com.tmtn.app.ACTION_FORCE_SYNC_NOW"
        const val EXTRA_CHALLENGE_ID = "EXTRA_CHALLENGE_ID"
        const val EXTRA_EXEC_TYPE = "EXTRA_EXEC_TYPE"
        // ⚠️ 2026-09-06 추가: 서버가 이미 배치 동기화로 갖고 있던 누적치 - 재개 시 이 값부터
        // 이어서 셈. 0이면 새로 시작하는 것과 동일(내부적으로 reset과 같은 결과).
        const val EXTRA_RESUME_COUNT = "EXTRA_RESUME_COUNT"
        // ⚠️ 2026-09-09 QA 반영: 백그라운드 알림 문구 캡용.
        const val EXTRA_TARGET_VALUE = "EXTRA_TARGET_VALUE"
        // ⚠️ 2026-09-07 추가: 걷기/조깅 케이던스 임계값(신장 구간표) 계산용. 없으면
        // 매니저가 만들어질 때 쓴 기본값(170cm/176cm) 그대로 유지.
        const val EXTRA_HEIGHT_CM = "EXTRA_HEIGHT_CM"
        // ⚠️ 2026-09-07 추가(신장×연령 이중 보정): 없으면 보정 없음(×1.0).
        const val EXTRA_AGE_YEARS = "EXTRA_AGE_YEARS"

        const val ACTION_START_STEP_IN_PLACE = "com.tmtn.app.ACTION_START_STEP_IN_PLACE"
        const val ACTION_STOP_STEP_IN_PLACE = "com.tmtn.app.ACTION_STOP_STEP_IN_PLACE"

        const val ACTION_START_STAIR_IN_PLACE = "com.tmtn.app.ACTION_START_STAIR_IN_PLACE"
        const val ACTION_STOP_STAIR_IN_PLACE = "com.tmtn.app.ACTION_STOP_STAIR_IN_PLACE"

        const val ACTION_START_RUNNING_DISTANCE = "com.tmtn.app.ACTION_START_RUNNING_DISTANCE"
        const val ACTION_START_RUNNING_DURATION = "com.tmtn.app.ACTION_START_RUNNING_DURATION"
        const val ACTION_STOP_RUNNING = "com.tmtn.app.ACTION_STOP_RUNNING"

        const val ACTION_START_WALKING = "com.tmtn.app.ACTION_START_WALKING"
        const val ACTION_STOP_WALKING = "com.tmtn.app.ACTION_STOP_WALKING"
    }
}
