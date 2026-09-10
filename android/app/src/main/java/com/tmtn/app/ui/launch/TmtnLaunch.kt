package com.tmtn.app.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tmtn.app.R
import com.tmtn.app.ui.theme.ColorBrandForest
import com.tmtn.app.ui.theme.TmtnMotion
import com.tmtn.app.ui.theme.rememberTmtnReducedMotion
import org.json.JSONObject

private class LaunchTracks(json: String) {
    private val source = JSONObject(json).getJSONObject("tracks")
    private val tracks = source.keys().asSequence().associateWith { key ->
        val values = source.getJSONArray(key)
        List(values.length()) { i -> values.getJSONArray(i).let { it.getDouble(0).toFloat() to it.getDouble(1).toFloat() } }
    }

    fun value(name: String, frame: Float): Float {
        val points = tracks.getValue(name)
        if (frame <= points.first().first) return points.first().second
        val index = points.indexOfFirst { it.first > frame }
        if (index < 0) return points.last().second
        val (start, from) = points[index - 1]
        val (end, to) = points[index]
        val t = ((frame - start) / (end - start)).coerceIn(0f, 1f)
        // Exact easing from the supplied Lottie: x=(1/3,2/3), y=(0,1).
        return from + (to - from) * t * t * (3f - 2f * t)
    }
}

/** Full 132-frame performance, started only after the system splash is removed.
 * The caller composes the destination underneath, so completion never exposes an empty window.
 */
@Composable
fun TmtnLaunchOverlay(
    ready: Boolean,
    onFinished: () -> Unit,
    reducedMotion: Boolean = rememberTmtnReducedMotion(),
    onExitStarted: () -> Unit = {},
) {
    val latestFinished by rememberUpdatedState(onFinished)
    val latestExitStarted by rememberUpdatedState(onExitStarted)
    val frame = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    LaunchedEffect(ready, reducedMotion) {
        if (!ready) return@LaunchedEffect
        // Anchor playback to a visible frame, not Activity creation or a fixed navigation timer.
        withFrameNanos { }
        if (reducedMotion) {
            latestExitStarted()
            withFrameNanos { }
            opacity.animateTo(0f, tween(TmtnMotion.PressMillis, easing = TmtnMotion.EaseOut))
        } else {
            frame.animateTo(132f, tween(((132f - frame.value) / 60f * 1000).toInt(), easing = LinearEasing))
            latestExitStarted()
            withFrameNanos { }
            opacity.animateTo(0f, tween(TmtnMotion.EnterMillis, easing = TmtnMotion.EaseOut))
        }
        latestFinished()
    }
    Box(
        Modifier.fillMaxSize().testTag("launch-overlay")
            .graphicsLayer { alpha = opacity.value }
            .background(ColorBrandForest)
            // The waiting screen must not click through to unseen login/home controls.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { contentDescription = "틈튼 시작 화면" },
    ) {
        TmtnLaunchFrame(frame = { frame.value })
    }
}

/** Native drawing of the supplied 7 PNG layers; Fit geometry, no video crop or GIF loop. */
@Composable
internal fun TmtnLaunchFrame(frame: () -> Float) {
    val resources = LocalContext.current.resources
    val tracks = remember(resources) {
        LaunchTracks(resources.openRawResource(R.raw.launch_motion).bufferedReader().use { it.readText() })
    }
    val head = ImageBitmap.imageResource(R.drawable.launch_head_base)
    val leaf = ImageBitmap.imageResource(R.drawable.launch_leaf)
    val eyes = listOf(ImageBitmap.imageResource(R.drawable.launch_eye_0), ImageBitmap.imageResource(R.drawable.launch_eye_1))
    val smiles = listOf(ImageBitmap.imageResource(R.drawable.launch_smile_0), ImageBitmap.imageResource(R.drawable.launch_smile_1))
    val wordmark = ImageBitmap.imageResource(R.drawable.splash_wordmark)
    Canvas(Modifier.fillMaxSize().testTag("launch-artwork")) {
        val f = frame()
        val unit = minOf(size.width / 360f, size.height / 800f)
        val center = Offset(size.width / 2f, size.height * .475f + (tracks.value("headY", f) - 380f) * unit)
        val headScale = 160f * unit / 940f * tracks.value("headScale", f)
        if (headScale > 0f) withTransform({
            translate(center.x, center.y)
            rotate(tracks.value("headRotation", f), Offset.Zero)
            scale(headScale, headScale, Offset.Zero)
            translate(-470f, -435.5f)
        }) {
            drawImage(head, Offset(0f, 23f))
            withTransform({ rotate(tracks.value("leafRotation", f), Offset(503f, 184f)) }) {
                drawImage(leaf, Offset(269f, 0f))
            }
            drawEye(eyes[0], smiles[0], Offset(248f, 487f), Offset(41f, 42f), tracks.value("eyeY", f) / 100f, tracks.value("eyeOpacity", f) / 100f, tracks.value("smileOpacity", f) / 100f)
            drawEye(eyes[1], smiles[1], Offset(660f, 491f), Offset(40f, 42f), tracks.value("eyeY", f) / 100f, tracks.value("eyeOpacity", f) / 100f, tracks.value("smileOpacity", f) / 100f)
        }
        val logoWidth = 105f * unit
        val logoScale = logoWidth / wordmark.width
        val logoTop = size.height - 70f * unit - wordmark.height * logoScale
        withTransform({ translate((size.width - logoWidth) / 2f, logoTop); scale(logoScale, logoScale, Offset.Zero) }) {
            drawImage(wordmark, alpha = tracks.value("logoOpacity", f) / 100f)
        }
    }
}

private fun DrawScope.drawEye(eye: ImageBitmap, smile: ImageBitmap, center: Offset, anchor: Offset, scaleY: Float, eyeAlpha: Float, smileAlpha: Float) {
    withTransform({ scale(1f, scaleY, center) }) { drawImage(eye, center - anchor, alpha = eyeAlpha) }
    drawImage(smile, center - Offset(41f, 19f), alpha = smileAlpha)
}
