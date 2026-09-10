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
    // ⚠️ 2026-09-09 QA 반영: 측정 중 백그라운드 알림 문구("N계단 · 자동 측정 중")가 목표를
    // 넘겨서 표시되는 문제 - 알림을 그리는 코드(서비스)는 목표값 자체를 몰랐음. 시작할 때
    // 같이 채워서 알림에서도 캡을 씌울 수 있게 함.
    var targetValue: Int? = null

    fun clear() {
        challengeId = null
        execType = null
        targetValue = null
    }
}
