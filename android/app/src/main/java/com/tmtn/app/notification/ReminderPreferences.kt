package com.tmtn.app.notification

import com.tmtn.app.network.model.NotificationSettingResponse
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Honors the existing server settings at delivery time, including a cross-midnight quiet period. */
internal fun reminderAllowed(setting: NotificationSettingResponse?, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
    if (setting == null || !setting.enabled) return false
    val localNow = now.withZoneSameInstant(runCatching { ZoneId.of(setting.timezone) }.getOrDefault(ZoneId.of("Asia/Seoul")))
    val weekday = localNow.dayOfWeek.name.take(3)
    if (setting.weekdays.isNotEmpty() && setting.weekdays.none { it.uppercase(Locale.ROOT).take(3) == weekday }) return false
    val quiet = setting.quiet_hours ?: return true
    val start = runCatching { LocalTime.parse(quiet["start"]) }.getOrNull() ?: return false
    val end = runCatching { LocalTime.parse(quiet["end"]) }.getOrNull() ?: return false
    val time = localNow.toLocalTime()
    val quietNow = if (start == end) false else if (start < end) time >= start && time < end else time >= start || time < end
    return !quietNow
}
