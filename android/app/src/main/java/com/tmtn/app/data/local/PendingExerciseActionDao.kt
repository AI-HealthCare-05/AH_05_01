package com.tmtn.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingExerciseActionDao {

    // sessionId가 기본 키라 같은 세션에 다시 저장하면 덮어씀(upsert).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(action: PendingExerciseAction)

    // ⚠️ 로그인된 계정(accountKey)이 만든 대기 요청만 돌려줌 - 다른 계정으로 전환된
    // 상태에서 재시도가 호출돼도 그 계정 소유가 아닌 대기 요청은 절대 안 나옴.
    @Query("SELECT * FROM pending_exercise_actions WHERE accountKey = :accountKey")
    suspend fun getForAccount(accountKey: String): List<PendingExerciseAction>

    @Query("DELETE FROM pending_exercise_actions WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("UPDATE pending_exercise_actions SET attemptCount = attemptCount + 1 WHERE sessionId = :sessionId")
    suspend fun incrementAttempt(sessionId: String)

    // 무한 재시도 금지 최종 방어선 - MissionRecordDao의 deleteUnsyncedBefore와 같은 패턴.
    @Query("DELETE FROM pending_exercise_actions WHERE createdAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long): Int
}
