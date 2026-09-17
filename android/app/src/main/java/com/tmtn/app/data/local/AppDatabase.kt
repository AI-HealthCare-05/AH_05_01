package com.tmtn.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * version 2 -> 3. MissionRecord.dailyCardId(Long) -> challengeId(String)로
 * 컬럼 타입이 바뀌어서 버전을 또 올렸다.
 * version 3 -> 4 (2026-09-17, QA #3 후속). PendingExerciseAction 테이블 추가 -
 * 틈새 운동 완료·그만두기 요청을 프로세스 종료에도 안전하게 재시도하기 위함.
 * fallbackToDestructiveMigration()이 있어서 별도 Migration 파일 없이
 * 기존 로컬 데이터를 지우고 새로 만든다 (개발 단계라 허용).
 */
@Database(entities = [MissionRecord::class, RawSensorLog::class, PendingExerciseAction::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun missionRecordDao(): MissionRecordDao
    abstract fun rawSensorLogDao(): RawSensorLogDao
    abstract fun pendingExerciseActionDao(): PendingExerciseActionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tmtn_local_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
