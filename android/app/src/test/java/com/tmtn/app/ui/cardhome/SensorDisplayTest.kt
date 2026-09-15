package com.tmtn.app.ui.cardhome

import org.junit.Assert.*
import org.junit.Test

class SensorDisplayTest {
    private fun display(type: String, target: Int = 100, steps: Int = 100, distance: Float = 0f) = computeSensorDisplay(
        type, target, steps, 6, distance, 120, 60, false, false, false, false, stepsInPlace = 40)

    @Test fun stepTargetsReachCompletionAndDoNotUseAnotherSensor() {
        assertEquals(1f, display("SENSOR_STEPS").progress)
        assertEquals(.4f, display("SENSOR_STEPS_IN_PLACE").progress)
        assertEquals(.06f, display("SENSOR_FLOORS_CLIMBED").progress)
    }
    @Test fun invalidTargetsAndSamplesNeverAutoComplete() {
        assertEquals(0f, display("SENSOR_STEPS", target = 0).progress)
        assertEquals(0f, display("SENSOR_RUNNING_DISTANCE", distance = Float.NaN).progress)
        assertEquals(0f, display("UNSUPPORTED").progress)
        assertEquals(0f, display("SENSOR_STEPS", steps = -5).progress)
    }
    @Test fun durationUsesMinutesAndWaitingDoesNotPretendToDetectMovement() {
        val result = display("SENSOR_WALKING_DURATION", target = 2)
        assertEquals("01:00", result.value)
        assertEquals(.5f, result.progress)
        assertFalse(result.isActive)
    }
}
