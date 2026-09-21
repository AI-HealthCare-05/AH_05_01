package com.tmtn.app.ui.cardhome

internal enum class SensorJourneyPhase(val label: String) {
    PREPARING("측정을 준비하고 있어요"),
    SIGNAL("GPS 신호를 찾고 있어요"),
    MOVING("움직임 인식 중"),
    WAITING("움직임을 기다리고 있어요"),
    PAUSED("일시정지됨"),
    COMPLETE("목표를 채웠어요"),
    ERROR("측정을 확인해 주세요"),
}

/** 세션 실행 여부와 실제 움직임 인식을 구분한다. 완료 후 자동 정지는 완료로 표시한다. */
internal fun sensorJourneyPhase(
    progress: Float, detected: Boolean, paused: Boolean, serviceReady: Boolean,
    serviceError: String? = null, waitingForGps: Boolean = false,
): SensorJourneyPhase = when {
    serviceError != null -> SensorJourneyPhase.ERROR
    progress.isFinite() && progress >= 1f -> SensorJourneyPhase.COMPLETE
    paused -> SensorJourneyPhase.PAUSED
    !serviceReady -> SensorJourneyPhase.PREPARING
    waitingForGps -> SensorJourneyPhase.SIGNAL
    detected -> SensorJourneyPhase.MOVING
    else -> SensorJourneyPhase.WAITING
}

internal fun sensorJourneyMessage(phase: SensorJourneyPhase, progress: Float): String = when (phase) {
    SensorJourneyPhase.PREPARING -> "준비되면 같이 출발하자."
    SensorJourneyPhase.SIGNAL -> "신호가 잡히면 같이 출발하자."
    SensorJourneyPhase.PAUSED -> "잠깐 쉬자. 여기서 기다릴게."
    SensorJourneyPhase.WAITING -> "괜찮아. 네 속도에 맞출게."
    SensorJourneyPhase.ERROR -> "여기서 잠깐 기다릴게."
    SensorJourneyPhase.COMPLETE -> "해냈다! 댐에 보탤 재료가 생겼어."
    SensorJourneyPhase.MOVING -> when {
        progress >= .75f -> "이제 마지막 구간이야!"
        progress >= .5f -> "벌써 절반이야. 잘 가고 있어!"
        progress >= .25f -> "한 걸음씩, 제법 멀리 왔네."
        else -> "좋아, 네 페이스대로 해 보자!"
    }
}
