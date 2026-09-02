package com.tmtn.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * 걷기(시간) 미션 전용 매니저. "실외에서 걷는 시간"을 측정하는 용도.
 *
 * 기준값(70spm)은 실제 연구(Tudor-Locke, 2018)의 "건강한 성인 30분 평균
 * 최고 케이던스 70spm 이상" 값과 일치한다.
 *
 * 키 보정은 달리기처럼 명확한 실측 계수를 찾지 못해, 기존 비율 방식
 * (기준 신장 대비 비율)을 그대로 사용한다.
 */
class WalkingCadenceManager(
    context: Context,
    heightCm: Float = 170f
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val recentStepTimestamps = ArrayDeque<Long>()
    private val cadenceWindowMs = 5000L

    private val baseThreshold = 70
    private val referenceHeightCm = 170f
    private val walkingCadenceThreshold: Int =
        (baseThreshold * (referenceHeightCm / heightCm)).toInt()

    private var lastStepDetectedAt: Long = 0L
    private val walkingTimeoutMs = 3000L

    var isCurrentlyWalking = false
        private set

    var accumulatedWalkingSeconds = 0
        private set

    private var walkingStartedAt: Long? = null

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
        isCurrentlyWalking = false
        accumulatedWalkingSeconds = 0
        walkingStartedAt = null
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

        evaluateWalkingState(now)
    }

    fun checkTimeout() {
        if (!isCurrentlyWalking) return

        val now = System.currentTimeMillis()
        if (now - lastStepDetectedAt > walkingTimeoutMs) {
            walkingStartedAt?.let { started ->
                accumulatedWalkingSeconds += ((lastStepDetectedAt - started) / 1000).toInt()
            }
            isCurrentlyWalking = false
            walkingStartedAt = null
        }
    }

    private fun evaluateWalkingState(now: Long) {
        val windowSeconds = cadenceWindowMs / 1000.0
        val currentCadence = (recentStepTimestamps.size / windowSeconds) * 60

        val walkingNow = currentCadence >= walkingCadenceThreshold

        if (walkingNow && !isCurrentlyWalking) {
            isCurrentlyWalking = true
            walkingStartedAt = now
        } else if (!walkingNow && isCurrentlyWalking) {
            walkingStartedAt?.let { started ->
                accumulatedWalkingSeconds += ((now - started) / 1000).toInt()
            }
            isCurrentlyWalking = false
            walkingStartedAt = null
        }
    }

    fun getCurrentTotalSeconds(): Int {
        val ongoing = if (isCurrentlyWalking && walkingStartedAt != null) {
            ((System.currentTimeMillis() - walkingStartedAt!!) / 1000).toInt()
        } else 0
        return accumulatedWalkingSeconds + ongoing
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 사용 안 함
    }
}