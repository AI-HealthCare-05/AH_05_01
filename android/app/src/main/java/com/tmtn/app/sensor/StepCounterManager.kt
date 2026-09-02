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
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSinceBoot = event.values[0].toInt()
            val now = System.currentTimeMillis()

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

            stepCount = totalSinceBoot - baselineCount!!
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 사용 안 함
    }
}