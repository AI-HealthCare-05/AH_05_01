package com.tmtn.app.ui.record

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RecordTimeTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    @Test fun fractionalOffsetTimestampBecomesReadableTime() {
        assertEquals("오전 10:55", recordCompletionTime("2026-09-10T10:55:08.298525+09:00", seoul))
    }
    @Test fun utcTimeUsesTheDevicesLocalZone() {
        assertEquals("오후 8:05", recordCompletionTime("2026-09-10T11:05:00Z", seoul))
    }
    @Test fun legacyLocalTimeAndInvalidDataDoNotLeakServerStrings() {
        assertEquals("오전 9:30", recordCompletionTime("2026-09-10T09:30:00", seoul))
        assertNull(recordCompletionTime("unknown", seoul))
    }
}
