package com.tmtn.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * 제자리걸음/계단 운동기구(스테퍼 등)로 하는 계단 운동을 인정하는 클래스.
 *
 * 기준값(100spm)은 실제 연구(Tudor-Locke, 2018)의 "건강한 성인 1분 최고
 * 케이던스 100spm 이상" 값과 일치한다.
 *
 * 키 보정은 달리기처럼 명확한 실측 계수를 찾지 못해, 기존 비율 방식을 유지한다.
 */
class StairInPlaceManager(
    context: Context,
    heightCm: Float = 170f
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val recentStepTimestamps = ArrayDeque<Long>()
    private val cadenceWindowMs = 4000L

    private val baseThreshold = 100
    private val referenceHeightCm = 170f
    private val activeCadenceThreshold: Int =
        (baseThreshold * (referenceHeightCm / heightCm)).toInt()

    private val stepsPerFloor = 16

    private var validStepsSinceLastFloor = 0

    var floorsClimbed = 0
        private set

    var isActivelyMoving = false
        private set

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
        validStepsSinceLastFloor = 0
        floorsClimbed = 0
        isActivelyMoving = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return

        val now = System.currentTimeMillis()
        recentStepTimestamps.addLast(now)

        while (recentStepTimestamps.isNotEmpty() && now - recentStepTimestamps.first() > cadenceWindowMs) {
            recentStepTimestamps.removeFirst()
        }

        val windowSeconds = cadenceWindowMs / 1000.0
        val currentCadence = (recentStepTimestamps.size / windowSeconds) * 60

        isActivelyMoving = currentCadence >= activeCadenceThreshold

        if (isActivelyMoving) {
            validStepsSinceLastFloor++
            if (validStepsSinceLastFloor >= stepsPerFloor) {
                floorsClimbed++
                validStepsSinceLastFloor = 0
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 사용 안 함
    }
}