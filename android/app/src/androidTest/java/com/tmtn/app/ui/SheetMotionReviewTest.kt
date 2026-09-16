package com.tmtn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.common.TmtnSheetDialog
import com.tmtn.app.ui.common.TmtnSheetMotion
import com.tmtn.app.ui.theme.TMTNv1Theme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SheetMotionReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstFrameIsHiddenAndRepeatedCloseFiresOnlyOnce() {
        compose.mainClock.autoAdvance = false
        lateinit var motion: TmtnSheetMotion
        var closed = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            motion = remember { TmtnSheetMotion(scope, { false }, { true }, { closed++ }) }
        }
        compose.runOnIdle {
            motion.heightPx = 500
            assertEquals(0f, motion.alpha.value, .001f)
            assertEquals(0f, motion.progress, .001f)
            motion.scope.launch { motion.enter() }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertEquals(1f, motion.progress, .001f)
            motion.dismiss(); motion.dismiss()
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun reducedMotionDragDismissFadesInsteadOfJumpingOut() {
        compose.mainClock.autoAdvance = false
        lateinit var motion: TmtnSheetMotion
        var closed = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            motion = remember { TmtnSheetMotion(scope, { true }, { true }, { closed++ }) }
        }
        compose.runOnIdle { motion.heightPx = 500; motion.scope.launch { motion.enter() } }
        compose.mainClock.advanceTimeBy(220)
        compose.runOnIdle { motion.scope.launch {
            motion.grab(); motion.dragTo(120f); motion.release(0f, 96f, 125f, true)
        } }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertEquals(0, closed)
            assertEquals(120f, motion.offset.value, .001f)
            assertTrue(motion.alpha.value < 1f && motion.alpha.value > 0f)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, closed); assertEquals(0f, motion.progress, .001f) }
    }

    @Test fun saveStartingDuringCloseKeepsTheSheetAndItsDataVisible() {
        compose.mainClock.autoAdvance = false
        lateinit var motion: TmtnSheetMotion
        var canClose = true
        var closed = 0
        compose.setContent {
            val scope = rememberCoroutineScope()
            motion = remember { TmtnSheetMotion(scope, { false }, { canClose }, { closed++ }) }
        }
        compose.runOnIdle { motion.heightPx = 500; motion.scope.launch { motion.enter() } }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { motion.dismiss(); canClose = false }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(0, closed); assertEquals(1f, motion.progress, .001f); assertFalse(motion.dismissing) }
    }

    @Test fun aRealHandleDragClosesTheWindowOnce() {
        var opened by mutableStateOf(true)
        var closes = 0
        compose.setContent { TMTNv1Theme { Box(Modifier.fillMaxSize()) {
            if (opened) TmtnSheetDialog({ closes++; opened = false }) {
                val sheet = this
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(300.dp)
                    .tmtnSheetMotion().background(Color.White)) {
                    Box(with(sheet) { Modifier.fillMaxWidth().height(48.dp).tmtnSheetDragHandle().testTag("review-handle") })
                    Text("검증용 시트")
                }
            }
        } } }
        compose.onNodeWithTag("review-handle").performTouchInput { swipeDown(durationMillis = 120) }
        compose.waitForIdle()
        compose.onNodeWithText("검증용 시트").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closes) }
    }
}
