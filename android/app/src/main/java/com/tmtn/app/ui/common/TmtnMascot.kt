package com.tmtn.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.TmtnMotion
import com.tmtn.app.ui.theme.rememberTmtnReducedMotion
import com.tmtn.app.ui.theme.tmtnClickable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import com.tmtn.app.ui.theme.ColorBrandForest
import kotlinx.coroutines.launch

/** A single gentle greeting on entry. No perpetual motion around reading/decision areas. */
@Composable
fun TmtnMascot(@DrawableRes image: Int, description: String?, modifier: Modifier = Modifier, greet: Boolean = true, reactToTap: Boolean = false) {
    val reduced = rememberTmtnReducedMotion() || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val arrival = remember(image) { Animatable(if (reduced) 1f else 0f) }
    val greeting = remember(image) { Animatable(0f) }
    val leaf = remember(image) { Animatable(1f) }
    var greetingRequest by remember(image) { mutableIntStateOf(0) }
    LaunchedEffect(image, reduced, greet, greetingRequest) {
        if (reduced) {
            arrival.snapTo(1f)
            greeting.snapTo(0f)
            leaf.snapTo(1f)
        } else {
            if (arrival.value < 1f) arrival.animateTo(1f, tween(TmtnMotion.EnterMillis, easing = TmtnMotion.EaseOut))
            if (greetingRequest > 0) {
                launch {
                    leaf.snapTo(0f)
                    leaf.animateTo(1f, tween(480, easing = TmtnMotion.EaseOut))
                }
                // Repeated taps retarget the current pose. The original illustration stays intact.
                greeting.animateTo(-4f, spring(dampingRatio = 1f, stiffness = TmtnMotion.TouchStiffness))
                greeting.animateTo(0f, spring(dampingRatio = .8f, stiffness = TmtnMotion.TouchStiffness))
            } else if (greet) {
                greeting.animateTo(-1.5f, tween(240, easing = TmtnMotion.EaseOut))
                greeting.animateTo(0f, tween(240, easing = TmtnMotion.EaseOut))
            } else greeting.snapTo(0f)
        }
    }
    Box(modifier.then(if (reactToTap) Modifier.tmtnClickable { greetingRequest++ }
        .semantics { contentDescription = "비버에게 인사하기" } else Modifier), contentAlignment = Alignment.Center) {
        Image(
            painterResource(image), contentDescription = if (reactToTap) null else description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = arrival.value
                translationY = if (reduced) 0f else (1f - arrival.value) * 8.dp.toPx()
                scaleX = if (reduced) 1f else 0.97f + arrival.value * 0.03f
                scaleY = scaleX
                rotationZ = if (reduced) 0f else greeting.value
                transformOrigin = TransformOrigin(.5f, .85f)
            },
        )
        // Brief leaf flecks acknowledge an intentional greeting; never loop during reading.
        // Vector decoration follows the existing leaf motif, leaving the character image intact.
        if (reactToTap && !reduced) Canvas(Modifier.fillMaxSize()) {
            val progress = leaf.value
            if (progress > 0f && progress < 1f) {
                val opacity = (1f - progress).coerceIn(0f, 1f)
                val length = 8.dp.toPx()
                listOf(-1f, 1f).forEach { direction ->
                    val x = size.width * (.5f + direction * .26f) + direction * progress * 10.dp.toPx()
                    val y = size.height * .27f - progress * 16.dp.toPx()
                    translate(x, y) { rotate(direction * (25f + progress * 25f), Offset.Zero) {
                        val shape = Path().apply {
                            moveTo(0f, 0f)
                            quadraticTo(-length, -length, 0f, -length * 1.5f)
                            quadraticTo(length, -length, 0f, 0f)
                            close()
                        }
                        drawPath(shape, ColorBrandForest.copy(alpha = opacity))
                    } }
                }
            }
        }
    }
}
