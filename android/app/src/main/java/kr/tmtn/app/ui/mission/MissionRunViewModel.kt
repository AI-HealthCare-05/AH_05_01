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
import kr.tmtn.app.data.TmtnStore
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
    /** 타이머를 기기에 적어 두는 곳. null 이면 화면을 벗어날 때 사라진다 */
    private val store: TmtnStore? = null,
    /** 타이머 저장 키에 쓸 오늘 날짜 (yyyy-MM-dd) */
    private val dateKey: String = "",
) : ViewModel() {

    /** 실제로 이 화면이 따를 규칙. forceManual 이면 모델형이어도 자가 확인으로 다룬다. */
    val type: MissionType = if (forceManual) MissionType.SELF_CHECK else card.type

    var phase by mutableStateOf(RunPhase.Idle)
        private set

    /**
     * SELF_TIMER 전용 — 사용자가 시작한 뒤 흐른 초.
     *
     * 1초마다 더하지 않는다. **시작 시각과 지금의 차이**를 그때그때 계산한다.
     * 그래야 화면을 벗어나 있는 동안에도, 폰이 잠들어 있는 동안에도 시간이 흐른다.
     * 아래 [tick] 은 화면을 다시 그리게 하는 신호일 뿐 시간을 세지 않는다.
     */
    val selfSeconds: Int
        get() {
            tick                      // 1초마다 이 값을 다시 읽게 만드는 장치
            val running = if (timer.isRunning) {
                ((now() - timer.startedAt) / 1000L).coerceAtLeast(0L).toInt()
            } else {
                0
            }
            return timer.accumulated + running
        }

    /** 초를 세는 게 아니라 화면을 다시 그리게 하는 신호 */
    private var tick by mutableStateOf(0)

    private var timer = TmtnStore.TimerState(0L, 0)

    /** `<날짜>_<미션ID>`. 미션을 바꾸면 키가 달라져 남의 시간을 이어받지 않는다. */
    private val timerKey: String get() = "${dateKey}_${card.mission.id}"

    /**
     * 벽시계를 쓴다. `elapsedRealtime()` 은 재부팅하면 0 으로 돌아가서
     * 20분짜리 미션 도중 폰을 껐다 켜면 시간이 튄다.
     * 사용자가 시계를 직접 돌리는 경우는 스스로를 속이는 것이라 막지 않는다.
     */
    private fun now(): Long = System.currentTimeMillis()

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
        // 나갔다 돌아온 경우. 적어 둔 시작 시각을 되살려 이어서 센다.
        if (type == MissionType.SELF_TIMER && store != null && dateKey.isNotBlank()) {
            timer = store.timerStateOf(timerKey)
            if (timer.isRunning) startTicking()
            phase = when {
                timer.isRunning -> RunPhase.Running
                timer.accumulated > 0 -> RunPhase.Paused
                else -> RunPhase.Idle
            }
        }
    }

    /** 1초마다 화면만 다시 그린다. 이 코루틴이 죽어도 시간은 그대로 흐른다. */
    private fun startTicking() {
        job?.cancel()
        job = viewModelScope.launch {
            while (true) {
                delay(1000)
                tick++
            }
        }
    }

    private fun persist() {
        if (store != null && dateKey.isNotBlank()) store.saveTimerState(timerKey, timer)
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
        if (type.isModelMeasured) {
            job = viewModelScope.launch {
                ModelRegistry.activityRecognizer
                    .measure(request, resumeFrom = snapshot)
                    .collectLatest { s ->
                        snapshot = s
                        if (s.reachedGoal(request)) finishAutomatically()
                    }
            }
        } else {
            // 시작 시각만 적어 둔다. 화면을 벗어나도 이 값은 남아 시간이 계속 흐른다.
            // SELF_TIMER 는 목표에 닿아도 자동 완료하지 않는다 —
            // 사용자가 완료 버튼을 눌러야 기록된다 (CSV 완료판정 그대로).
            timer = timer.copy(startedAt = now())
            persist()
            startTicking()
        }
    }

    /** 사용자가 직접 멈춘 경우. 모델형에서는 선택 기능이다. */
    fun pause() {
        // 멈출 때 지금까지 흐른 만큼을 누적으로 옮긴다. 시작 시각은 지운다.
        if (timer.isRunning) {
            timer = TmtnStore.TimerState(startedAt = 0L, accumulated = selfSeconds)
            persist()
        }
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
        // 끝낸 값을 먼저 굳혀 둔다. 지우기 전에 해야 achievedValue() 가 맞는다.
        if (timer.isRunning) timer = TmtnStore.TimerState(0L, selfSeconds)
        job?.cancel()
        job = null
        phase = RunPhase.Saving
        viewModelScope.launch {
            delay(600)
            phase = RunPhase.Done
        }
    }

    /**
     * 이 미션을 접었을 때. 적어 둔 시간을 지운다.
     * 남겨 두면 내일 같은 카드를 뽑았을 때 어제 시간이 이어져 보인다.
     */
    fun discardTimer() {
        timer = TmtnStore.TimerState(0L, 0)
        if (store != null && dateKey.isNotBlank()) store.clearTimer(timerKey)
        job?.cancel()
        job = null
        phase = RunPhase.Idle
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
