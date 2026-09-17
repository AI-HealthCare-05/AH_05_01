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
        previous?.let { if (it == "null") shell("settings delete global animator_duration_scale")
            else shell("settings put global animator_duration_scale $it") }
        previous = null
    }
}
