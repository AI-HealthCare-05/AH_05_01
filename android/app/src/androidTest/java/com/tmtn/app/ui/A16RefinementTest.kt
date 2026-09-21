package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.nav.*
import com.tmtn.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class A16RefinementTest {
    @get:Rule val compose = createComposeRule()
    @Composable private fun Page(tab: MainTab? = null, content: @Composable () -> Unit) {
        TMTNv1Theme { Column(Modifier.fillMaxSize().background(LocalTmtnColors.current.background)) {
            Box(Modifier.weight(1f)) { content() }
            if (tab != null) BottomNavBar(tab, {})
        } }
    }
    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "a16-refinement").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun assertOneLine(text: String) {
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); it(result)
            assertEquals(text, 1, result.single().lineCount)
            val r = result.single()
            // Compose can retain a parent-width paragraph inside a wrap-content Text.
            // Check actual glyph-line bounds rather than that unused paragraph width.
            assertFalse(text, r.isLineEllipsized(0))
            assertEquals(text.length, r.getLineEnd(0, visibleEnd = true))
            assertTrue(text, r.getLineRight(0) <= r.size.width + .51f)
            assertTrue(text, r.getLineBottom(0) <= r.size.height + .51f)
        }
    }
    @Test fun strengthSelectionKeepsAerobicRequestAndUsesDayBuckets() {
        val state = OnboardingState().apply { aerobicLowMinutes.value = 60; aerobicModerateMinutes.value = 90; aerobicHighMinutes.value = 20 }
        compose.setContent { Page { A08ExerciseScreen(state, rememberCoroutineScope(), { true }) } }
        compose.onNodeWithText("주 0일").assertIsDisplayed()
        compose.onNodeWithTag("strength-intensity-MODERATE").assertDoesNotExist()
        for (day in listOf(0,2,4)) compose.onNodeWithTag("strength-weekday-$day").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("다음으로").assertIsNotEnabled()
        compose.onNodeWithTag("strength-intensity-MODERATE").performScrollTo().performClick()
        compose.onNodeWithText("다음으로").assertIsEnabled()
        compose.onNodeWithText("평소 일주일,\n운동하는 날을 골라 주세요.").performScrollTo()
        capture("01-a16-three-days")
        compose.runOnIdle {
            val request = state.exerciseHabitsRequest()
            assertEquals("days", request.strength_frequency_unit)
            assertEquals(3,request.strength_weekly_count); assertEquals("MODERATE",request.strength_intensity)
            assertEquals(60,request.aerobic_low_minutes); assertEquals(90,request.aerobic_moderate_minutes); assertEquals(20,request.aerobic_high_minutes)
            assertEquals(setOf("strength_frequency_unit","strength_weekly_count","strength_intensity","aerobic_low_minutes","aerobic_moderate_minutes","aerobic_high_minutes"),
                com.google.gson.JsonParser.parseString(com.google.gson.Gson().toJson(request)).asJsonObject.keySet())
        }
        for (day in listOf(1,3,5,6)) compose.onNodeWithTag("strength-weekday-$day").performScrollTo().performClick()
        compose.onNodeWithText("주 7일").assertIsDisplayed()
        compose.onNodeWithText("주 5일 이상으로 저장해요", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(5,state.exerciseHabitsRequest().strength_weekly_count) }
        capture("02-a16-seven-days")
        for (day in 0..6) compose.onNodeWithTag("strength-weekday-$day").performScrollTo().performClick()
        compose.onNodeWithText("주 0일").assertIsDisplayed()
        compose.runOnIdle { assertNull(state.exerciseHabitsRequest().strength_intensity) }
        compose.onNodeWithText("고강도").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsEnabled()
        capture("03-aerobic-unchanged")
    }
    @Test fun restoredCountDoesNotInventWeekdaysOrEraseSavedExercise() {
        val state = OnboardingState().apply { strengthWeekdays.value = null; strengthWeeklyCount.value = 3; strengthIntensity.value = "LIGHT" }
        compose.setContent { Page { A08ExerciseScreen(state, rememberCoroutineScope(), { true }) } }
        for (day in 0..6) compose.onNodeWithTag("strength-weekday-$day").assertIsOff()
        compose.onNodeWithText("주 3일").assertIsDisplayed()
        compose.runOnIdle { assertEquals(3,state.exerciseHabitsRequest().strength_weekly_count) }
        capture("04-a16-restored-count")
        compose.onNodeWithText("근력운동 안 함으로 변경").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0,state.exerciseHabitsRequest().strength_weekly_count); assertNull(state.exerciseHabitsRequest().strength_intensity) }
    }
    @Test fun narrowLargeTextRetainsAllSevenTouchTargetsAndFixedFooter() {
        val state = OnboardingState()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { Page { A08ExerciseScreen(state, rememberCoroutineScope(), { true }) } }
            }
        }
        for (day in 0..6) {
            val node = compose.onNodeWithTag("strength-weekday-$day").performScrollTo().assertIsDisplayed()
            node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(72.dp).performClick().assertIsOn()
            compose.onNodeWithText("다음으로").assertIsDisplayed()
        }
        capture("05-a16-large-text")
        compose.onNodeWithTag("strength-intensity-MODERATE").performScrollTo().performClick()
        compose.onNodeWithText("다음으로").assertIsEnabled()
    }
    private fun option(title: String, sensor: Boolean = false, done: Boolean = false) = ExerciseMissionOption(
        UUID.nameUUIDFromBytes(title.toByteArray()),title,"편안한 속도로 움직여요.",if(sensor) "SENSOR_WALKING_DURATION" else "CHECK",10,if(sensor) "분" else "걸음","WOOD","나뭇가지",done)
    @Test fun listUsesEqualNeutralBordersAndSingleLineNamesAndNavigates() {
        val options = listOf(option("제자리 걷기"),option("천천히 걷기",true),option("옆으로 한 걸음씩 움직이기"),option("의자에서 앉았다 일어서기"),option("벽 짚고 밀기",done=true))
        val state = CardHomeState().apply { exerciseMissionsToday.value = ExerciseMissionsTodayResponse(true,0,2,2,options) }
        compose.setContent { Box(Modifier.width(360.dp).fillMaxHeight()) { Page(MainTab.HOME) { ExerciseMissionListScreen(state,rememberCoroutineScope(),loadToday={}) } } }
        capture("06-extra-mission-list")
        compose.onNodeWithText("비버와 한 번 더,\n오늘의 틈새 운동").assertIsDisplayed()
        for (option in options) {
            val node = compose.onNodeWithTag("extra-mission-${option.catalog_entry_id}").performScrollTo()
            assertOneLine(option.title)
            val image = node.captureToImage().toPixelMap()
            // Inspect the straight left edge, away from the corner and text.
            val edge = image[0,image.height / 2]
            assertTrue("Neutral border: $edge", kotlin.math.abs(edge.red-edge.green)<.08f && kotlin.math.abs(edge.green-edge.blue)<.08f)
        }
        compose.onNodeWithTag("extra-mission-${options.last().catalog_entry_id}").assertIsNotEnabled()
        compose.onNodeWithText("비버와 한 번 더,\n오늘의 틈새 운동").performScrollTo()
        capture("06-extra-mission-list")
        compose.onNodeWithText("천천히 걷기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CardHomeStep.EXTRA_DETAIL,state.step.value); assertEquals(options[1],state.selectedExerciseOption.value) }
    }
    @Test fun sensorMeasurementUsesCompactTitleAndNeutralPanel() {
        val state = CardHomeState().apply { revealedCard.value = CardRevealResponse("qa-distance","SENSOR_RUNNING_DISTANCE","빠른 속도로 달리기","편한 속도로 달려요.","WOOD","유산소",200,"m","ACTIVE",fortune_text=null,lucky_location=null,line_text=null) }
        compose.setContent { Page { SensorMeasuringScreen(state,rememberCoroutineScope(),{},{},{}) } }
        capture("07-sensor-measurement")
        assertOneLine("빠른 속도로 달리기")
        compose.onNodeWithText("0 m").assertIsDisplayed()
        capture("07-sensor-measurement")
    }
}
