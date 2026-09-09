package com.tmtn.app.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 센서 측정값을 앱 전체에서 공유하는 저장소.
 *
 * MissionSensorService(백그라운드에서 실제로 센서를 읽는 쪽)가 이 값을 갱신하고,
 * MainActivity(화면)는 이 값을 구독만 해서 화면에 보여준다.
 * object로 선언했으므로 앱 전체에서 단 하나만 존재하는 싱글턴이다.
 */
object SensorDataHolder {

    // ── 걸음(카운트) — 항상 백그라운드에서 측정 ──
    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount

    // ── 실제 계단 오르기 — 항상 백그라운드에서 측정 ──
    private val _floorsClimbed = MutableStateFlow(0)
    val floorsClimbed: StateFlow<Int> = _floorsClimbed

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    // ⚠️ 2026-09-04 추가: 센서 측정 "일시정지" - 자가타이머(TIMER)형의 일시정지와 같은
    // 개념을 센서형에도 적용. true면 실제 센서 리스너를 꺼둔 상태라, 그동안은
    // 걸음/계단/거리/시간 등 어떤 값도 안 늘어남(측정도 "일시정지"됨).
    private val _isSensorPaused = MutableStateFlow(false)
    val isSensorPaused: StateFlow<Boolean> = _isSensorPaused

    // ── 제자리걸음(카운트) — 사용자가 시작/종료 ──
    private val _stepInPlaceCount = MutableStateFlow(0)
    val stepInPlaceCount: StateFlow<Int> = _stepInPlaceCount

    private val _isStepInPlaceActive = MutableStateFlow(false)
    val isStepInPlaceActive: StateFlow<Boolean> = _isStepInPlaceActive

    // ── 계단오르기(제자리) — 사용자가 시작/종료 ──
    private val _stairInPlaceFloors = MutableStateFlow(0)
    val stairInPlaceFloors: StateFlow<Int> = _stairInPlaceFloors

    private val _isStairInPlaceActive = MutableStateFlow(false)
    val isStairInPlaceActive: StateFlow<Boolean> = _isStairInPlaceActive

    // ── 달리기(거리) — 사용자가 시작/종료, GPS. 단위는 미터(m)로 관리 ──
    private val _runningDistanceM = MutableStateFlow(0f)
    val runningDistanceM: StateFlow<Float> = _runningDistanceM

    // ── 달리기(시간) — 사용자가 시작/종료, 케이던스 ──
    private val _runningSeconds = MutableStateFlow(0)
    val runningSeconds: StateFlow<Int> = _runningSeconds

    // 달리기 세션(거리든 시간이든) 진행 중 여부
    private val _isRunningActive = MutableStateFlow(false)
    val isRunningActive: StateFlow<Boolean> = _isRunningActive

    // ⚠️ 2026-09-07 QA 반영: isRunningActive/isWalkingActive는 "측정 세션이 켜져 있는지"만
    // 뜻했음(시작/이어하기 때 true, 일시정지/끝내기 때만 false) - 세션 중엔 실제로 멈춰
    // 서있어도 계속 true라서, 화면 문구("움직임을 확인했어요")가 세션 내내 고정으로 보이고
    // 몇 초 안 움직였는지도 알 수 없었음. Cadence 매니저가 이미 판정해 둔 "지금 이 순간
    // 진짜 케이던스가 나오는지"(isCurrentlyRunning/isCurrentlyWalking)를 따로 노출해서
    // 화면이 실시간 움직임 여부를 정확히 구분하게 함.
    private val _isRunningDetectedNow = MutableStateFlow(false)
    val isRunningDetectedNow: StateFlow<Boolean> = _isRunningDetectedNow

    // ── 걷기(시간) — 사용자가 시작/종료, 케이던스, 실외 전용 ──
    private val _walkingSeconds = MutableStateFlow(0)
    val walkingSeconds: StateFlow<Int> = _walkingSeconds

    private val _isWalkingActive = MutableStateFlow(false)
    val isWalkingActive: StateFlow<Boolean> = _isWalkingActive

    private val _isWalkingDetectedNow = MutableStateFlow(false)
    val isWalkingDetectedNow: StateFlow<Boolean> = _isWalkingDetectedNow

    // ⚠️ 2026-09-07 반영: 계단/걸음수형은 "지금 이 순간 움직임이 감지되고 있나"를 아예
    // 안 보고 항상 true로 고정돼 있어서, 가만히 있어도 "움직임을 확인했어요"가 계속
    // 떴음(QA - "가만히 있는데 계속 움직임 확인했어요"). Walking/Running과 같은 패턴 추가.
    private val _isFloorsClimbedDetectedNow = MutableStateFlow(false)
    val isFloorsClimbedDetectedNow: StateFlow<Boolean> = _isFloorsClimbedDetectedNow
    private val _isStepDetectedNow = MutableStateFlow(false)
    val isStepDetectedNow: StateFlow<Boolean> = _isStepDetectedNow

    fun updateStepCount(value: Int) { _stepCount.value = value }
    fun updateFloorsClimbed(value: Int) { _floorsClimbed.value = value }
    fun setServiceRunning(running: Boolean) { _isServiceRunning.value = running }

    fun updateStepInPlaceCount(value: Int) { _stepInPlaceCount.value = value }
    fun setStepInPlaceActive(active: Boolean) { _isStepInPlaceActive.value = active }

    fun updateStairInPlaceFloors(value: Int) { _stairInPlaceFloors.value = value }
    fun setStairInPlaceActive(active: Boolean) { _isStairInPlaceActive.value = active }

    /** 달린 거리를 미터(m) 단위로 갱신한다. */
    fun updateRunningDistanceM(value: Float) { _runningDistanceM.value = value }
    fun updateRunningSeconds(value: Int) { _runningSeconds.value = value }
    fun setRunningActive(active: Boolean) { _isRunningActive.value = active }

    fun updateWalkingSeconds(value: Int) { _walkingSeconds.value = value }
    fun setWalkingActive(active: Boolean) { _isWalkingActive.value = active }
    fun updateWalkingDetectedNow(value: Boolean) { _isWalkingDetectedNow.value = value }
    fun updateRunningDetectedNow(value: Boolean) { _isRunningDetectedNow.value = value }
    fun updateFloorsClimbedDetectedNow(value: Boolean) { _isFloorsClimbedDetectedNow.value = value }
    fun updateStepDetectedNow(value: Boolean) { _isStepDetectedNow.value = value }
    fun setSensorPaused(paused: Boolean) { _isSensorPaused.value = paused }

    /** 걸음/계단(항상 측정되는 값들)을 초기화. 서비스를 완전히 중지할 때 사용. */
    fun resetAll() {
        _stepCount.value = 0
        _floorsClimbed.value = 0
        _stepInPlaceCount.value = 0
        _stairInPlaceFloors.value = 0
        _runningDistanceM.value = 0f
        _runningSeconds.value = 0
        _walkingSeconds.value = 0
    }
}