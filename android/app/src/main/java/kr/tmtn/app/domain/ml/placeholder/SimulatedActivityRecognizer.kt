package kr.tmtn.app.domain.ml.placeholder

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kr.tmtn.app.domain.ml.*

/**
 * 센서가 없는 에뮬레이터에서도 화면을 끝까지 볼 수 있게 하는 가짜 측정기.
 * 실제 기기에서는 SensorActivityRecognizer 가 쓰인다 (ModelRegistry 가 고른다).
 *
 * 8초 움직이고 2초 쉬는 패턴을 반복한다.
 */
class SimulatedActivityRecognizer : ActivityRecognizer {

    override val info = ModelInfo(
        name = "simulated",
        version = "0.1.0",
        isPlaceholder = true,
        note = "이 기기에 움직임 센서가 없어 샘플 값으로 보여 주고 있어요.",
    )

    override fun availability(): RecognizerAvailability = RecognizerAvailability.Ready

    override fun measure(request: MeasureRequest, resumeFrom: ActivitySnapshot): Flow<ActivitySnapshot> = flow {
        var active = resumeFrom.activeSeconds
        var elapsed = resumeFrom.elapsedSeconds
        var steps = resumeFrom.steps
        while (true) {
            delay(1000)
            elapsed += 1
            val moving = (elapsed % 10) < 8
            if (moving) { active += 1; steps += 2 }
            emit(
                ActivitySnapshot(
                    activeSeconds = active,
                    elapsedSeconds = elapsed,
                    restSeconds = (elapsed - active).coerceAtLeast(0),
                    excludedSeconds = 0,
                    steps = steps,
                    distanceMeters = steps * 0.70,
                    stairs = active / 3,
                    quality = SignalQuality.FAIR,
                    moving = moving,
                ),
            )
        }
    }
}
