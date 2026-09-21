package com.tmtn.app.sensor

import android.Manifest
import android.content.pm.ServiceInfo
import org.junit.Assert.*
import org.junit.Test

class SensorPermissionsTest {
    @Test fun walkingAndStairsNeverAskForLocationOrNotifications() {
        listOf("SENSOR_WALKING_DURATION", "SENSOR_RUNNING_DURATION", "SENSOR_FLOORS_CLIMBED", "SENSOR_STEPS").forEach { type ->
            assertEquals(listOf(Manifest.permission.ACTIVITY_RECOGNITION), SensorPermissions.required(type, 36))
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH, SensorPermissions.serviceType(type, 36))
        }
    }

    @Test fun distanceRequestsCoarseAndFineTogetherAndUsesLocationService() {
        assertEquals(setOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            SensorPermissions.required("SENSOR_RUNNING_DISTANCE", 36).toSet())
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            SensorPermissions.serviceType("SENSOR_RUNNING_DISTANCE", 36))
    }

    @Test fun oldDevicesDoNotRequireUnavailableActivityPermission() {
        assertTrue(SensorPermissions.required("SENSOR_WALKING_DURATION", 28).isEmpty())
        assertEquals(0, SensorPermissions.serviceType("SENSOR_WALKING_DURATION", 28))
    }
}
