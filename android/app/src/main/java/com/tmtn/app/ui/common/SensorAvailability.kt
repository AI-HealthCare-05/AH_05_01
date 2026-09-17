package com.tmtn.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager

/**
 * ⚠️ 2026-09-12 신규(2026-09-13 공용화) - 팀 QA 지적: "TYPE_STEP_COUNTER 미지원 또는
 * 권한 거부 기기에서 미션 완료·보상까지 시험 필요". 원래 "틈새 운동" 전용으로 만들었는데,
 * "오늘의 카드"(SensorChallengeScreens.kt) 쪽엔 이 체크 자체가 없어서 센서 없는 기기가
 * 그냥 측정 화면에 멈춰있는 문제가 있었음 - 공용 위치로 옮겨서 양쪽 다 재사용.
 */
fun requiredHardwareSensorType(execType: String): Int? = when (execType) {
    "SENSOR_STEPS_IN_PLACE", "SENSOR_STEPS" -> Sensor.TYPE_STEP_COUNTER
    "SENSOR_FLOORS_CLIMBED" -> Sensor.TYPE_PRESSURE
    "SENSOR_WALKING_DURATION", "SENSOR_RUNNING_DURATION" -> Sensor.TYPE_STEP_DETECTOR
    else -> null // TIMER/CHECK/거리(GPS)는 하드웨어 센서 유무 체크 대상이 아님
}

fun hasRequiredSensor(context: Context, execType: String): Boolean {
    val sensorType = requiredHardwareSensorType(execType) ?: return true
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    return sensorManager.getDefaultSensor(sensorType) != null
}

// ⚠️ 2026-09-17 추가(QA F07/F12) - "달리기(거리)"는 GPS 칩 유무가 아니라 "지금 위치
// 권한·위치 서비스가 켜져 있는지"가 관건이라 hasRequiredSensor()의 하드웨어 존재 체크와는
// 성격이 달라서 별도 함수로 둔다. 둘 다 시작 전 프리체크용 - 측정 화면(EXTRA_RUNNING)
// 진입 전에 걸러서, 권한/위치가 없는 채로 세션부터 만들어버리는 일이 없게 한다.
fun hasLocationPermission(context: Context): Boolean {
    val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

fun isGpsProviderEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
}
