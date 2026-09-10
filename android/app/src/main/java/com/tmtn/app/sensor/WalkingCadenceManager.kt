package com.tmtn.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * 걷기(시간) 미션 전용 매니저. "실외에서 걷는 시간"을 측정하는 용도.
 *
 * ⚠️ 2026-09-07 반영(신장×연령 이중 보정): 신장 구간표는 실측 연구(Rowe 2011, 3 MET
 * 케이던스 152~198cm에서 90~113spm)를 참고했지만, 그건 "중강도(활발한) 걷기" 기준이라
 * "천천히 걷기"류 미션엔 너무 높았음(실측 테스트에서 90~110spm을 오가며 자주 못 넘김).
 * 그래서 기준값 자체를 한 단계 낮췄고(중강도 → 가벼운 활동 수준), 여기에 연령대별 보정
 * 비율을 곱함(나이 들수록 자연스러운 케이던스가 낮아진다는 NIH peak-30분-케이던스 경향을
 * 참고한 근사치 - 이 비율 자체는 정밀한 연구 수치가 아니라 추정값임, 추후 QA로 조정 필요).
 *
 * 신장 기준값(가벼운 활동 수준으로 낮춤):
 * 150~160cm 미만: 90 spm
 * 160~170cm 미만: 85 spm
 * 170~180cm 미만: 80 spm
 * 180cm 이상    : 75 spm
 *
 * 연령 보정(기준값에 곱함):
 * 40세 미만    : ×1.0
 * 40~59세      : ×0.9
 * 60~74세      : ×0.8
 * 75세 이상    : ×0.65
 * (나이 정보 없음/0이면 보정 없이 ×1.0)
 */
class WalkingCadenceManager(
    context: Context,
    heightCm: Float = 170f,
    ageYears: Int = 0
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val recentStepTimestamps = ArrayDeque<Long>()
    private val cadenceWindowMs = 5000L

    private val heightBaseThreshold: Int = when {
        heightCm < 160f -> 90
        heightCm < 170f -> 85
        heightCm < 180f -> 80
        else -> 75
    }
    private val ageAdjustFactor: Double = when {
        ageYears <= 0 -> 1.0 // 나이 정보 없음 - 보정 안 함
        ageYears < 40 -> 1.0
        ageYears < 60 -> 0.9
        ageYears < 75 -> 0.8
        else -> 0.65
    }
    private val walkingCadenceThreshold: Int = (heightBaseThreshold * ageAdjustFactor).toInt()

    private var lastStepDetectedAt: Long = 0L
    private val walkingTimeoutMs = 3000L

    var isCurrentlyWalking = false
        private set

    var accumulatedWalkingSeconds = 0
        private set

    private var walkingStartedAt: Long? = null

    fun isAvailable(): Boolean = stepDetector != null

    fun start() {
        // ⚠️ 2026-09-07 QA(걷기 미감지) 임시 진단 로그 - 이 기기에 걸음 감지 센서가
        // 실제로 있는지, threshold가 얼마로 계산됐는지 확인용.
        android.util.Log.w(
            "WalkingCadence",
            "start() called: sensorAvailable=${stepDetector != null} threshold=$walkingCadenceThreshold" +
                " (height=$heightBaseThreshold x ageFactor=$ageAdjustFactor)"
        )
        stepDetector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    // ⚠️ 2026-09-07 추가: stop()은 센서 리스너만 해제할 뿐 isCurrentlyWalking/
    // walkingStartedAt은 그대로 남아있어서, "일시정지" 직후 화면이 checkTimeout()의
    // 뒤늦은 판정(최대 walkingTimeoutMs)까지 기다렸다가 갑자기 값이 확 뛰는 것처럼
    // 보였음. 일시정지 시점에 즉시 정산해서 그 "점프"를 없앰.
    fun settleOngoing() {
        if (!isCurrentlyWalking) return
        walkingStartedAt?.let { started ->
            accumulatedWalkingSeconds += ((System.currentTimeMillis() - started) / 1000).toInt()
        }
        isCurrentlyWalking = false
        walkingStartedAt = null
    }

    fun reset() {
        recentStepTimestamps.clear()
        isCurrentlyWalking = false
        accumulatedWalkingSeconds = 0
        walkingStartedAt = null
        lastStepDetectedAt = 0L
    }

    // ⚠️ 2026-09-06 추가: reset()과 달리 0부터가 아니라 서버가 계산해서 준 실제 경과
    // 시간(elapsed_seconds)부터 이어서 셈. accumulatedWalkingSeconds는 "지금 걷고 있지
    // 않을 때의 값"이라 여기에 baseline을 넣고, isCurrentlyWalking/walkingStartedAt은
    // 초기화해서 다음 걸음 감지부터 새로 판정되게 함.
    fun resumeFrom(baselineSeconds: Int) {
        recentStepTimestamps.clear()
        isCurrentlyWalking = false
        accumulatedWalkingSeconds = baselineSeconds
        walkingStartedAt = null
        lastStepDetectedAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return

        val now = System.currentTimeMillis()
        lastStepDetectedAt = now
        recentStepTimestamps.addLast(now)

        while (recentStepTimestamps.isNotEmpty() && now - recentStepTimestamps.first() > cadenceWindowMs) {
            recentStepTimestamps.removeFirst()
        }

        evaluateWalkingState(now)
    }

    fun checkTimeout() {
        if (!isCurrentlyWalking) return

        val now = System.currentTimeMillis()
        if (now - lastStepDetectedAt > walkingTimeoutMs) {
            walkingStartedAt?.let { started ->
                accumulatedWalkingSeconds += ((lastStepDetectedAt - started) / 1000).toInt()
            }
            isCurrentlyWalking = false
            walkingStartedAt = null
        }
    }

    private fun evaluateWalkingState(now: Long) {
        val windowSeconds = cadenceWindowMs / 1000.0
        val currentCadence = (recentStepTimestamps.size / windowSeconds) * 60

        val walkingNow = currentCadence >= walkingCadenceThreshold

        // ⚠️ 2026-09-07 QA(걷기 미감지) 임시 진단 로그 - 실제 걸음 이벤트가 들어오고 있는지,
        // 케이던스가 몇 spm으로 계산되는지, 임계값을 넘는지 확인용. 원인 확정되면 지워도 됨.
        android.util.Log.w(
            "WalkingCadence",
            "step event: recentSteps=${recentStepTimestamps.size} cadence=${"%.1f".format(currentCadence)}" +
                " threshold=$walkingCadenceThreshold walkingNow=$walkingNow"
        )

        if (walkingNow && !isCurrentlyWalking) {
            isCurrentlyWalking = true
            walkingStartedAt = now
        } else if (!walkingNow && isCurrentlyWalking) {
            walkingStartedAt?.let { started ->
                accumulatedWalkingSeconds += ((now - started) / 1000).toInt()
            }
            isCurrentlyWalking = false
            walkingStartedAt = null
        }
    }

    fun getCurrentTotalSeconds(): Int {
        val ongoing = if (isCurrentlyWalking && walkingStartedAt != null) {
            ((System.currentTimeMillis() - walkingStartedAt!!) / 1000).toInt()
        } else 0
        return accumulatedWalkingSeconds + ongoing
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 사용 안 함
    }
}