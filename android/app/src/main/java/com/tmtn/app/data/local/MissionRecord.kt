package com.tmtn.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 로컬에 저장되는 미션 측정 기록 하나.
 * 서버 전송 성공 전까지는 이 테이블에 남아있고, 성공하면 isSynced를 true로 바꾼다.
 *
 * v2 변경사항: dailyCardId(Long) → challengeId(String, UUID).
 * 서버 PK가 UUID로 바뀌면서 타입도 같이 바뀜.
 */
@Entity(tableName = "mission_records")
data class MissionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val challengeId: String,        // 이전: dailyCardId: Long
    val measurementType: String,    // "STEP" / "STAIR" / "STEP_IN_PLACE" / "RUN_DISTANCE_M" / "RUN_DURATION" / "WALK_DURATION"
    val value: Int,                 // 걸음 수, 오른 칸 수, 미터, 초 등 (전부 "그 순간까지의 누적값" 스냅샷)
    val recordedAt: Long,           // 측정된 시각 (epoch millis)
    val isSynced: Boolean = false   // 서버에 전송 완료했는지 여부
)
