package com.tmtn.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * version 2 -> 3. MissionRecord.dailyCardId(Long) -> challengeId(String)로
 * 컬럼 타입이 바뀌어서 버전을 또 올렸다.
 * fallbackToDestructiveMigration()이 있어서 별도 Migration 파일 없이
 * 기존 로컬 데이터를 지우고 새로 만든다 (개발 단계라 허용).
 */
@Database(entities = [MissionRecord::class, RawSensorLog::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun missionRecordDao(): MissionRecordDao
    abstract fun rawSensorLogDao(): RawSensorLogDao

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
