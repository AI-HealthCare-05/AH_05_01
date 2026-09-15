package com.tmtn.app.notification

import com.tmtn.app.network.model.NotificationSettingResponse
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderPreferencesTest {
    private val setting = NotificationSettingResponse("Asia/Seoul", listOf("07:00", "13:00", "21:00"), emptyList(), null, true, "2026-09-14")
    private fun at(time: String) = ZonedDateTime.parse("2026-09-14T${time}:00+09:00[Asia/Seoul]")

    @Test fun turnedOffAndMissingSettingsNeverNotify() {
        assertFalse(reminderAllowed(null, at("12:00")))
        assertFalse(reminderAllowed(setting.copy(enabled = false), at("12:00")))
    }
    @Test fun overnightQuietHoursIncludeStartAndExcludeEnd() {
        val quiet = setting.copy(quiet_hours = mapOf("start" to "22:00", "end" to "07:00"))
        assertFalse(reminderAllowed(quiet, at("22:00")))
        assertFalse(reminderAllowed(quiet, at("06:59")))
        assertTrue(reminderAllowed(quiet, at("07:00")))
        assertTrue(reminderAllowed(quiet, at("21:59")))
    }
    @Test fun weekdaysUseTheUsersTimezone() {
        val monday = setting.copy(weekdays = listOf("MON"))
        assertTrue(reminderAllowed(monday, ZonedDateTime.parse("2026-09-13T16:00:00Z")))
        assertFalse(reminderAllowed(monday, ZonedDateTime.parse("2026-09-14T16:00:00Z")))
    }
}
