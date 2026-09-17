package com.tmtn.app.sensor

import android.content.Context
import android.util.Log
import com.tmtn.app.data.local.AppDatabase
import com.tmtn.app.data.local.PendingExerciseAction
import com.tmtn.app.data.local.PendingExerciseActionDao
import com.tmtn.app.network.AccountKey
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.CompleteExerciseMissionSessionRequest
import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * ⚠️ 2026-09-17 추가(QA #3 후속 - PR #21 패키지 "저장 대기"/"cancel_pending").
 *
 * PendingExerciseAction(Room)에 남아있는 완료·그만두기 요청을 앱 시작 시 한 번 재시도한다.
 * MissionSyncManager(센서 측정치 배치 동기화)와 같은 분류 원칙을 그대로 따른다:
 *  - 서버가 성공이든 영구 거절(4xx)이든 "응답을 줬다" = 결론이 났다 → 대기열에서 뺀다.
 *    (완료가 이미 처리돼 있었으면 서버가 idempotency_key로 같은 결과를 그대로 돌려주고,
 *    이미 취소·완료된 세션에 취소를 다시 보내면 409로 응답 - 둘 다 "재전송할 필요가
 *    없어졌다"는 뜻이라 지운다. 이후 CardHomeState.loadExerciseMissionsToday()의 재조회가
 *    실제 화면 상태를 서버 진실과 맞춘다.)
 *  - 401은 재로그인이 필요하다는 뜻(TokenAuthenticator가 이미 갱신을 시도하고도
 *    실패한 상태) - 지우지 않고 다음 로그인 때 다시 시도한다.
 *  - 429/5xx/네트워크 예외는 일시적일 수 있으니 시도 횟수만 늘리고 남겨두되,
 *    MAX_ATTEMPTS·MAX_AGE_MS를 넘기면 포기한다("무한 재시도 금지").
 */
object PendingExerciseSyncManager {

    private const val TAG = "PendingExerciseSync"

    private val TERMINAL_CODES = setOf(400, 403, 404, 409, 410, 422)
    private const val MAX_ATTEMPTS = 8
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    suspend fun replayPending(context: Context) {
        val dao = AppDatabase.getInstance(context).pendingExerciseActionDao()
        val now = System.currentTimeMillis()
        runCatching { dao.deleteOlderThan(now - MAX_AGE_MS) }

        // ⚠️ 로그인 안 돼 있으면(access_token 없음) 아무것도 보내지 않는다 - 대기 요청은
        // Room에 그대로 남아 다음에 같은 계정으로 로그인했을 때 재시도된다.
        val accountKey = AccountKey.current() ?: return
        val pending = runCatching { dao.getForAccount(accountKey) }.getOrNull() ?: return
        if (pending.isEmpty()) return

        for (action in pending) {
            try {
                when (action.actionType) {
                    "complete" -> replayComplete(dao, action)
                    "cancel" -> replayCancel(dao, action)
                    else -> dao.deleteBySession(action.sessionId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "재시도 중 예외: sessionId=${action.sessionId} type=${action.actionType} error=${e.message}")
                giveUpOrKeep(dao, action)
            }
        }
    }

    private suspend fun replayComplete(dao: PendingExerciseActionDao, action: PendingExerciseAction) {
        val idempotencyKey = action.idempotencyKey
        if (idempotencyKey == null) {
            // 완료 요청인데 키가 없으면 안전하게 보낼 방법이 없다 - 애초에 잘못 저장된
            // 레코드이므로 버린다(다시 만들 수 없음).
            dao.deleteBySession(action.sessionId)
            return
        }
        val sessionId = runCatching { UUID.fromString(action.sessionId) }.getOrNull()
        if (sessionId == null) {
            dao.deleteBySession(action.sessionId)
            return
        }
        val response = ApiClient.exerciseMissionApi.completeSession(
            sessionId,
            CompleteExerciseMissionSessionRequest(
                idempotency_key = idempotencyKey,
                manual_check = action.manualCheck,
                accumulated_count = action.accumulatedCount,
                accumulated_duration_seconds = action.accumulatedDurationSeconds,
            ),
        )
        Log.i(TAG, "complete 재시도: sessionId=${action.sessionId} httpCode=${response.code()}")
        resolveOrRetry(dao, action, response.code(), response.isSuccessful)
    }

    private suspend fun replayCancel(dao: PendingExerciseActionDao, action: PendingExerciseAction) {
        val sessionId = runCatching { UUID.fromString(action.sessionId) }.getOrNull()
        if (sessionId == null) {
            dao.deleteBySession(action.sessionId)
            return
        }
        val response = ApiClient.exerciseMissionApi.cancelSession(sessionId)
        Log.i(TAG, "cancel 재시도: sessionId=${action.sessionId} httpCode=${response.code()}")
        resolveOrRetry(dao, action, response.code(), response.isSuccessful)
    }

    private suspend fun resolveOrRetry(dao: PendingExerciseActionDao, action: PendingExerciseAction, code: Int, isSuccessful: Boolean) {
        when {
            isSuccessful || code in TERMINAL_CODES -> dao.deleteBySession(action.sessionId)
            code == 401 -> Unit // 재로그인 필요 - 그대로 둠
            else -> giveUpOrKeep(dao, action) // 429/5xx
        }
    }

    private suspend fun giveUpOrKeep(dao: PendingExerciseActionDao, action: PendingExerciseAction) {
        if (action.attemptCount + 1 >= MAX_ATTEMPTS) {
            Log.w(TAG, "재시도 ${MAX_ATTEMPTS}회 초과 - 포기: sessionId=${action.sessionId} type=${action.actionType}")
            dao.deleteBySession(action.sessionId)
        } else {
            dao.incrementAttempt(action.sessionId)
        }
    }
}
