package com.tmtn.app.ui.cardhome

import org.junit.Assert.*
import org.junit.Test

class SensorJourneyStateTest {
    @Test fun readySessionDoesNotMeanMovementIsDetected() {
        assertEquals(SensorJourneyPhase.WAITING, sensorJourneyPhase(.2f, false, false, true))
        assertEquals(SensorJourneyPhase.MOVING, sensorJourneyPhase(.2f, true, false, true))
    }

    @Test fun pauseAndGpsAndErrorOverrideStaleDetection() {
        assertEquals(SensorJourneyPhase.PAUSED, sensorJourneyPhase(.2f, true, true, true))
        assertEquals(SensorJourneyPhase.SIGNAL, sensorJourneyPhase(.2f, true, false, true, waitingForGps = true))
        assertEquals(SensorJourneyPhase.ERROR, sensorJourneyPhase(.2f, true, false, true, "오류"))
        assertEquals(SensorJourneyPhase.PREPARING, sensorJourneyPhase(.2f, true, false, false))
    }

    @Test fun automaticStopAtGoalStillShowsComplete() {
        assertEquals(SensorJourneyPhase.COMPLETE, sensorJourneyPhase(1f, false, true, false))
        assertNotEquals(SensorJourneyPhase.COMPLETE, sensorJourneyPhase(Float.NaN, false, false, true))
    }

    @Test fun exactSecondTargetDoesNotRoundUpToMinutes() {
        val display = computeSensorDisplay("SENSOR_WALKING_DURATION", 0, 0, 0, 0f, 0, 45,
            false, true, false, false, durationTargetSeconds = 90)
        assertEquals("00:45", display.value)
        assertEquals("목표 01:30", display.target)
        assertEquals(.5f, display.progress, .0001f)
        assertTrue(display.isActive)
    }

    @Test fun milestoneMessagesChangeOnlyAtMeasuredThresholds() {
        assertEquals("좋아, 네 페이스대로 해 보자!", sensorJourneyMessage(SensorJourneyPhase.MOVING, .24f))
        assertEquals("한 걸음씩, 제법 멀리 왔네.", sensorJourneyMessage(SensorJourneyPhase.MOVING, .25f))
        assertEquals("벌써 절반이야. 잘 가고 있어!", sensorJourneyMessage(SensorJourneyPhase.MOVING, .5f))
        assertEquals("이제 마지막 구간이야!", sensorJourneyMessage(SensorJourneyPhase.MOVING, .75f))
    }
}
