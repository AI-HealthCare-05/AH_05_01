package com.tmtn.app.network.model

/** ⚠️ 2026-09-16 신규 - app/dtos/practice_score.py의 PracticeScoreResponse와 1:1로 맞춤.
 * composite_score/lifestyle_score는 초기 습관 점수가 아직 계산 전(policy_status='pending')
 * 이거나 건강 영역을 못 가져오면 null로 내려옴 - composite_blocked_reason에 이유가 담김
 * (예: "INITIAL_FORMULA_POLICY_PENDING", "HEALTH_COMPONENTS_UNAVAILABLE"). null이면
 * "아직 계산 전"으로 처리하고 0이나 이전 값으로 대체하지 않을 것. */
data class PracticeScoreResponse(
    val as_of: String,
    val daily_units: Double,
    val cumulative_units: Double,
    val practice_score: Double,
    val confirmed_rest_run: Int,
    val unknown_run: Int,
    val freshness: String,
    val policy_version: String,
    val lifestyle_score: Double?,
    val composite_score: Double?,
    val composite_blocked_reason: String?,
)
