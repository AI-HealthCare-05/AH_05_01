package com.tmtn.app.ui.theme

import androidx.compose.runtime.mutableStateOf

/** UI preferences shared by typography, control sizing and motion. */
object AccessibilitySettingsHolder {
    /** "NORMAL" / "LARGE" / "EXTRA_LARGE" */
    var textScaleHint = mutableStateOf("NORMAL")
    var seniorMode = mutableStateOf(false)
    val reducedMotion = mutableStateOf(false)
    val largeControlsEnabled = mutableStateOf(false)

    fun apply(largeControls: Boolean, seniorModeValue: Boolean, textScaleHintValue: String?) {
        largeControlsEnabled.value = largeControls
        seniorMode.value = seniorModeValue
        textScaleHint.value = textScaleHintValue ?: "NORMAL"
    }
}

fun textScaleHintToFactor(hint: String): Float = when (hint) {
    "LARGE" -> 1.15f
    "EXTRA_LARGE" -> 1.3f
    else -> 1f
}
