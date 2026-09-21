package com.tmtn.app.sensor

/**
 * "지금 어떤 틈새 운동(ExerciseMission) 세션을 추적하고 있는지"를 앱 전체에서 공유하는
 * 임시 저장소. CurrentChallengeHolder와 같은 패턴이지만 오늘의 카드 Challenge 전용인
 * CurrentChallengeHolder를 그대로 쓸 수 없어서 따로 둔다("오늘의 카드 완료 후에만 틈새
 * 운동 시작 가능"이라 두 값이 동시에 채워질 일은 없지만, 의미가 다른 값을 같은 필드에
 * 겹쳐 쓰지 않기 위함).
 *
 * ⚠️ 2026-09-17 신규(QA 리뷰 #3) - 서비스의 주기 저장(saveToLocalDbAndSync)이 지금까지
 * CurrentChallengeHolder.challengeId 기준이라, 틈새 운동 시작 액션(ACTION_START_STEP_IN_PLACE
 * 등, 오늘의 카드와 공용)에는 이 값이 전혀 안 채워져서 틈새 운동 중 누적값이 서버에
 * 한 번도 동기화되지 않고 있었음(그래서 화면을 나갔다 돌아오면 이어하기가 0부터 다시
 * 시작함). ui/cardhome/ExerciseMissionScreens.kt가 측정 시작 직전에 이 값을 채우고,
 * MissionSensorService가 이 값이 있으면 주기적으로 PATCH .../sync를 보낸다. 화면이
 * 사라져도(탭 이동) 이 값은 그대로 유지돼야 백그라운드에서 계속 동기화된다 - 서비스가
 * 명시적 STOP 액션을 받을 때만 clear()한다.
 */
object CurrentExerciseSessionHolder {
    var sessionId: String? = null
    var execType: String? = null

    fun clear() {
        sessionId = null
        execType = null
    }
}
