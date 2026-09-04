package com.tmtn.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 모델 학습용 원시 센서 데이터를 그대로 기록하는 테이블.
 *
 * 지금까지 만든 매니저들(StairClimbManager, RunningManager 등)은 계산 로직을
 * 거쳐 최종 결과값(칸 수, km, 초)만 남기고 중간 원시값은 버렸다.
 * 이 테이블은 그 계산에 쓰인 "가공 전 원본 값"을 그대로 다 남겨서,
 * 나중에 별도 데이터셋으로 뽑아 모델 학습 등에 활용할 수 있게 한다.
 *
 * 센서 종류마다 값의 개수와 의미가 달라서, value1~value4로 넉넉하게
 * 컬럼을 만들어두고 센서 종류(sensorType)에 따라 의미를 다르게 쓴다.
 */
@Entity(tableName = "raw_sensor_logs")
data class RawSensorLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // 센서 종류 구분자. "PRESSURE", "STEP_DETECTOR", "STEP_COUNTER", "GPS" 중 하나.
    val sensorType: String,

    // 이 로그가 어떤 미션을 수행하는 중에 찍힌 것인지 (STAIR, RUN, WALK, STEP 등).
    // 같은 센서라도 어느 미션 중이었는지에 따라 나중에 데이터를 분리해서 볼 수 있다.
    val missionContext: String,

    // 측정된 시각 (epoch millis). 안드로이드 시스템 시각 기준.
    val timestamp: Long,

    // ── 센서별 값 의미 ──
    // PRESSURE:      value1 = 기압(hPa),        value2~4 = 사용 안 함
    // STEP_DETECTOR: value1 = 1.0(이벤트 발생 표시), value2~4 = 사용 안 함
    // STEP_COUNTER:  value1 = 누적 걸음 수(절대값), value2~4 = 사용 안 함
    // GPS:           value1 = 위도, value2 = 경도, value3 = 정확도(m), value4 = 속도(m/s, 있으면)
    val value1: Float,
    val value2: Float? = null,
    val value3: Float? = null,
    val value4: Float? = null
)