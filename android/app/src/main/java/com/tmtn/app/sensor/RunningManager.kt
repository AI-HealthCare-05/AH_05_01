package com.tmtn.app.sensor

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.tmtn.app.data.local.AppDatabase
import com.tmtn.app.data.local.RawSensorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.annotation.SuppressLint
/**
 * GPS로 달린 거리를 누적 계산하는 클래스. "목표 거리(m)" 방식의 달리기 미션에 사용.
 *
 * 원리: 위치가 갱신될 때마다 "직전 위치 ~ 현재 위치" 사이의 직선 거리를 계산해서
 * 계속 더해나간다.
 *
 * 기존 버그: 속도의 상한선(GPS 오류로 인한 튐 방지)만 있고 하한선이 없어서,
 * 걷는 정도의 느린 속도로 이동해도 거리가 그대로 누적되는 문제가 있었다
 * (실측 결과 5번 중 2번 발생). 최소 속도 기준을 추가해서, 달리기라고 부를 만한
 * 속도가 아니면 거리에 반영하지 않도록 했다.
 *
 * 주의: 실외에서만 정확하게 작동한다. 실내(트레드밀 등)에서는 GPS 신호가
 * 약하거나 아예 안 잡혀서 거리가 거의 늘지 않을 수 있다.
 */
class RunningManager(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val db by lazy { AppDatabase.getInstance(context) }
    private val loggingScope = CoroutineScope(Dispatchers.IO)

    private var lastLocation: Location? = null

    // 누적 달린 거리 (미터 단위)
    var totalDistanceMeters = 0f
        private set

    // GPS 오차로 인한 "튐" 현상을 걸러내기 위한 상한선
    private val minAccuracyMeters = 20f  // 정확도가 이보다 나쁜(큰) 위치는 계산에서 제외
    private val maxSpeedMps = 8f         // 초당 8m(약 시속 29km) 넘으면 GPS 오류로 간주하고 무시

    // 달리기로 인정할 최소 속도. 시속 약 8km(초당 2.2m) 이상이어야 "달리기"로 본다.
    // 일반적인 빠른 걷기는 시속 6~7km 수준이라, 이보다 높게 잡아 걷기와 구분한다.
    // 실측하며 조정이 필요할 수 있다.
    private val minRunningSpeedMps = 2.2f

    fun isAvailable(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    @SuppressLint("MissingPermission")
    fun start() {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,   // 최소 1초 간격으로 위치 갱신 요청
            0f,      // 최소 이동 거리 제한 없음 (아래에서 직접 필터링하므로)
            this
        )
    }

    fun stop() {
        locationManager.removeUpdates(this)
    }

    fun reset() {
        lastLocation = null
        totalDistanceMeters = 0f
    }

    // ⚠️ 2026-09-06 추가: reset()과 달리 0부터가 아니라 서버가 준 baseline(미터)부터
    // 이어서 셈. totalDistanceMeters는 상대 증가값(+=)으로 관리되니 초기값만 baseline으로
    // 맞추면 됨 - lastLocation은 그대로 초기화해서 다음 위치 업데이트로 새로 기준을 잡음.
    fun resumeFrom(baselineMeters: Int) {
        lastLocation = null
        totalDistanceMeters = baselineMeters.toFloat()
    }

    override fun onLocationChanged(location: Location) {
        // 원시 데이터 기록: 정확도/속도 필터링 이전의 위치값을 그대로 기록
        loggingScope.launch {
            db.rawSensorLogDao().insert(
                RawSensorLog(
                    sensorType = "GPS",
                    missionContext = "RUN_DISTANCE",
                    timestamp = location.time,
                    value1 = location.latitude.toFloat(),
                    value2 = location.longitude.toFloat(),
                    value3 = location.accuracy,
                    value4 = if (location.hasSpeed()) location.speed else null
                )
            )
        }

        // 정확도가 너무 나쁜 위치는 계산에 포함하지 않음
        if (location.accuracy > minAccuracyMeters) return

        val previous = lastLocation
        if (previous == null) {
            lastLocation = location
            return
        }

        val distance = previous.distanceTo(location)  // 두 지점 사이 거리(m)
        val timeDeltaSec = (location.time - previous.time) / 1000f

        if (timeDeltaSec <= 0f) return  // 시간 차이가 0이면 나누기 오류 방지

        val speed = distance / timeDeltaSec

        // GPS 튐(너무 빠름)과 걷는 수준(너무 느림) 둘 다 걸러낸다.
        // 두 경우 모두 "이번 이동은 거리에 반영하지 않지만, 다음 비교를 위해
        // 현재 위치는 갱신"해서 다음 구간부터 다시 정상 판정되게 한다.
        if (speed > maxSpeedMps || speed < minRunningSpeedMps) {
            lastLocation = location
            return
        }

        totalDistanceMeters += distance
        lastLocation = location
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // 구버전 API 호환용. 최신 안드로이드에서는 호출되지 않는다.
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}