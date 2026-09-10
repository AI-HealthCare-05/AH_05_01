package com.tmtn.app.ui.theme

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

object TmtnMotion {
    const val PressMillis = 120
    const val EnterMillis = 240
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    // Critically damped spring, response 0.3s (stiffness = (2π / response)²).
    val TouchStiffness = ((2.0 * Math.PI / 0.3) * (2.0 * Math.PI / 0.3)).toFloat()
}

@Composable
fun rememberTmtnReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var systemReduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemReduced = !ValueAnimator.areAnimatorsEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return systemReduced || AccessibilitySettingsHolder.reducedMotion.value || AccessibilitySettingsHolder.seniorMode.value
}

/** Interruptible feedback; click handlers run immediately. */
@Composable
fun Modifier.tmtnPressFeedback(source: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduced = rememberTmtnReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) 0.97f else 1f,
        animationSpec = if (reduced) snap() else if (pressed)
            tween(100, easing = TmtnMotion.EaseOut)
        else spring(dampingRatio = 1f, stiffness = TmtnMotion.TouchStiffness),
        label = "button press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed && enabled) .88f else 1f }
}

/** Standard click semantics and cancel-on-drag, with shared touch-down feedback. */
@Composable
fun Modifier.tmtnClickable(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier {
    val interactions = remember { MutableInteractionSource() }
    return tmtnPressFeedback(interactions, enabled).clickable(
        interactionSource = interactions, indication = ripple(), enabled = enabled, role = role, onClick = onClick)
}
