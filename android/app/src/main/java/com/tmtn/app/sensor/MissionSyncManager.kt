package com.tmtn.app.sensor

import android.content.Context
import android.util.Log
import com.tmtn.app.data.local.AppDatabase
import com.tmtn.app.data.local.MissionRecord
import com.tmtn.app.data.local.MissionRecordDao
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.SensorMeasurementBatchRequest
import com.tmtn.app.network.model.SensorMeasurementItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 로컬 Room DB에 쌓인 측정 기록을 challengeId별로 묶어서 서버에 배치 전송한다.
 *
 * ⚠️ 2026-09-08 수정 (서버 로그에 sensor-measurements/batch 401이 몇 시간째 도배되던 문제).
 *
 * 예전 구현은 실패 코드를 전혀 구분하지 않고 "실패하면 isSynced=false로 두고 다음 주기에
 * 재시도"만 했음. 그런데 401(만료·삭제된 계정), 403(남의 챌린지), 404(없는 챌린지),
 * 409(이미 끝난 챌린지) 같은 건 몇 번을 다시 보내도 결과가 절대 안 바뀜.
 * 그 결과 한 번 쌓인 실패 기록이 영구 대기열이 돼서, 측정 서비스가 30초마다 돌 때마다
 * 챌린지 개수만큼 요청이 계속 나갔음(로그에 찍힌 게 그거임).
 *
 * 이제 세 가지로 나눠서 처리한다.
 *  - 성공(2xx)           → 동기화 완료 표시
 *  - 영구 실패(4xx 대부분) → 재시도해도 소용없으므로 "처리됨"으로 표시하고 대기열에서 뺀다
 *  - 일시 실패(401/429/5xx/네트워크 예외) → 남겨두고 재시도. 단 401은 쿨다운을 걸고,
 *                          5xx가 계속되면 일정 횟수 뒤 포기한다.
 *
 * 여기에 더해 오래된 기록은 주기적으로 폐기해서(RETENTION) 어떤 경우에도 대기열이
 * 무한히 남지 않게 했다.
 */
object MissionSyncManager {

    private const val TAG = "MissionSyncManager"

    /** 재시도해도 결과가 바뀌지 않는 응답들. 대기열에서 빼고 로그만 남긴다. */
    private val TERMINAL_CODES = setOf(400, 403, 404, 409, 410, 422)

    /** 401을 받으면 이 시간 동안은 아예 시도하지 않음(토큰 갱신 실패 = 재로그인 필요 상태). */
    private const val UNAUTHORIZED_COOLDOWN_MS = 5 * 60 * 1000L

    /** 같은 챌린지에서 서버 오류(5xx/429)가 이만큼 연속되면 그 기록은 포기한다. */
    private const val MAX_SERVER_ERROR_RETRIES = 10

    /** 전송 완료된 기록 보관 기간. */
    private const val SYNCED_RETENTION_MS = 3L * 24 * 60 * 60 * 1000

    /** 미전송 기록 최대 보관 기간. 이보다 오래된 건 서버가 영원히 안 받는다고 보고 버린다. */
    private const val UNSYNCED_RETENTION_MS = 3L * 24 * 60 * 60 * 1000

    /** SQLite 바인딩 변수 상한(999) 회피용. */
    private const val ID_CHUNK_SIZE = 500

    /** 한 요청에 실어 보내는 측정 항목 최대 개수. */
    private const val BATCH_ITEM_LIMIT = 500

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Volatile
    private var unauthorizedUntil = 0L

    /** challengeId -> 연속 서버 오류 횟수(프로세스 살아있는 동안만 유지). */
    private val serverErrorCounts = mutableMapOf<String, Int>()

    /**
     * 로그인·재로그인에 성공했을 때 호출하면 401 쿨다운이 즉시 풀린다.
     * (안 불러도 5분 뒤 자동으로 풀리므로 필수는 아님)
     */
    fun onSessionRestored() {
        unauthorizedUntil = 0L
        synchronized(serverErrorCounts) { serverErrorCounts.clear() }
    }

    suspend fun syncUnsyncedRecords(context: Context) {
        val db = AppDatabase.getInstance(context)
        val dao = db.missionRecordDao()

        purgeOldRecords(dao)

        val now = System.currentTimeMillis()
        if (now < unauthorizedUntil) {
            // 로그를 매번 남기면 그것대로 도배되므로 남기지 않는다.
            return
        }

        val unsynced = dao.getUnsyncedRecords()
        if (unsynced.isEmpty()) return

        // 같은 challengeId끼리 묶어서, 챌린지 하나당 배치 요청 하나씩 보낸다
        val grouped = unsynced.groupBy { it.challengeId }

        for ((challengeId, allRecords) in grouped) {
            // 아주 오래 오프라인이었던 경우를 대비해 한 번에 보내는 양을 제한한다.
            val records = allRecords.sortedBy { it.recordedAt }.take(BATCH_ITEM_LIMIT)
            val items = records.map { record -> record.toSensorMeasurementItem() }

            try {
                val response = ApiClient.missionApi.sendSensorMeasurements(
                    challengeId,
                    SensorMeasurementBatchRequest(items)
                )
                val code = response.code()

                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        Log.d(
                            TAG,
                            "챌린지 $challengeId 동기화 성공: 누적=${body?.challenge?.accumulated_count}, " +
                                "accepted=${body?.accepted_count}, rejected=${body?.rejected_count}"
                        )
                        // ⚠️ 2026-09-08 QA(계단 오탐 - 서버가 12칸 중 9칸만 앎) 임시 진단 로그
                        // 추가 - 지금까지 rejected_count(개수)만 찍고, 서버가 이미 응답에
                        // 담아주는 rejected_reasons(왜 거부됐는지)는 안 찍고 있었음.
                        // 원인 확정되면 지워도 됨.
                        if ((body?.rejected_count ?: 0) > 0) {
                            Log.w(TAG, "챌린지 $challengeId 거부 사유: ${body?.rejected_reasons}")
                            Log.w(
                                TAG,
                                "챌린지 $challengeId 이번에 보낸 기록(전부): " +
                                    records.joinToString { "${it.measurementType}=${it.value}@${it.recordedAt}" }
                            )
                        }
                        resetServerErrorCount(challengeId)
                        markAsSynced(dao, records)
                    }

                    code == 401 -> {
                        // 여기까지 401이 왔다는 건 TokenAuthenticator의 리프레시까지 실패했다는 뜻.
                        // 다른 챌린지도 똑같이 401일 게 뻔하므로 이번 주기는 통째로 중단하고
                        // 쿨다운을 건다. 기록은 남겨두고, 재로그인 후에 다시 올라간다.
                        unauthorizedUntil = System.currentTimeMillis() + UNAUTHORIZED_COOLDOWN_MS
                        Log.w(
                            TAG,
                            "인증 실패(401) - 동기화를 ${UNAUTHORIZED_COOLDOWN_MS / 1000}초 동안 중단함. " +
                                "재로그인 후 자동 재시도됨"
                        )
                        return
                    }

                    code in TERMINAL_CODES -> {
                        // 재시도해도 결과가 안 바뀌는 응답. 대기열에서 빼지 않으면 영구 도배가 됨.
                        Log.w(
                            TAG,
                            "챌린지 $challengeId 동기화 영구 실패(HTTP $code) - " +
                                "기록 ${records.size}건을 재시도 대상에서 제외함"
                        )
                        resetServerErrorCount(challengeId)
                        markAsSynced(dao, records)
                    }

                    else -> {
                        // 429 / 5xx - 서버 쪽 일시 문제로 보고 재시도하되, 무한정은 아님.
                        val attempts = incrementServerErrorCount(challengeId)
                        if (attempts >= MAX_SERVER_ERROR_RETRIES) {
                            Log.e(
                                TAG,
                                "챌린지 $challengeId 동기화 $attempts 회 연속 실패(HTTP $code) - 포기함"
                            )
                            resetServerErrorCount(challengeId)
                            markAsSynced(dao, records)
                        } else {
                            Log.e(TAG, "챌린지 $challengeId 동기화 실패: HTTP $code ($attempts/$MAX_SERVER_ERROR_RETRIES)")
                        }
                    }
                }
            } catch (e: Exception) {
                // 네트워크 자체가 안 되는 경우(오프라인 등)는 사용자 잘못이 아니므로
                // 횟수도 세지 않고 그대로 남겨서 다음에 재시도한다.
                Log.e(TAG, "챌린지 $challengeId 동기화 중 예외 발생: ${e.message}")
            }
        }
    }

    /**
     * 오래된 기록 정리. 전송 완료분은 보관 기간이 지나면 지우고, 미전송분도
     * UNSYNCED_RETENTION_MS 를 넘기면 폐기한다(= 어떤 이유로든 서버가 영원히
     * 받아주지 않는 기록이 대기열에 눌러앉지 않게 하는 최종 방어선).
     */
    private suspend fun purgeOldRecords(dao: MissionRecordDao) {
        val now = System.currentTimeMillis()
        try {
            val syncedRemoved = dao.deleteSyncedBefore(now - SYNCED_RETENTION_MS)
            val staleRemoved = dao.deleteUnsyncedBefore(now - UNSYNCED_RETENTION_MS)
            if (staleRemoved > 0) {
                Log.w(TAG, "오래된 미전송 기록 ${staleRemoved}건 폐기(보관 기간 초과)")
            }
            if (syncedRemoved > 0) {
                Log.d(TAG, "전송 완료된 오래된 기록 ${syncedRemoved}건 정리")
            }
        } catch (e: Exception) {
            Log.e(TAG, "오래된 기록 정리 실패: ${e.message}")
        }
    }

    private fun incrementServerErrorCount(challengeId: String): Int = synchronized(serverErrorCounts) {
        val next = (serverErrorCounts[challengeId] ?: 0) + 1
        serverErrorCounts[challengeId] = next
        next
    }

    private fun resetServerErrorCount(challengeId: String) = synchronized(serverErrorCounts) {
        serverErrorCounts.remove(challengeId)
    }

    private suspend fun markAsSynced(
        dao: MissionRecordDao,
        records: List<MissionRecord>
    ) {
        records.map { it.id }.chunked(ID_CHUNK_SIZE).forEach { ids ->
            dao.markSynced(ids)
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
