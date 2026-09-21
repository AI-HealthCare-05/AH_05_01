package com.tmtn.app.ui.record

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The stored timestamp stays intact; the record sheet displays a readable local time. */
internal fun recordCompletionTime(raw: String, zone: ZoneId = ZoneId.systemDefault()): String? {
    val formatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
    return runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone).format(formatter) }
        .recoverCatching { LocalDateTime.parse(raw).format(formatter) }.getOrNull()
}
