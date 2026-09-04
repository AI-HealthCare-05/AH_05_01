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

        when (intent?.action) {

            ACTION_START_TRACKING_CHALLENGE -> {
                val challengeId = intent.getStringExtra(EXTRA_CHALLENGE_ID)
                val execType = intent.getStringExtra(EXTRA_EXEC_TYPE)
                if (challengeId != null && execType != null) {
                    startTrackingChallenge(challengeId, execType)
                }
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
    private fun startTrackingChallenge(challengeId: String, execType: String) {
        CurrentChallengeHolder.challengeId = challengeId
        CurrentChallengeHolder.execType = execType

        when (execType) {
            "SENSOR_STEPS" -> {
                stepCounterManager.stop(); stepCounterManager.reset(); stepCounterManager.start()
            }
            "SENSOR_FLOORS_CLIMBED" -> {
                stairClimbManager.stop(); stairClimbManager.reset(); stairClimbManager.start()
            }
            "SENSOR_STEPS_IN_PLACE" -> {
                stepInPlaceManager.stop(); stepInPlaceManager.reset(); stepInPlaceManager.start()
                SensorDataHolder.setStepInPlaceActive(true)
            }
            "SENSOR_RUNNING_DISTANCE" -> {
                runningManager.stop(); runningManager.reset(); runningManager.start()
                SensorDataHolder.setRunningActive(true)
            }
            "SENSOR_RUNNING_DURATION" -> {
                runningCadenceManager.stop(); runningCadenceManager.reset(); runningCadenceManager.start()
                SensorDataHolder.setRunningActive(true)
            }
            "SENSOR_WALKING_DURATION" -> {
                walkingCadenceManager.stop(); walkingCadenceManager.reset(); walkingCadenceManager.start()
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
            var lastSavedAt = 0L
            val saveIntervalMs = 30_000L
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

        return serviceScope.launch {
            db.missionRecordDao().insert(
                MissionRecord(challengeId = challengeId, measurementType = "STEP", value = steps, recordedAt = timestamp)
            )
            db.missionRecordDao().insert(
                MissionRecord(challengeId = challengeId, measurementType = "STAIR", value = floors, recordedAt = timestamp)
            )
            db.missionRecordDao().insert(
                MissionRecord(challengeId = challengeId, measurementType = "STEP_IN_PLACE", value = stepInPlace, recordedAt = timestamp)
            )
            db.missionRecordDao().insert(
                MissionRecord(challengeId = challengeId, measurementType = "RUN_DISTANCE_M", value = runDistanceM.toInt(), recordedAt = timestamp)
            )
            db.missionRecordDao().insert(
                MissionRecord(challengeId = challengeId, measurementType = "RUN_DURATION", value = runSeconds, recordedAt = timestamp)
            )
            db.missionRecordDao().insert(
                MissionRecord(challengeId = challengeId, measurementType = "WALK_DURATION", value = walkSeconds, recordedAt = timestamp)
            )

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
            "SENSOR_FLOORS_CLIMBED" -> "${stairClimbManager.floorsClimbed}계단 · 자동 측정 중"
            else -> "오늘의 미션을 측정하고 있어요"
        }
    }

    companion object {
        const val ACTION_START_TRACKING_CHALLENGE = "com.tmtn.app.ACTION_START_TRACKING_CHALLENGE"
        const val ACTION_STOP_TRACKING = "com.tmtn.app.ACTION_STOP_TRACKING"
        const val EXTRA_CHALLENGE_ID = "EXTRA_CHALLENGE_ID"
        const val EXTRA_EXEC_TYPE = "EXTRA_EXEC_TYPE"

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
