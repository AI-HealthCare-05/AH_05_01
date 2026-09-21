package com.tmtn.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.tmtn.app.ui.theme.TmtnMotion
import com.tmtn.app.ui.theme.rememberTmtnReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Scrim opacity once the sheet has fully arrived; the platform dim is disabled so it can follow the sheet. */
private const val ScrimAlpha = .45f

/**
 * Sheet motion owned by the dialog, shared by every caller:
 * enters from the bottom edge, tracks the finger 1:1, springs back or leaves with the release velocity,
 * and exits the way it came. Reduced motion keeps a short fade instead of any travel.
 */
internal class TmtnSheetMotion(
    val scope: CoroutineScope,
    private val reduced: () -> Boolean,
    private val canDismiss: () -> Boolean,
    private val onDismiss: () -> Unit,
) {
    val offset: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val alpha: Animatable<Float, AnimationVector1D> = Animatable(0f)
    private var dragging by mutableStateOf(false)
    private var dragY by mutableFloatStateOf(0f)
    val translationY: Float get() = if (dragging) dragY else offset.value
    var heightPx by mutableIntStateOf(0)
    var dismissing by mutableStateOf(false)
    private var entered = false

    /** 0 while the sheet is off screen, 1 when it rests at its final position. Drives the scrim. */
    val progress: Float
        get() = if (reduced()) alpha.value else alpha.value * (1f - (translationY / heightPx.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f))

    /** Real things slow before they stop: over-drag upward moves less the further it goes. */
    fun rubberBanded(raw: Float): Float {
        if (raw >= 0f) return raw
        val h = heightPx.toFloat().coerceAtLeast(1f)
        return raw * .55f * h / (h + .55f * abs(raw))
    }

    suspend fun enter() {
        if (entered || heightPx == 0 || dismissing) return
        entered = true
        if (reduced()) {
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(TmtnMotion.SheetReducedMillis, easing = TmtnMotion.EaseOut))
        } else {
            offset.snapTo(heightPx.toFloat())
            alpha.snapTo(1f)
            offset.animateTo(0f, tween(TmtnMotion.SheetEnterMillis, easing = TmtnMotion.EaseDrawer))
        }
    }

    /** Scrim tap, back press and explicit close buttons all leave along the entry path, then run [then]. */
    fun dismiss(then: () -> Unit = onDismiss) {
        if (dismissing || !canDismiss()) return
        dismissing = true
        scope.launch {
            if (reduced()) alpha.animateTo(0f, tween(TmtnMotion.SheetReducedMillis, easing = TmtnMotion.EaseOut))
            else offset.animateTo(heightPx.toFloat(), tween(TmtnMotion.SheetExitMillis, easing = TmtnMotion.EaseOut))
            finishDismiss(then)
        }
    }

    /** Grabbing the sheet mid-flight cancels whatever it was doing and hands control back to the finger. */
    suspend fun grab() {
        dragY = offset.value
        dragging = true
        offset.stop()
        alpha.stop()
        alpha.snapTo(1f)
        dismissing = false
    }

    fun dragTo(raw: Float) { dragY = rubberBanded(raw) }

    private suspend fun finishDismiss(then: () -> Unit) {
        // Saving may have begun during the exit animation. Keep that operation's sheet open.
        if (canDismiss()) then() else {
            offset.snapTo(0f)
            alpha.snapTo(1f)
            dismissing = false
        }
    }

    /**
     * Decide from the release velocity, not only the distance: a short fast flick closes, a slow long drag
     * that ends short of the threshold springs back. The projected resting point follows Apple's
     * `project(v, 0.998)`, which for px/s is `v * 0.499`.
     */
    suspend fun release(velocityPxPerSecond: Float, distancePx: Float, flickPxPerSecond: Float, enabled: Boolean) {
        if (dragging) offset.snapTo(dragY)
        dragging = false
        val y = offset.value
        val h = heightPx.toFloat()
        val projected = y + velocityPxPerSecond * .499f
        val shouldDismiss = enabled && canDismiss() && y > 0f &&
            (y > distancePx || velocityPxPerSecond > flickPxPerSecond || projected > h / 2f)
        if (shouldDismiss) {
            dismissing = true
            if (reduced()) alpha.animateTo(0f, tween(TmtnMotion.SheetReducedMillis, easing = TmtnMotion.EaseOut))
            // The only spring with bounce in the app: a throw preceded it.
            else offset.animateTo(h, spring(dampingRatio = .85f, stiffness = TmtnMotion.TouchStiffness), initialVelocity = velocityPxPerSecond)
            finishDismiss(onDismiss)
        } else {
            if (reduced()) offset.snapTo(0f)
            else offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = TmtnMotion.TouchStiffness), initialVelocity = velocityPxPerSecond)
        }
    }
}

/** Content scope: the usual BoxScope alignment plus the sheet's motion and drag handle. */
class TmtnSheetScope internal constructor(
    box: BoxScope,
    private val motion: TmtnSheetMotion,
) : BoxScope by box {
    /** Animate out, then run [then] (defaults to the dialog's onDismiss). Ignored while the sheet cannot be dismissed. */
    fun dismiss(then: (() -> Unit)? = null) = if (then == null) motion.dismiss() else motion.dismiss(then)

    /** Apply to the sheet surface. Translation and fade live in the draw phase, never in layout. */
    fun Modifier.tmtnSheetMotion(): Modifier = this
        .onSizeChanged { motion.heightPx = it.height }
        .graphicsLayer {
            translationY = motion.translationY
            // Stay invisible until measured so the first frame never shows the sheet at rest.
            alpha = if (motion.heightPx == 0) 0f else motion.alpha.value
        }

    /** Apply to the grab handle only, so the buttons below keep plain clicks. */
    @Composable
    fun Modifier.tmtnSheetDragHandle(enabled: Boolean = true): Modifier {
        val density = LocalDensity.current
        val distancePx = with(density) { 96.dp.toPx() }
        val flickPxPerSecond = with(density) { 125.dp.toPx() }
        var raw by remember { mutableFloatStateOf(0f) }
        val latestEnabled by rememberUpdatedState(enabled)
        val drag = rememberDraggableState { delta ->
            raw += delta
            // Finger movement is synchronous; queued snap coroutines must not cancel the release spring.
            motion.dragTo(raw)
        }
        return draggable(
            state = drag,
            orientation = Orientation.Vertical,
            enabled = enabled,
            onDragStarted = { raw = motion.offset.value.coerceAtLeast(0f); motion.grab() },
            onDragStopped = { velocity -> motion.release(velocity, distancePx, flickPxPerSecond, latestEnabled) },
        )
    }
}

/**
 * A separate window keeps background tabs out of touch, keyboard and accessibility focus.
 * The window's own centred dialog animation is switched off; the sheet animates itself from the bottom edge.
 */
@Composable
fun TmtnSheetDialog(onDismiss: () -> Unit, canDismiss: Boolean = true, content: @Composable TmtnSheetScope.() -> Unit) {
    val density = LocalDensity.current
    val reduced by rememberUpdatedState(rememberTmtnReducedMotion())
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestCanDismiss by rememberUpdatedState(canDismiss)
    val scope = rememberCoroutineScope()
    val motion = remember { TmtnSheetMotion(scope, { reduced }, { latestCanDismiss }, { latestDismiss() }) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        Dialog(onDismissRequest = { motion.dismiss() }, properties = DialogProperties(
            // Compose 1.7 otherwise remeasures to screenHeightDp and shifts content under system bars.
            usePlatformDefaultWidth = true, decorFitsSystemWindows = false,
        )) {
            val dialogView = LocalView.current
            SideEffect {
                (dialogView.parent as? DialogWindowProvider)?.window?.apply {
                    setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // No centred scale/fade from the platform; the sheet owns its entry and exit.
                    setWindowAnimations(0)
                    // The scrim below follows the sheet's progress instead of appearing all at once.
                    setDimAmount(0f)
                    // Lay the window out behind the status and navigation bars so the scrim covers them too;
                    // the content box below still pads by safeDrawing.
                    addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                            // Without this a dialog window gets an opaque system-drawn status bar.
                            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                    )
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        attributes = attributes.apply {
                            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                            fitInsetsTypes = 0
                        }
                    }
                    // The bars themselves stay transparent; only the scrim tints them.
                    @Suppress("DEPRECATION")
                    statusBarColor = android.graphics.Color.TRANSPARENT
                    @Suppress("DEPRECATION")
                    navigationBarColor = android.graphics.Color.TRANSPARENT
                    isStatusBarContrastEnforced = false
                    isNavigationBarContrastEnforced = false
                }
            }
            // Keyed on Unit on purpose: keying on the measured height restarted this effect one frame
            // after the entry animation began and cancelled it, leaving the sheet parked off screen.
            LaunchedEffect(Unit) {
                snapshotFlow { motion.heightPx }.first { it > 0 }
                motion.enter()
            }
            CompositionLocalProvider(LocalDensity provides density) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    // Scrim and tap-outside target. Covers the system bars too.
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer { alpha = motion.progress }
                        .background(Color.Black.copy(alpha = ScrimAlpha))
                        .clickable(onClickLabel = "시트 닫기") { motion.dismiss() })
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                        contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(availableWidth).fillMaxHeight().testTag("tmtn-sheet-dialog")) {
                            TmtnSheetScope(this, motion).content()
                        }
                    }
                }
            }
        }
    }
}
