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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode

object TmtnMotion {
    const val TouchDownMillis = 100
    const val PressMillis = 120
    const val EnterMillis = 240
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    // Critically damped spring, response 0.3s (stiffness = (2π / response)²).
    val TouchStiffness = ((2.0 * Math.PI / 0.3) * (2.0 * Math.PI / 0.3)).toFloat()

    // ⚠️ 2026-09-18 추가(UI/UX 핸드오프 FR01~08 "첫 복구") - 재료가 댐으로 이동하는
    // 연출(Travel) + 자리잡는 연출(Settle) 시간. reducedMotion이면 SheetReducedMillis로
    // 즉시(거의) 전환.
    const val FirstRepairTravelMillis = 500
    const val FirstRepairSettleMillis = 250
    const val SheetReducedMillis = 80
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
    return systemReduced
}

/** Interruptible feedback; click handlers run immediately. */
@Composable
fun Modifier.tmtnPressFeedback(source: MutableInteractionSource, enabled: Boolean = true, pressedScale: Float = .985f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduced = rememberTmtnReducedMotion()
    val keepStill = reduced || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !keepStill) pressedScale else 1f,
        animationSpec = if (keepStill) snap() else if (pressed)
            tween(TmtnMotion.TouchDownMillis, easing = TmtnMotion.EaseOut)
        else spring(dampingRatio = 1f, stiffness = TmtnMotion.TouchStiffness),
        label = "button press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed && enabled) .92f else 1f }
}

/** Keyboard / switch focus is separate from selected and pressed states. */
@Composable
fun Modifier.tmtnFocusOutline(source: MutableInteractionSource, shape: Shape, enabled: Boolean = true): Modifier {
    val focused by source.collectIsFocusedAsState()
    val colors = LocalTmtnColors.current
    return then(if (focused && enabled) Modifier.drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        // A light separation keeps the ink focus ring visible even on the filled ink button.
        drawOutline(outline, colors.background, style = Stroke((TmtnLayout.FocusWidth * 3).toPx()))
        drawOutline(outline, colors.onSurface, style = Stroke(TmtnLayout.FocusWidth.toPx()))
    } else Modifier)
}

/** Standard click semantics and cancel-on-drag, with shared touch-down feedback. */
@Composable
fun Modifier.tmtnClickable(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier {
    val interactions = remember { MutableInteractionSource() }
    return tmtnPressFeedback(interactions, enabled)
        .tmtnFocusOutline(interactions, TmtnLayout.ControlShape, enabled).clickable(
        interactionSource = interactions, indication = null, enabled = enabled, role = role,
        onClick = { com.tmtn.app.audio.TmtnAudio.play(com.tmtn.app.audio.TmtnSound.Tap); onClick() })
}
