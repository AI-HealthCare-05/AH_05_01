package com.tmtn.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * GPS 없이, 걸음 빈도(케이던스)로 "달리는 중"인지 판정해서 시간을 누적하는 클래스.
 *
 * 기존 버그: WalkingCadenceManager와 동일한 구조적 문제가 있었다.
 * "달리는 중 → 정지"로 상태를 바꾸는 판정이 새 걸음 이벤트가 들어와야만
 * 실행되기 때문에, 뛰다가 완전히 걸음을 멈추면(예: 걷기로 전환하며 걸음이
 * 뜸해짐) 판정 자체가 다시 실행되지 않아 시간이 계속 흐르는 문제가 있었다
 * (실측 결과 5번 중 1번 발생).
 *
 * 해결: checkTimeout()을 만들어서 외부(서비스의 주기적 갱신 루프)에서
 * 걸음 이벤트와 무관하게 주기적으로 호출해, 마지막 걸음 이후 일정 시간이
 * 지나면 강제로 멈춤 처리하도록 했다.
 */
class RunningCadenceManager(
    context: Context,
    heightCm: Float = 176f
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val recentStepTimestamps = ArrayDeque<Long>()
    private val cadenceWindowMs = 4000L

    private val baseThreshold = 140
    private val referenceHeightCm = 176f
    private val spmPerCm = 6f / 5f
    private val runningCadenceThreshold: Int =
        (baseThreshold - (heightCm - referenceHeightCm) * spmPerCm).toInt()

    // 마지막으로 걸음이 감지된 시각. 타임아웃 판단 기준.
    private var lastStepDetectedAt: Long = 0L

    // 마지막 걸음 이후 이 시간(ms)이 지나면, 새 걸음이 없어도 강제로 멈춤 처리한다.
    private val runningTimeoutMs = 3000L

    var isCurrentlyRunning = false
        private set

    var accumulatedRunningSeconds = 0
        private set

    private var runningStartedAt: Long? = null

    fun isAvailable(): Boolean = stepDetector != null

    fun start() {
        stepDetector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun reset() {
        recentStepTimestamps.clear()
        isCurrentlyRunning = false
        accumulatedRunningSeconds = 0
        runningStartedAt = null
        lastStepDetectedAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return

        val now = System.currentTimeMillis()
        lastStepDetectedAt = now
        recentStepTimestamps.addLast(now)

        while (recentStepTimestamps.isNotEmpty() && now - recentStepTimestamps.first() > cadenceWindowMs) {
            recentStepTimestamps.removeFirst()
        }

        evaluateRunningState(now)
    }

    /**
     * 걸음 이벤트와 무관하게, 외부(서비스의 갱신 루프)에서 주기적으로 호출해야 하는 함수.
     * 마지막 걸음 이후 runningTimeoutMs 이상 지났는데도 "달리는 중"으로 남아있다면
     * 강제로 멈춤 처리한다.
     */
    fun checkTimeout() {
        if (!isCurrentlyRunning) return

        val now = System.currentTimeMillis()
        if (now - lastStepDetectedAt > runningTimeoutMs) {
            runningStartedAt?.let { started ->
                accumulatedRunningSeconds += ((lastStepDetectedAt - started) / 1000).toInt()
            }
            isCurrentlyRunning = false
            runningStartedAt = null
        }
    }

    private fun evaluateRunningState(now: Long) {
        val windowSeconds = cadenceWindowMs / 1000.0
        val currentCadence = (recentStepTimestamps.size / windowSeconds) * 60

        val runningNow = currentCadence >= runningCadenceThreshold

        if (runningNow && !isCurrentlyRunning) {
            isCurrentlyRunning = true
            runningStartedAt = now
        } else if (!runningNow && isCurrentlyRunning) {
            runningStartedAt?.let { started ->
                accumulatedRunningSeconds += ((now - started) / 1000).toInt()
            }
            isCurrentlyRunning = false
            runningStartedAt = null
        }
    }

    fun getCurrentTotalSeconds(): Int {
        val ongoing = if (isCurrentlyRunning && runningStartedAt != null) {
            ((System.currentTimeMillis() - runningStartedAt!!) / 1000).toInt()
        } else 0
        return accumulatedRunningSeconds + ongoing
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 사용 안 함
    }
}