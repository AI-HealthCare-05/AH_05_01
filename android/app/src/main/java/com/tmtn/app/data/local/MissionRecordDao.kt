package com.tmtn.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MissionRecordDao {

    @Insert
    suspend fun insert(record: MissionRecord): Long

    // 아직 서버로 전송 안 한 기록들만 조회 (동기화 대상)
    @Query("SELECT * FROM mission_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<MissionRecord>

    // 서버 전송 성공 후, 해당 기록을 "동기화됨"으로 표시
    @Update
    suspend fun update(record: MissionRecord)

    // 화면에 최근 기록 보여줄 때 사용
    @Query("SELECT * FROM mission_records ORDER BY recordedAt DESC LIMIT 50")
    suspend fun getRecent(): List<MissionRecord>
}