package com.tmtn.app.sensor

import org.junit.Assert.*
import org.junit.Test

class SensorSessionResetTest {
    @Test fun aStoppedMeasurementCannotLookPausedOrMovingInTheNextSession() {
        SensorDataHolder.setServiceRunning(true)
        SensorDataHolder.setSensorPaused(true)
        SensorDataHolder.updateStepInPlaceCount(42)
        SensorDataHolder.updateWalkingSeconds(90)
        SensorDataHolder.setWalkingActive(true)
        SensorDataHolder.updateWalkingDetectedNow(true)
        SensorDataHolder.updateStepDetectedNow(true)
        SensorDataHolder.setServiceError("Previous session failure")
        SensorDataHolder.finishSyncAttempt("previous", true)
        SensorDataHolder.resetAll()
        assertFalse(SensorDataHolder.isServiceRunning.value)
        assertFalse(SensorDataHolder.isSensorPaused.value)
        assertFalse(SensorDataHolder.isWalkingActive.value)
        assertFalse(SensorDataHolder.isWalkingDetectedNow.value)
        assertFalse(SensorDataHolder.isStepDetectedNow.value)
        assertEquals(0, SensorDataHolder.stepInPlaceCount.value)
        assertEquals(0, SensorDataHolder.walkingSeconds.value)
        assertNull(SensorDataHolder.serviceError.value)
        assertNull(SensorDataHolder.syncAttempt.value)
    }
}
