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

    // ⚠️ 2026-09-07 반영: 최근 걸음 감지 시각 - "지금 이 순간 움직이고 있나"를
    // 화면에 정확히 보여주려고 추가(전에는 SENSOR_FLOORS_CLIMBED가 항상 true로
    // 고정돼 있어서 가만히 있어도 "움직임을 확인했어요"가 계속 떴음).
    private var lastStepDetectedAt: Long = 0L
    private val recentlyActiveWindowMs = 3000L

    fun isRecentlyActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        lastStepDetectedAt != 0L && nowMs - lastStepDetectedAt <= recentlyActiveWindowMs

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
        // ⚠️ 2026-09-07 반영: SENSOR_DELAY_NORMAL(약 200ms지만 기기에 따라 훨씬 느릴 수
        // 있음, 일부 기기는 기압 센서가 1초에 1번 정도만 갱신)이라 실제로 계단을 오르고
        // 있는 동안 화면이 한참 안 오르다가 갑자기 몰아서 반영되는 것처럼 보였음
        // (QA - "7칸 올라갔는데 계속 4칸이야"). GAME으로 폴링 주기를 앞당김.
        pressureSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
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
        lastStepDetectedAt = 0L
    }

    // ⚠️ 2026-09-06 추가: reset()과 달리 0부터가 아니라 서버가 준 baseline부터 이어서 셈.
    // floorsClimbed는 상대 증가값(+=)으로 관리되니 초기값만 baseline으로 맞추면 됨 -
    // 기압 기준점(baselineAltitude)은 그대로 초기화해서 다음 측정값으로 새로 잡음.
    fun resumeFrom(baseline: Int) {
        baselineAltitude = null
        referenceSetAt = 0L
        floorsClimbed = baseline
        altitudeWindow.clear()
        stepsSincePendingClimb = 0
        lastStepDetectedAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {

            Sensor.TYPE_STEP_DETECTOR -> {
                val now = System.currentTimeMillis()
                // 지금 누적 중인 상승 구간에 걸음이 있었다는 걸 기록한다.
                stepsSincePendingClimb++
                lastStepDetectedAt = now
                logRaw(sensorType = "STEP_DETECTOR", timestamp = now, value1 = 1f)
                // ⚠️ 2026-09-08 QA(계단 오탐) 임시 진단 로그 - 가만히 앉아있을 때도 걸음
                // 감지 센서가 반응하는지 확인용. 원인 확정되면 지워도 됨.
                android.util.Log.w("StairClimb", "step detected: stepsSincePendingClimb=$stepsSincePendingClimb")
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

                // ⚠️ 2026-09-08 QA(계단 오탐 - "의자에 앉았더니 갑자기 3칸 늘었다") 임시
                // 진단 로그 - 매 압력 이벤트마다 실제 고도값·기준점·판정 재료를 전부 남김.
                // 원인 확정되면 지워도 됨.
                android.util.Log.w(
                    "StairClimb",
                    "pressure event: rawPressure=$rawPressure rawAltitude=$rawAltitude " +
                        "smoothedAltitude=$smoothedAltitude baseline=$baselineAltitude " +
                        "climbedFromBaseline=$climbedFromBaseline stepsSincePendingClimb=$stepsSincePendingClimb " +
                        "floorsClimbed=$floorsClimbed"
                )

                // 고도 조건(0.6m 이상 상승) + 전진 이동 조건(그 사이 걸음이 최소 1번)을
                // 둘 다 만족해야 인정한다. 걸음이 전혀 없다면(폰 들기, 앉아서 흔들림)
                // 고도가 아무리 올라도 인정되지 않는다.
                if (climbedFromBaseline > climbThresholdM) {
                    // ⚠️ 2026-09-09 QA 반영: "그냥 걸으면서 폰을 위아래로 흔드니까 카운트가
                    // 됐다"는 실사용 보고 - 원인은 minStepsRequiredForCredit이 상승폭과
                    // 무관하게 고정 1이었던 것. climbThresholdM(0.6m)은 계단 3~4칸에
                    // 해당하는 높이인데, 정상 보행 중엔 자연스럽게 걸음이 계속 잡히고
                    // 있으니 "최소 1걸음"은 사실상 거의 항상 이미 충족된 상태였음 - 그
                    // 상태에서 손으로 폰을 팔 길이만큼 들어올리기만 해도(실제 기압 변화가
                    // 있으니) 계단으로 인정돼버렸음. 인정하려는 칸 수(newFloors)만큼은
                    // 최소한 걸음도 있어야 한다는 물리적 제약(계단 한 칸에 최소 한 걸음)을
                    // 추가함 - 완전히 막을 수는 없지만(몇 걸음 걸으면서도 흔들 수는 있으니),
                    // 정지 상태에서의 순간적인 흔들기 부정계수는 확실히 막음.
                    val newFloors = (climbedFromBaseline / stepHeightM).toInt().coerceAtLeast(1)
                    val requiredSteps = maxOf(minStepsRequiredForCredit, newFloors)
                    if (stepsSincePendingClimb >= requiredSteps) {
                        floorsClimbed += newFloors
                        android.util.Log.w(
                            "StairClimb",
                            "CREDITED: +$newFloors floors (now $floorsClimbed) - " +
                                "climbedFromBaseline=$climbedFromBaseline stepsSincePendingClimb=$stepsSincePendingClimb"
                        )
                        baselineAltitude = smoothedAltitude
                        referenceSetAt = now
                        stepsSincePendingClimb = 0
                    }
                    // 걸음 조건을 못 채웠다면, 기준점은 그대로 두고 조금 더 기다린다.
                    // (혹시 이 상승이 순수 노이즈라면 다음에 다시 낮아질 것이고,
                    // 진짜 계단이라면 뒤이어 걸음이 잡히면서 다음 판정에서 인정될 수 있다)
                } else if (climbedFromBaseline < -climbThresholdM) {
                    // ⚠️ 2026-09-07 반영: 계단을 "내려간" 경우, 기존엔 5분이 지나야만
                    // 기준점을 다시 잡았음(referenceResetIntervalMs). 그 사이 기준점이
                    // 계속 "정상보다 높은 지점"에 고정된 채로 남아있어서, 다시 올라가도
                    // 상승분이 정확히 안 잡히거나(기준점이 이미 높아서 상승폭이 작게
                    // 계산됨), 반대로 원래 위치로 "복귀"하는 것 자체가 이전 기준점보다
                    // 낮았다가 다시 올라오는 구간으로 오인되어 중복 카운트되는 문제가
                    // 있었음(QA - "내려가는데도 칸수가 올랐다"). 뚜렷하게 내려간 게
                    // 확인되면(0.6m 이상 하강) 그 지점을 새 기준점으로 즉시 갱신 -
                    // "지금 서 있는 층"을 기준으로 다시 정확하게 잼.
                    baselineAltitude = smoothedAltitude
                    referenceSetAt = now
                    stepsSincePendingClimb = 0
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