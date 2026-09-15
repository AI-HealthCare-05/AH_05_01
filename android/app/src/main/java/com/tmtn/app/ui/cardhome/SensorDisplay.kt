package com.tmtn.app.ui.cardhome

import java.util.Locale

internal data class SensorDisplay(val value: String, val target: String, val progress: Float, val isActive: Boolean)

/** Presentation only. Inputs are the existing sensor model's accumulated measurements. */
internal fun computeSensorDisplay(
    execType: String, targetValue: Int, steps: Int, floors: Int, distanceM: Float,
    runningSeconds: Int, walkingSeconds: Int, isRunningDetectedNow: Boolean,
    isWalkingDetectedNow: Boolean, isFloorsClimbedDetectedNow: Boolean, isStepDetectedNow: Boolean,
    stepsInPlace: Int = 0,
): SensorDisplay {
    fun progress(value: Float, goal: Float): Float =
        if (goal <= 0f || !value.isFinite()) 0f else (value.coerceAtLeast(0f) / goal).coerceIn(0f, 1f)
    fun clock(seconds: Int): String = seconds.coerceAtLeast(0).let { "%02d:%02d".format(Locale.KOREA, it / 60, it % 60) }
    val target = if (targetValue > 0) targetValue else 0
    return when (execType) {
        "SENSOR_WALKING_DURATION", "SENSOR_RUNNING_DURATION" -> {
            val value = if (execType == "SENSOR_WALKING_DURATION") walkingSeconds else runningSeconds
            val active = if (execType == "SENSOR_WALKING_DURATION") isWalkingDetectedNow else isRunningDetectedNow
            val seconds = target.toLong().times(60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            SensorDisplay(clock(value), "목표 ${clock(seconds)}", progress(value.toFloat(), seconds.toFloat()), active)
        }
        "SENSOR_RUNNING_DISTANCE" -> {
            val meters = if (distanceM.isFinite()) distanceM.coerceAtLeast(0f) else 0f
            SensorDisplay("%.2f km".format(Locale.KOREA, meters / 1000f), "목표 %.2f km".format(Locale.KOREA, target / 1000f),
                progress(meters, target.toFloat()), isRunningDetectedNow)
        }
        "SENSOR_FLOORS_CLIMBED" -> {
            val value = floors.coerceAtLeast(0)
            SensorDisplay("$value 계단", "목표 ${target}계단", progress(value.toFloat(), target.toFloat()), isFloorsClimbedDetectedNow)
        }
        "SENSOR_STEPS", "SENSOR_STEPS_IN_PLACE" -> {
            val value = (if (execType == "SENSOR_STEPS") steps else stepsInPlace).coerceAtLeast(0)
            SensorDisplay("$value 걸음", "목표 ${target}보", progress(value.toFloat(), target.toFloat()), isStepDetectedNow)
        }
        else -> SensorDisplay("—", "측정 방법 확인 중", 0f, false)
    }
}
