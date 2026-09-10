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

/** A single gentle greeting on entry. No perpetual motion around reading/decision areas. */
@Composable
fun TmtnMascot(@DrawableRes image: Int, description: String?, modifier: Modifier = Modifier, greet: Boolean = true, reactToTap: Boolean = false) {
    val reduced = rememberTmtnReducedMotion()
    val arrival = remember(image) { Animatable(if (reduced) 1f else 0f) }
    val greeting = remember(image) { Animatable(0f) }
    var greetingRequest by remember(image) { mutableIntStateOf(0) }
    LaunchedEffect(image, reduced, greet, greetingRequest) {
        if (reduced) {
            arrival.snapTo(1f)
            greeting.snapTo(0f)
        } else {
            if (arrival.value < 1f) arrival.animateTo(1f, tween(TmtnMotion.EnterMillis, easing = TmtnMotion.EaseOut))
            if (greetingRequest > 0) {
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
    }
}
