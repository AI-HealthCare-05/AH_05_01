package com.tmtn.app.sensor

import android.Manifest
import android.content.pm.ServiceInfo

/** Permissions follow the selected measurement; notification permission never gates a workout. */
internal object SensorPermissions {
    fun needsLocation(execType: String?) = execType == "SENSOR_RUNNING_DISTANCE"

    fun required(execType: String?, sdk: Int): List<String> = buildList {
        if (sdk >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (needsLocation(execType)) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    fun serviceType(execType: String?, sdk: Int): Int {
        val health = if (sdk >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        val location = if (needsLocation(execType) && sdk >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        return health or location
    }
}
