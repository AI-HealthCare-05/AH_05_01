package com.tmtn.app.ui

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Exercise the actual Android preference and always restore the device's previous value. */
internal object SystemMotionTest {
    private var previous: String? = null
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }
    fun disable() {
        if (previous == null) previous = shell("settings get global animator_duration_scale")
        shell("settings put global animator_duration_scale 0")
    }
    fun restore() {
        // Deleting the key leaves WindowManager's cached scale at 0 (its Settings read defaults to the
        // current value), which switches every app on the device to reduced motion until reboot.
        // Write the platform default back explicitly instead.
        previous?.let { shell("settings put global animator_duration_scale ${if (it == "null") "1.0" else it}") }
        previous = null
    }
}
