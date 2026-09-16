package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.CompanionResponse
import com.tmtn.app.network.model.MaterialItem
import com.tmtn.app.network.model.StageItem
import com.tmtn.app.ui.common.damRepairLabel
import com.tmtn.app.ui.common.damWaterDescription
import com.tmtn.app.ui.dam.DamHomeScreen
import com.tmtn.app.ui.dam.MaterialEncyclopediaScreen
import com.tmtn.app.ui.dam.StageGuideScreen
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class DamWaterStoryUiTest {
    @get:Rule val compose = createComposeRule()

    private fun companion(stage: Int = 0): CompanionResponse {
        val materials = listOf(0, 1, 15, 35, 70, 120)[stage]
        val next = listOf(5, 15, 35, 70, 120, null)[stage]
        val elements = listOf("WOOD", "FIRE", "EARTH", "METAL", "WATER")
        val names = listOf("나뭇가지", "받침돌", "다짐흙", "새잎", "물길")
        val domains = listOf("움직임·유산소", "근력", "생활리듬", "식사·기록", "수분")
        val thresholds = listOf(if (stage == 0) 5 else 1, 15, 35, 70, 120)
        return CompanionResponse(stage, materials, next, next?.minus(materials) ?: 0,
            elements.mapIndexed { i, element ->
                MaterialItem(element, names[i], domains[i], materials / 5 + if (i < materials % 5) 1 else 0)
            },
            thresholds.mapIndexed { i, threshold -> StageItem(i + 1, damRepairLabel(i + 1), threshold, stage > i) })
    }

    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "dam-water-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun everyPersistedStageHasItsOwnWaterStoryAndFullScene() {
        val stage = mutableStateOf(0)
        compose.setContent { TMTNv1Theme { DamHomeScreen(companion(stage.value), {}, {}, {}) } }
        for (number in 0..5) {
            compose.runOnIdle { stage.value = number }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("내 댐 ${number}단계")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, damWaterDescription(number)))
                .assertIsDisplayed()
            compose.onNodeWithText(damWaterDescription(number)).assertIsDisplayed()
            capture("dam-stage-$number")
        }
    }

    @Test fun previewingFinishedDamDoesNotChangeTheActualStageOrMaterials() {
        val original = companion(1)
        compose.setContent { TMTNv1Theme { StageGuideScreen(original, {}) } }
        compose.onNodeWithText("5단계").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("내 댐 5단계").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, original.current_stage); assertEquals(1, original.total_materials) }
        capture("stage-guide-five")
        compose.onNodeWithText("0단계").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("내 댐 0단계").assertIsDisplayed()
    }

    @Test fun stageGuideAtLargeTypeKeepsStageSelectionAndStoryReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                TMTNv1Theme { Box(Modifier.width(320.dp).fillMaxHeight()) { StageGuideScreen(companion(), {}) } }
            }
        }
        compose.onNodeWithText("5단계").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(damWaterDescription(5)).performScrollTo().assertIsDisplayed()
        capture("stage-guide-large-320")
    }

    @Test fun materialGuideKeepsTheFiveExistingActions() {
        var opened = ""
        compose.setContent { TMTNv1Theme { MaterialEncyclopediaScreen({}, { opened = it }) } }
        compose.onNodeWithText("작은 실천이, 댐의 한 조각.").assertIsDisplayed()
        capture("material-guide")
        compose.onNodeWithText("받침돌").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("FIRE", opened) }
        compose.onNodeWithText("새잎").performScrollTo().assertIsDisplayed()
    }
}
