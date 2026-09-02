package com.tmtn.app.sensor

import android.content.Context
import android.util.Log
import com.tmtn.app.data.local.AppDatabase
import com.tmtn.app.data.local.MissionRecord
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.SensorMeasurementBatchRequest
import com.tmtn.app.network.model.SensorMeasurementItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 지금까지 로컬 Room DB에만 쌓이고 서버로 한 번도 안 보내지던 부분.
 * challengeId별로 묶어서 배치 전송하고, 성공한 것만 isSynced=true로 표시한다.
 */
object MissionSyncManager {

    private const val TAG = "MissionSyncManager"

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun syncUnsyncedRecords(context: Context) {
        val db = AppDatabase.getInstance(context)
        val unsynced = db.missionRecordDao().getUnsyncedRecords()
        if (unsynced.isEmpty()) return

        // 같은 challengeId끼리 묶어서, 챌린지 하나당 배치 요청 하나씩 보낸다
        val grouped = unsynced.groupBy { it.challengeId }

        for ((challengeId, records) in grouped) {
            val items = records.map { record -> record.toSensorMeasurementItem() }

            try {
                val response = ApiClient.missionApi.sendSensorMeasurements(
                    challengeId,
                    SensorMeasurementBatchRequest(items)
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(
                        TAG,
                        "챌린지 $challengeId 동기화 성공: 누적=${body?.challenge?.accumulated_count}, " +
                            "accepted=${body?.accepted_count}, rejected=${body?.rejected_count}"
                    )
                    markAsSynced(db, records)
                } else {
                    // 401(토큰 만료), 403(소유권 문제), 409(챌린지 상태 문제) 등은
                    // isSynced=false로 남겨두고 다음 주기에 재시도
                    Log.e(TAG, "챌린지 $challengeId 동기화 실패: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                // 네트워크 자체가 안 되는 경우(오프라인 등)도 재시도를 위해 isSynced 그대로 둠
                Log.e(TAG, "챌린지 $challengeId 동기화 중 예외 발생: ${e.message}")
            }
        }
    }

    private suspend fun markAsSynced(db: AppDatabase, records: List<MissionRecord>) {
        records.forEach { record ->
            db.missionRecordDao().update(record.copy(isSynced = true))
        }
    }

    private fun MissionRecord.toSensorMeasurementItem(): SensorMeasurementItem {
        return SensorMeasurementItem(
            measurement_type = measurementType,
            value = value,
            recorded_at = isoFormat.format(Date(recordedAt))
        )
    }
}
