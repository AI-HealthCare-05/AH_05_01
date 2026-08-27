package kr.tmtn.app.domain.ml

import kotlinx.coroutines.flow.Flow
import kr.tmtn.app.domain.model.MissionType

/**
 * 모델 2 — 기기 센서로 행동을 인식한다.
 *
 * 이 모델이 있는 미션(MODEL_ACTIVE_TIME / MODEL_DISTANCE / MODEL_STAIR_COUNT)은
 * 사용자가 "완료" 버튼을 누르지 않아도 목표에 닿으면 자동으로 완료된다.
 *
 * 그래서 이 미션 화면에는 일시정지 버튼이 필수가 아니다.
 * 멈춰 있으면 모델이 알아서 시간을 세지 않기 때문이다.
 * (사용자가 원하면 쓸 수 있게 "직접 일시정지" 는 남겨 둔다.)
 */
enum class SignalQuality(val label: String) {
    GOOD("좋음"), FAIR("보통"), WEAK("약함"),
}

/** 자동 측정을 쓸 수 없는 이유. 화면에서 그대로 목록으로 보여준다. */
enum class BlockReason(val label: String, val detail: String) {
    NO_ACTIVITY_PERMISSION("활동 권한", "허용 안 됨"),
    NO_MOTION_SENSOR("움직임 센서", "지원 안 함"),
    NO_BAROMETER("기압계", "지원 안 함"),
    NO_LOCATION_PERMISSION("위치 권한", "허용 안 됨"),
}

sealed interface RecognizerAvailability {
    data object Ready : RecognizerAvailability
    data class Blocked(val reasons: List<BlockReason>) : RecognizerAvailability
}

/** 측정 한 번의 요청. */
data class MeasureRequest(
    val missionType: MissionType,
    val targetSeconds: Int = 0,
    val targetMeters: Int = 0,
    val targetStairs: Int = 0,
)

/**
 * 1초에 한 번쯤 흘러나오는 측정 스냅샷.
 * 화면은 이 값만 그린다 — 모델이 무엇이든 화면 코드는 바뀌지 않는다.
 */
data class ActivitySnapshot(
    /** 모델이 "실제로 움직였다" 고 인정한 시간 */
    val activeSeconds: Int = 0,
    /** 시작 버튼을 누른 뒤 흐른 전체 시간 */
    val elapsedSeconds: Int = 0,
    /** 움직이지 않아 자동으로 세지 않은 시간 */
    val restSeconds: Int = 0,
    /** 신호가 나빠 계산에서 뺀 시간 */
    val excludedSeconds: Int = 0,
    val steps: Int = 0,
    val distanceMeters: Double = 0.0,
    val stairs: Int = 0,
    val quality: SignalQuality = SignalQuality.GOOD,
    /** 지금 이 순간 움직임이 인식되고 있는가 */
    val moving: Boolean = false,
) {
    /** 목표 대비 진행도 0.0~1.0 */
    fun progress(req: MeasureRequest): Float = when (req.missionType) {
        MissionType.MODEL_ACTIVE_TIME ->
            if (req.targetSeconds <= 0) 0f else activeSeconds.toFloat() / req.targetSeconds
        MissionType.MODEL_DISTANCE ->
            if (req.targetMeters <= 0) 0f else (distanceMeters / req.targetMeters).toFloat()
        MissionType.MODEL_STAIR_COUNT ->
            if (req.targetStairs <= 0) 0f else stairs.toFloat() / req.targetStairs
        else -> 0f
    }.coerceIn(0f, 1f)

    fun reachedGoal(req: MeasureRequest): Boolean = progress(req) >= 1f
}

interface ActivityRecognizer {
    val info: ModelInfo

    /** 이 기기에서 자동 측정을 쓸 수 있는지. Blocked 면 화면이 "직접 체크" 로 안내한다. */
    fun availability(): RecognizerAvailability

    /**
     * 측정 스트림. collect 를 멈추면 측정도 끝난다.
     * 일시정지는 화면에서 collect 를 멈췄다 다시 시작하는 방식으로 처리한다.
     */
    fun measure(request: MeasureRequest, resumeFrom: ActivitySnapshot = ActivitySnapshot()): Flow<ActivitySnapshot>
}
