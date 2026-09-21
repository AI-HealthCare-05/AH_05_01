package com.tmtn.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.tmtn.app.data.local.AppDatabase
import com.tmtn.app.data.local.RawSensorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 걸음 수를 세는 클래스.
 *
 * "걸음(카운트)" 미션과 "제자리걸음(카운트)" 미션 둘 다 이 클래스를 그대로 쓴다.
 * missionContext 값으로 두 미션을 구분해서 원시 데이터를 기록한다.
 */
class StepCounterManager(
    private val context: Context,
    private val missionContext: String = "STEP"  // "STEP" 또는 "STEP_IN_PLACE"로 구분해서 생성
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val db by lazy { AppDatabase.getInstance(context) }
    private val loggingScope = CoroutineScope(Dispatchers.IO)

    private var baselineCount: Int? = null
    // ⚠️ 2026-09-06 추가: 뒤로가기 등으로 화면을 벗어났다가 "진행 중인 미션 확인"으로
    // 돌아올 때, 서버가 갖고 있던 마지막 누적치(accumulated_count)부터 이어서 세게 함.
    // 예전엔 재개할 때도 그냥 0부터 다시 셌음(reset과 다를 게 없었음) - 이게 "5초로
    // 되돌아간 것처럼 보였던" 버그의 실제 원인.
    private var resumeOffset = 0

    // ⚠️ 2026-09-07 반영: "지금 이 순간 움직이고 있나"를 화면에 정확히 보여주기 위한
    // 최근 걸음 감지 시각(StairClimbManager와 같은 패턴).
    private var lastStepDetectedAt: Long = 0L
    private val recentlyActiveWindowMs = 3000L

    fun isRecentlyActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        lastStepDetectedAt != 0L && nowMs - lastStepDetectedAt <= recentlyActiveWindowMs

    var stepCount = 0
        private set

    fun isAvailable(): Boolean = stepCounterSensor != null

    fun start() {
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun reset() {
        baselineCount = null
        stepCount = 0
        resumeOffset = 0
        lastStepDetectedAt = 0L
    }

    // ⚠️ 2026-09-06 추가: reset()과 달리 0부터가 아니라 서버가 준 baseline부터 이어서 셈.
    // 센서 자체는 "재부팅 이후 누적값"만 주므로, 다음 이벤트에서 새 기준점을 다시 잡되
    // (baselineCount=null) 그 위에 baseline을 더해서 실제 걸음수로 환산.
    fun resumeFrom(baseline: Int) {
        baselineCount = null
        stepCount = baseline
        resumeOffset = baseline
        lastStepDetectedAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSinceBoot = event.values[0].toInt()
            val now = System.currentTimeMillis()
            lastStepDetectedAt = now

            // 원시 데이터 기록: 가공 전 "재부팅 이후 누적 걸음 수" 절대값을 그대로 기록
            loggingScope.launch {
                db.rawSensorLogDao().insert(
                    RawSensorLog(
                        sensorType = "STEP_COUNTER",
                        missionContext = missionContext,
                        timestamp = now,
                        value1 = totalSinceBoot.toFloat()
                    )
                )
            }

            if (baselineCount == null) {
                baselineCount = totalSinceBoot
                return
            }

            stepCount = resumeOffset + (totalSinceBoot - baselineCount!!)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 사용 안 함
    }
}