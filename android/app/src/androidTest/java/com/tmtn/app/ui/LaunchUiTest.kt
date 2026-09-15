package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.ui.launch.TmtnLaunchFrame
import com.tmtn.app.ui.launch.TmtnLaunchOverlay
import com.tmtn.app.ui.theme.ColorBrandForest
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LaunchUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playbackWaitsForSystemHandoffAndCompletesOnceAfterTheExit() {
        compose.mainClock.autoAdvance = false
        var ready by mutableStateOf(false)
        var completions = 0
        var exits = 0
        compose.setContent { TMTNv1Theme { TmtnLaunchOverlay(ready, onFinished = { completions++ }, reducedMotion = false,
            onExitStarted = { exits++; assertEquals(0, completions) }) } }
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle { assertEquals(0, completions); ready = true }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle {
            assertEquals("Must not cut the nod/smile short", 0, completions)
            assertEquals("Destination stays hidden during the character animation", 0, exits)
        }
        compose.mainClock.advanceTimeBy(750)
        compose.runOnIdle { assertEquals("Finish only after the forest exit fades", 0, completions) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, completions); assertEquals(1, exits) }
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle { assertEquals("No looping", 1, completions) }
    }

    @Test fun reducedMotionDoesNotWaitForTheFullPerformance() {
        compose.mainClock.autoAdvance = false
        var completed = false
        compose.setContent { TMTNv1Theme { TmtnLaunchOverlay(true, onFinished = { completed = true }, reducedMotion = true) } }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertTrue(completed) }
    }

    @Test fun changingMotionPreferenceFinishesInsteadOfRestartingThePerformance() {
        compose.mainClock.autoAdvance = false
        var reduced by mutableStateOf(false)
        var completions = 0
        compose.setContent { TMTNv1Theme { TmtnLaunchOverlay(true, onFinished = { completions++ }, reducedMotion = reduced) } }
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { reduced = true }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun captureWholeFaceBlinkSmileAndExitAtTwoScreenRatios() {
        var frame by mutableFloatStateOf(0f)
        var compact by mutableStateOf(false)
        compose.setContent {
            TMTNv1Theme {
                Box(Modifier.requiredSize(if (compact) 320.dp else 360.dp, if (compact) 568.dp else 800.dp).background(ColorBrandForest).testTag("launch-stage")) {
                    TmtnLaunchFrame(frame = { frame })
                }
            }
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "splash-native-qa").apply { mkdirs() }
        for ((name, value) in listOf("01-start" to 0f, "02-blink" to 22f, "03-nod" to 56f, "04-smile" to 78f, "05-exit" to 116f, "06-end" to 132f)) {
            compose.runOnIdle { frame = value }
            val bitmap = compose.onNodeWithTag("launch-stage").captureToImage().asAndroidBitmap()
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertEquals("Background must reach every edge", android.graphics.Color.rgb(63, 93, 75), bitmap.getPixel(0, 0))
        }
        compose.runOnIdle { compact = true; frame = 56f }
        val bitmap = compose.onNodeWithTag("launch-stage").captureToImage().asAndroidBitmap()
        File(directory, "07-compact.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
