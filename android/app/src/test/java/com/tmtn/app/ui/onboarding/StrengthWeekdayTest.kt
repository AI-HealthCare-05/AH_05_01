package com.tmtn.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthWeekdayTest {
    @Test fun allDaySubsetsUseExistingApiBuckets() {
        for (mask in 0..127) {
            val days = (0..6).filter { mask and (1 shl it) != 0 }.toSet()
            assertEquals(minOf(Integer.bitCount(mask), 5), strengthCountForDays(days))
        }
    }
    @Test fun repeatedDayIsStillOneDay() {
        assertEquals(1, strengthCountForDays(listOf(0, 0, 0).toSet()))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidWeekdayCannotBecomeAnApiCount() {
        strengthCountForDays(setOf(7))
    }
}
