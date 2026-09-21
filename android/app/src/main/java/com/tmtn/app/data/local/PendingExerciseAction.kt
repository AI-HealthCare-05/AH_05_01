package com.tmtn.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ⚠️ 2026-09-17 추가(QA #3 후속 - PR #21 패키지 "저장 대기"/"cancel_pending").
 *
 * 틈새 운동 완료(complete)·그만두기(cancel) 요청을 서버에 보내는 도중 통신이 끊기거나
 * 앱 프로세스가 죽으면, exerciseScope(메모리에만 존재)도 같이 사라져서 그 요청이
 * 영영 사라진 것처럼 보였음(사용자는 운동을 끝냈는데 기록이 안 남거나, 그만뒀는데
 * 서버엔 계속 진행 중으로 남는 문제).
 *
 * sessionId를 기본 키로 써서 세션 하나당 대기 요청 하나만 남게 하고(재시도 시 upsert),
 * accountKey(로그인 계정 상관관계 키 - AccountKey.kt)로 "다른 계정에 요청 전송 없음"을
 * 보장한다. 서버가 어떤 응답이든 내려주면(성공이든 영구 거절이든) 그 순간 지워지고,
 * 네트워크 예외처럼 결과를 모르는 경우에만 다음 앱 실행 때 같은 idempotency_key로
 * 재시도된다(PendingExerciseSyncManager).
 */
@Entity(tableName = "pending_exercise_actions")
data class PendingExerciseAction(
    @PrimaryKey val sessionId: String,
    val actionType: String, // "complete" / "cancel"
    val accountKey: String, // 요청을 만든 시점의 로그인 계정(JWT user_id) - 다른 계정 재전송 방지
    val idempotencyKey: String?, // complete만 사용. cancel은 서버가 상태 전이 자체로 멱등.
    val accumulatedCount: Int?,
    val accumulatedDurationSeconds: Int?,
    val manualCheck: Boolean = false,
    val createdAt: Long,
    val attemptCount: Int = 0,
)
