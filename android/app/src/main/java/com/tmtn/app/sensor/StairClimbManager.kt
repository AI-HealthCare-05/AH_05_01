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
 * 실제 계단 오르기(오른 칸 수)를 감지하는 클래스.
 *
 * Garmin의 계단 카운터 방식(공식 FAQ 참고)에서 핵심 아이디어를 가져왔다:
 * "고도만 오르는 것"이 아니라 "고도가 오르는 동안 동시에 전진 이동이
 * 있었는지"를 같이 봐야 한다. 실내에서는 GPS로 전진 이동 거리를 정확히
 * 잴 수 없어서, "그 상승 구간 동안 걸음이 실제로 있었는지"를 대리 지표로 쓴다.
 *
 * 기존 방식들의 문제점과 이번 방식이 다른 점:
 * - 순수 고도 임계값: 폰을 들었다 놓기만 해도 오작동 (걸음이 전혀 없는데도 인정됨)
 * - 최저점 추적: 가만히 있어도 노이즈가 쌓여 폭증
 * - 이번 방식: "상승분이 쌓이는 동안 걸음이 최소 1번이라도 있었는지"를
 *   요구해서, 걸음이 전혀 없는 폰 들기/앉아서 노이즈 흔들림을 원천적으로 배제한다.
 *
 * 기준점(baseline)은 고정 방식을 유지한다(안전성 확인됨). 5분마다 자동
 * 리셋해서 장시간 방치 시 드리프트가 무한정 쌓이는 것도 막는다.
 */
class StairClimbManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val db by lazy { AppDatabase.getInstance(context) }
    private val loggingScope = CoroutineScope(Dispatchers.IO)

    private val altitudeWindow = ArrayDeque<Float>()
    private val smoothingWindowSize = 8

    private var baselineAltitude: Float? = null
    private var referenceSetAt: Long = 0L
    private val referenceResetIntervalMs = 5 * 60 * 1000L  // 5분

    // 지금 기준점 이후로 누적되고 있는 상승 구간 동안 발생한 걸음 수.
    // 기준점이 갱신될 때마다(인정되거나 5분 리셋될 때) 0으로 초기화된다.
    private var stepsSincePendingClimb = 0

    // 이 구간 동안 최소 이만큼의 걸음이 있어야 "진짜 전진 이동이 있었다"고 보고 인정한다.
    // Garmin의 "전진 이동 + 상승"이라는 조건을 걸음 수로 근사한 것.
    // 1로 낮게 잡은 이유: 계단을 빠르게 오를 때 걸음 감지 센서가 걸음을
    // 자주 놓치는 것이 실측으로 확인되어서, 너무 엄격하게 잡으면 진짜
    // 계단도 놓칠 수 있다. "0번(전혀 없음)"과 "1번 이상"만 구분하는 것이 목적.
    private val minStepsRequiredForCredit = 1

    var floorsClimbed = 0
        private set

    private val stepHeightM = 0.18f
    private val climbThresholdM = 0.6f

    fun isAvailable(): Boolean = pressureSensor != null

    fun start() {
        pressureSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun reset() {
        baselineAltitude = null
        referenceSetAt = 0L
        floorsClimbed = 0
        altitudeWindow.clear()
        stepsSincePendingClimb = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {

            Sensor.TYPE_STEP_DETECTOR -> {
                val now = System.currentTimeMillis()
                // 지금 누적 중인 상승 구간에 걸음이 있었다는 걸 기록한다.
                stepsSincePendingClimb++
                logRaw(sensorType = "STEP_DETECTOR", timestamp = now, value1 = 1f)
            }

            Sensor.TYPE_PRESSURE -> {
                val rawPressure = event.values[0]
                val now = System.currentTimeMillis()

                logRaw(sensorType = "PRESSURE", timestamp = now, value1 = rawPressure)

                val rawAltitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, rawPressure
                )

                altitudeWindow.addLast(rawAltitude)
                if (altitudeWindow.size > smoothingWindowSize) {
                    altitudeWindow.removeFirst()
                }
                val smoothedAltitude = altitudeWindow.average().toFloat()

                if (baselineAltitude == null) {
                    baselineAltitude = smoothedAltitude
                    referenceSetAt = now
                    stepsSincePendingClimb = 0
                    return
                }

                val climbedFromBaseline = smoothedAltitude - baselineAltitude!!

                // 고도 조건(0.6m 이상 상승) + 전진 이동 조건(그 사이 걸음이 최소 1번)을
                // 둘 다 만족해야 인정한다. 걸음이 전혀 없다면(폰 들기, 앉아서 흔들림)
                // 고도가 아무리 올라도 인정되지 않는다.
                if (climbedFromBaseline > climbThresholdM) {
                    if (stepsSincePendingClimb >= minStepsRequiredForCredit) {
                        val newFloors = (climbedFromBaseline / stepHeightM).toInt().coerceAtLeast(1)
                        floorsClimbed += newFloors
                        baselineAltitude = smoothedAltitude
                        referenceSetAt = now
                        stepsSincePendingClimb = 0
                    }
                    // 걸음 조건을 못 채웠다면, 기준점은 그대로 두고 조금 더 기다린다.
                    // (혹시 이 상승이 순수 노이즈라면 다음에 다시 낮아질 것이고,
                    // 진짜 계단이라면 뒤이어 걸음이 잡히면서 다음 판정에서 인정될 수 있다)
                } else if (now - referenceSetAt > referenceResetIntervalMs) {
                    baselineAltitude = smoothedAltitude
                    referenceSetAt = now
                    stepsSincePendingClimb = 0
                }
            }
        }
    }

    private fun logRaw(sensorType: String, timestamp: Long, value1: Float) {
        loggingScope.launch {
            db.rawSensorLogDao().insert(
                RawSensorLog(
                    sensorType = sensorType,
                    missionContext = "STAIR",
                    timestamp = timestamp,
                    value1 = value1
                )
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 사용 안 함
    }
}