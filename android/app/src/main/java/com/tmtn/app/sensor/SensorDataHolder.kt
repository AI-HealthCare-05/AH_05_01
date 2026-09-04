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

    // ── 걷기(시간) — 사용자가 시작/종료, 케이던스, 실외 전용 ──
    private val _walkingSeconds = MutableStateFlow(0)
    val walkingSeconds: StateFlow<Int> = _walkingSeconds

    private val _isWalkingActive = MutableStateFlow(false)
    val isWalkingActive: StateFlow<Boolean> = _isWalkingActive

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