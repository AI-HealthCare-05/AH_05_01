package com.tmtn.app.sensor

/**
 * "지금 어떤 챌린지를 추적하고 있는지"를 앱 전체에서 공유하는 임시 저장소.
 * TokenHolder와 같은 패턴 — 로그인 화면 없이 토큰을 들고 있던 것처럼,
 * 카드 선택 화면 없이 challengeId를 들고 있는 용도.
 *
 * ⚠️ 실제 카드 선택 화면이 생기면, 그 화면에서 /daily-cards/today →
 * /select 호출 성공 시 이 값을 채우도록 교체할 것.
 */
object CurrentChallengeHolder {
    var challengeId: String? = null
    var execType: String? = null  // "SENSOR_STEPS" / "SENSOR_FLOORS_CLIMBED" / ... 6종류 중 하나

    fun clear() {
        challengeId = null
        execType = null
    }
}
