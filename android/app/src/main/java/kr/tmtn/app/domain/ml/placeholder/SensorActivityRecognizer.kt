package kr.tmtn.app.domain.ml.placeholder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kr.tmtn.app.domain.ml.*
import kr.tmtn.app.domain.model.MissionType
import kotlin.math.abs

/**
 * 모델 2 자리를 채워 두는 임시 구현 — 안드로이드 기본 센서를 규칙으로 읽는다.
 *
 * ⚠ 학습된 행동 인식 모델이 아니다. 걸음 센서 증가분으로 "움직이는 중" 을 판정한다.
 *   실제 폰에서는 걷기·계단 오르기가 어느 정도 잡히므로 시연은 된다.
 *
 * 교체하는 사람에게:
 *  - 가속도계 원본이 필요하면 Sensor.TYPE_ACCELEROMETER 를 여기서 추가로 등록하고
 *    윈도우를 모아 모델에 넣으면 된다. 화면은 ActivitySnapshot 만 보므로 안 바뀐다.
 *  - 교체 후 info.isPlaceholder 를 false 로 바꿔야 화면의 "샘플" 안내가 사라진다.
 *
 * 읽는 것: 걸음 수, (있으면) 기압.  읽지 않는 것: 위치 기록, 심박, 연락처, 사진.
 */
class SensorActivityRecognizer(private val context: Context) : ActivityRecognizer {

    override val info = ModelInfo(
        name = "sensor-rule-placeholder",
        version = "0.1.0",
        isPlaceholder = true,
        note = "걸음 센서를 규칙으로 센 임시 구현입니다. 학습 모델로 교체할 자리입니다.",
    )

    private val sensorManager: SensorManager?
        get() = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private fun hasActivityPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** 권한과 무관하게, 이 기기에 걸음 센서가 물리적으로 있는지. */
    fun hasHardware(): Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    override fun availability(): RecognizerAvailability {
        val sm = sensorManager ?: return RecognizerAvailability.Blocked(listOf(BlockReason.NO_MOTION_SENSOR))
        val reasons = mutableListOf<BlockReason>()
        if (!hasActivityPermission()) reasons += BlockReason.NO_ACTIVITY_PERMISSION
        if (sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) == null) reasons += BlockReason.NO_MOTION_SENSOR
        return if (reasons.isEmpty()) RecognizerAvailability.Ready else RecognizerAvailability.Blocked(reasons)
    }

    /** 기압계가 없으면 계단 미션은 자동 측정을 못 한다. 별도로 알려 준다. */
    fun stairAvailability(): RecognizerAvailability {
        val sm = sensorManager ?: return RecognizerAvailability.Blocked(listOf(BlockReason.NO_BAROMETER))
        val reasons = mutableListOf<BlockReason>()
        if (!hasActivityPermission()) reasons += BlockReason.NO_ACTIVITY_PERMISSION
        if (sm.getDefaultSensor(Sensor.TYPE_PRESSURE) == null) reasons += BlockReason.NO_BAROMETER
        return if (reasons.isEmpty()) RecognizerAvailability.Ready else RecognizerAvailability.Blocked(reasons)
    }

    override fun measure(request: MeasureRequest, resumeFrom: ActivitySnapshot): Flow<ActivitySnapshot> = flow {
        val sm = sensorManager
        val stepSensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val pressureSensor = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        val state = SensorState()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_STEP_COUNTER -> {
                        val total = event.values.firstOrNull()?.toLong() ?: return
                        if (state.stepBase < 0) state.stepBase = total
                        val delta = (total - state.stepBase).coerceAtLeast(0)
                        if (delta > state.steps) {
                            state.steps = delta
                            state.lastStepAtMs = SystemClock.elapsedRealtime()
                        }
                    }
                    Sensor.TYPE_PRESSURE -> {
                        val hPa = event.values.firstOrNull() ?: return
                        val altitude = SensorManager.getAltitude(
                            SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa,
                        )
                        val prev = state.lastAltitude
                        if (prev != null) {
                            val rise = altitude - prev
                            // 계단 한 칸 ≈ 0.17m. 잡음(±0.05m 미만)은 버린다.
                            if (rise > 0.05f) state.climbedMeters += rise
                            if (abs(rise) > 3f) state.noisy = true
                        }
                        state.lastAltitude = altitude
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_STEP_COUNTER &&
                    accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                ) state.noisy = true
            }
        }

        try {
            if (stepSensor != null) sm.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            if (pressureSensor != null && request.missionType == MissionType.MODEL_STAIR_COUNT) {
                sm.registerListener(listener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }

            var active = resumeFrom.activeSeconds
            var elapsed = resumeFrom.elapsedSeconds
            var excluded = resumeFrom.excludedSeconds
            val stepOffset = resumeFrom.steps

            while (true) {
                delay(1000)
                elapsed += 1

                val now = SystemClock.elapsedRealtime()
                // 최근 4초 안에 걸음이 늘었으면 "움직이는 중" 으로 본다.
                val moving = state.lastStepAtMs > 0 && (now - state.lastStepAtMs) < 4000
                if (moving) active += 1
                if (state.noisy) { excluded += 1; state.noisy = false }

                val steps = stepOffset + state.steps.toInt()
                val quality = when {
                    stepSensor == null -> SignalQuality.WEAK
                    excluded > elapsed / 4 -> SignalQuality.FAIR
                    else -> SignalQuality.GOOD
                }

                emit(
                    ActivitySnapshot(
                        activeSeconds = active,
                        elapsedSeconds = elapsed,
                        restSeconds = (elapsed - active).coerceAtLeast(0),
                        excludedSeconds = excluded,
                        steps = steps,
                        // 보폭 0.70m 고정. 개인 보폭 추정도 모델이 할 일이다.
                        distanceMeters = steps * 0.70,
                        stairs = (state.climbedMeters / 0.17f).toInt(),
                        quality = quality,
                        moving = moving,
                    ),
                )
            }
        } finally {
            sm?.unregisterListener(listener)
        }
    }

    private class SensorState {
        @Volatile var stepBase: Long = -1
        @Volatile var steps: Long = 0
        @Volatile var lastStepAtMs: Long = 0
        @Volatile var lastAltitude: Float? = null
        @Volatile var climbedMeters: Float = 0f
        @Volatile var noisy: Boolean = false
    }
}
