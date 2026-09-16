package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.formatDistanceMeters
import org.junit.Assert.*
import org.junit.Test

class QaPresentationTest {
    @Test fun meterTextIsConsistentAndNeverRoundsUpToTarget() {
        assertEquals("0 m", formatDistanceMeters(0f))
        assertEquals("199 m", formatDistanceMeters(199.99f))
        assertEquals("200 m", formatDistanceMeters(200f))
        assertEquals("1200 m", formatDistanceMeters(1200f))
        assertEquals("0 m", formatDistanceMeters(Float.NaN))
        assertEquals("0 m", formatDistanceMeters(-1f))
        val result = computeSensorDisplay("SENSOR_RUNNING_DISTANCE",200,0,0,199.99f,0,0,true,false,false,false)
        assertEquals("199 m",result.value)
        assertEquals("목표 200 m",result.target)
        assertTrue(result.progress < 1f)
    }
    @Test fun cardNavigationHonorsTerminalAndSelectedStates() {
        assertEquals(CardHomeStep.DECK_PICK, journalCardDestination(false,null,null))
        listOf("READY","ACTIVE","PAUSED").forEach {
            assertEquals(CardHomeStep.REVEALED, journalCardDestination(false,it,"card"))
        }
        listOf("COMPLETED","SKIPPED").forEach {
            assertEquals(CardHomeStep.HOME, journalCardDestination(false,it,"card"))
        }
        assertEquals(CardHomeStep.HOME, journalCardDestination(true,"ACTIVE","card"))
    }
}
