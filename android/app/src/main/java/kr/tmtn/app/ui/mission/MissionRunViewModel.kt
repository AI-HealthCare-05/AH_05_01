package kr.tmtn.app.ui.mission

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kr.tmtn.app.domain.ml.*
import kr.tmtn.app.domain.model.MissionCard
import kr.tmtn.app.domain.model.MissionType

enum class RunPhase { Idle, Running, Paused, Saving, Done }

/**
 * 미션 실행 상태.
 *
 * 자가 수행형(SELF_TIMER)과 모델 측정형(MODEL_*)이 **같은 상태 기계**를 쓰되,
 * 시간을 세는 주체가 다르다.
 *  - SELF_TIMER : 앱이 1초마다 무조건 센다. 멈추려면 사용자가 일시정지를 눌러야 한다.
 *  - MODEL_*    : 모델이 "움직였다" 고 한 시간만 센다. 멈춰 있으면 자동으로 안 센다.
 *                 그래서 일시정지 버튼이 없어도 된다.
 */
class MissionRunViewModel(
    private val card: MissionCard,
    /** 모델형 카드지만 센서를 못 써서 사용자가 직접 체크로 진행하는 경우 true */
    private val forceManual: Boolean = false,
) : ViewModel() {

    /** 실제로 이 화면이 따를 규칙. forceManual 이면 모델형이어도 자가 확인으로 다룬다. */
    val type: MissionType = if (forceManual) MissionType.SELF_CHECK else card.type

    var phase by mutableStateOf(RunPhase.Idle)
        private set

    /** SELF_TIMER 전용 — 사용자가 시작한 뒤 흐른 초 */
    var selfSeconds by mutableStateOf(0)
        private set

    /** MODEL_* 전용 — 모델이 돌려준 최신 스냅샷 */
    var snapshot by mutableStateOf(ActivitySnapshot())
        private set

    var availability by mutableStateOf<RecognizerAvailability>(RecognizerAvailability.Ready)
        private set

    val recognizerInfo: ModelInfo get() = ModelRegistry.activityRecognizer.info

    private var job: Job? = null

    private val request = MeasureRequest(
        missionType = card.type,
        targetSeconds = card.targetSeconds,
        targetMeters = card.targetMeters,
        targetStairs = card.targetStairs,
    )

    init {
        if (type.isModelMeasured) {
            availability = ModelRegistry.activityRecognizer.availability()
        }
    }

    fun refreshAvailability() {
        if (type.isModelMeasured) {
            availability = ModelRegistry.activityRecognizer.availability()
        }
    }

    /* -------------------------------------------------- 진행도 / 표시값 */

    val progress: Float
        get() = if (type == MissionType.SELF_TIMER) {
            if (card.targetSeconds <= 0) 0f else (selfSeconds.toFloat() / card.targetSeconds).coerceIn(0f, 1f)
        } else {
            snapshot.progress(request)
        }

    val reachedGoal: Boolean
        get() = if (type == MissionType.SELF_TIMER) {
            card.targetSeconds > 0 && selfSeconds >= card.targetSeconds
        } else {
            snapshot.reachedGoal(request)
        }

    /** 완료 기록에 남길 "실제로 채운 양" */
    fun achievedValue(): Int = when (type) {
        MissionType.SELF_TIMER -> if (card.mission.unit == "초") selfSeconds else selfSeconds / 60
        MissionType.MODEL_ACTIVE_TIME -> snapshot.activeSeconds / 60
        MissionType.MODEL_DISTANCE -> snapshot.distanceMeters.toInt()
        MissionType.MODEL_STAIR_COUNT -> snapshot.stairs
        MissionType.SELF_CHECK -> card.targetNumber
    }

    /* -------------------------------------------------------- 제어 */

    fun start() {
        if (phase == RunPhase.Running) return
        phase = RunPhase.Running
        job?.cancel()
        job = viewModelScope.launch {
            if (type.isModelMeasured) {
                ModelRegistry.activityRecognizer
                    .measure(request, resumeFrom = snapshot)
                    .collectLatest { s ->
                        snapshot = s
                        if (s.reachedGoal(request)) finishAutomatically()
                    }
            } else {
                while (true) {
                    delay(1000)
                    selfSeconds += 1
                    // SELF_TIMER 는 목표에 닿아도 자동 완료하지 않는다.
                    // 사용자가 완료 버튼을 눌러야 기록된다 (CSV 완료판정 그대로).
                }
            }
        }
    }

    /** 사용자가 직접 멈춘 경우. 모델형에서는 선택 기능이다. */
    fun pause() {
        job?.cancel()
        job = null
        phase = RunPhase.Paused
    }

    fun resume() = start()

    /** 목표에 닿아 모델이 스스로 끝낸 경우 */
    private fun finishAutomatically() {
        job?.cancel()
        job = null
        phase = RunPhase.Saving
        viewModelScope.launch {
            delay(900)          // "기록하는 중…" 을 잠깐 보여 준다
            phase = RunPhase.Done
        }
    }

    /** 사용자가 여기까지 하겠다고 한 경우 (목표 미달이어도 기록은 남는다) */
    fun finishByUser() {
        job?.cancel()
        job = null
        phase = RunPhase.Saving
        viewModelScope.launch {
            delay(600)
            phase = RunPhase.Done
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
