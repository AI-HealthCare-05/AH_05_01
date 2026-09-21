package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.ExerciseMissionOption
import com.tmtn.app.network.model.ExerciseMissionSessionResponse
import com.tmtn.app.network.model.ExerciseMissionsTodayResponse
import com.tmtn.app.sensor.CurrentExerciseSessionHolder
import com.tmtn.app.sensor.SensorDataHolder
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChallengeEngagementUiTest {
    @get:Rule val compose = createComposeRule()
    @After fun reset() { SensorDataHolder.resetAll(); CurrentExerciseSessionHolder.clear() }

    private fun walkingState() = CardHomeState().apply {
        activeExerciseSession.value = ExerciseMissionSessionResponse(
            UUID(0, 999), "ACTIVE", "SENSOR_WALKING_DURATION", "비버와 천천히 걷기",
            "WOOD", "나뭇가지", 180, null, 0, 0, null,
        )
    }

    @Test fun extraWalkingFollowsDetectionPauseAndGoal() {
        SensorDataHolder.resetAll()
        val state = walkingState()
        compose.setContent { TMTNv1Theme { ExerciseMissionRunningScreen(state, rememberCoroutineScope()) } }
        compose.onNodeWithText("측정을 준비하고 있어요").assertIsDisplayed()
        compose.runOnIdle { SensorDataHolder.setServiceRunning(true) }
        compose.onNodeWithText("움직임을 기다리고 있어요").assertIsDisplayed()
        compose.runOnIdle {
            SensorDataHolder.updateWalkingSeconds(90)
            SensorDataHolder.updateWalkingDetectedNow(true)
        }
        compose.onNodeWithText("움직임 인식 중").assertIsDisplayed()
        compose.onNodeWithText("01:30").assertIsDisplayed()
        compose.onNodeWithText("50% 달성").assertExists()
        capture("01-walking")
        compose.onNodeWithText("완료").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { SensorDataHolder.updateWalkingDetectedNow(false) }
        compose.onNodeWithText("움직임을 기다리고 있어요").performScrollTo().assertIsDisplayed()
        capture("02-waiting")
        compose.runOnIdle {
            state.activeExerciseSession.value = state.activeExerciseSession.value!!.copy(state = "PAUSED", accumulated_duration_seconds = 90)
            SensorDataHolder.updateWalkingSeconds(0)
        }
        compose.onNodeWithText("일시정지됨").assertIsDisplayed()
        compose.onNodeWithText("01:30").assertIsDisplayed()
        compose.onNodeWithText("이어하기").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            state.activeExerciseSession.value = state.activeExerciseSession.value!!.copy(state = "ACTIVE")
            SensorDataHolder.updateWalkingSeconds(180)
        }
        compose.onNodeWithText("목표를 채웠어요").performScrollTo().assertIsDisplayed()
        capture("03-goal")
        compose.onNodeWithText("완료").performScrollTo().assertIsEnabled()
    }

    @Test fun dailyListContainsFiveAndDoesNotReplaceCompletedExercise() {
        val options = (1..16).map { index -> ExerciseMissionOption(
            UUID(0, index.toLong()), if (index <= 6) "가볍게 걷기 $index" else "기지개 켜기 $index", "안내",
            if (index <= 6) "SENSOR_WALKING_DURATION" else "CHECK", 3, "분", "WOOD", "나뭇가지", false,
        ) }
        val state = CardHomeState().apply { exerciseMissionsToday.value = ExerciseMissionsTodayResponse(true, 0, 2, 2, options) }
        compose.setContent { TMTNv1Theme { ExerciseMissionListScreen(state, rememberCoroutineScope(), loadToday = {}) } }
        val cards = SemanticsMatcher("틈새 운동 카드") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("extra-mission-") == true }
        compose.waitUntil { compose.onAllNodes(cards).fetchSemanticsNodes().size == 5 }
        val before = compose.onAllNodes(cards).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        compose.onAllNodesWithText("센서형 · 움직임 측정", useUnmergedTree = true).assertCountEquals(2)
        capture("04-daily-five")
        val completedId = before.first().removePrefix("extra-mission-")
        compose.runOnIdle {
            state.exerciseMissionsToday.value = state.exerciseMissionsToday.value!!.copy(
                used = 1, remaining = 1, options = options.reversed().map { it.copy(already_completed_today = it.catalog_entry_id.toString() == completedId) })
        }
        compose.waitForIdle()
        assertEquals(before, compose.onAllNodes(cards).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] })
        compose.onNodeWithTag(before.first()).assertIsNotEnabled()
    }

    @Test fun enlargedTextKeepsControlsReachable() {
        SensorDataHolder.resetAll()
        SensorDataHolder.setServiceRunning(true)
        val state = walkingState()
        compose.setContent { TMTNv1Theme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                ExerciseMissionRunningScreen(state, rememberCoroutineScope())
            }
        } }
        compose.onNodeWithText("움직임을 기다리고 있어요").assertIsDisplayed()
        capture("05-large-text")
        compose.onNodeWithText("그만두기").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "challenge-ux")
        directory.mkdirs()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
