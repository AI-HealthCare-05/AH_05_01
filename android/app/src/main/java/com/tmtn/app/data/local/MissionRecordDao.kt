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

    // ─────────────────────────────────────────────────────────────────────
    // ⚠️ 2026-09-08 추가 (sensor-measurements/batch 401 무한 재시도 대응).
    // 아래 세 개는 전부 @Query 기반 벌크 연산이라 Room 스키마(엔티티)에는 영향이 없음
    // → DB 버전을 또 올릴 필요 없음(= 사용자 로컬 데이터가 날아가지 않음).
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 여러 건을 한 번에 "동기화됨"으로 표시. 예전엔 record 하나마다 @Update를 돌려서
     * 배치 하나에 수십 번씩 DB 쓰기가 일어났음.
     * SQLite 바인딩 변수 상한(999) 때문에 호출하는 쪽에서 chunk 해서 넘겨야 함.
     */
    @Query("UPDATE mission_records SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /** 이미 서버로 올라간 오래된 기록 정리(로컬 DB가 무한히 커지는 것 방지). */
    @Query("DELETE FROM mission_records WHERE isSynced = 1 AND recordedAt < :threshold")
    suspend fun deleteSyncedBefore(threshold: Long): Int

    /**
     * 너무 오래된 미전송 기록 폐기. 계정 삭제·챌린지 종료 등으로 서버가 영원히
     * 받아주지 않는 기록이 남아 계속 재시도되는 걸 막는 마지막 안전장치.
     */
    @Query("DELETE FROM mission_records WHERE isSynced = 0 AND recordedAt < :threshold")
    suspend fun deleteUnsyncedBefore(threshold: Long): Int
}
