package com.tmtn.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RawSensorLogDao {

    @Insert
    suspend fun insert(log: RawSensorLog): Long

    /** 특정 센서 종류의 로그만 최근 순으로 조회 (데이터셋 추출 시 사용) */
    @Query("SELECT * FROM raw_sensor_logs WHERE sensorType = :sensorType ORDER BY timestamp ASC")
    suspend fun getBySensorType(sensorType: String): List<RawSensorLog>

    /** 전체 원시 로그를 시간순으로 조회 (CSV 내보내기 등에 사용) */
    @Query("SELECT * FROM raw_sensor_logs ORDER BY timestamp ASC")
    suspend fun getAll(): List<RawSensorLog>

    /** 저장 공간 관리를 위해 오래된 로그를 정리할 때 사용 (선택적) */
    @Query("DELETE FROM raw_sensor_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}