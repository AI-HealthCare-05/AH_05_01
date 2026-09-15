package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.audio.TmtnAudio
import com.tmtn.app.network.ProfileApi
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.theme.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsAccessibilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val original = Triple(AccessibilitySettingsHolder.largeControlsEnabled.value, AccessibilitySettingsHolder.seniorMode.value, AccessibilitySettingsHolder.textScaleHint.value)
    private val originalMotion = AccessibilitySettingsHolder.reducedMotion.value
    private val originalEffects = TmtnAudio.effectsEnabled
    private val setting = AccessibilityResponse(false, false, "NORMAL", false, "2026-09-14T00:00:00Z")
    @After fun restore() {
        AccessibilitySettingsHolder.apply(original.first, original.second, original.third)
        AccessibilitySettingsHolder.reducedMotion.value = originalMotion
        TmtnAudio.setEffects(originalEffects)
    }

    @Composable private fun Stage(large: Boolean = false, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val resolver = remember(context) {
            val config = android.content.res.Configuration(context.resources.configuration).apply { fontWeightAdjustment = 0 }
            androidx.compose.ui.text.font.createFontFamilyResolver(context.createConfigurationContext(config))
        }
        CompositionLocalProvider(LocalFontFamilyResolver provides resolver, LocalDensity provides Density(density.density, if (large) 1.8f else 1f)) {
            TMTNv1Theme { Box(Modifier.width(320.dp).fillMaxHeight().background(LocalTmtnColors.current.background).testTag("settings-adaptive")) { content() } }
        }
    }

    @Test fun allTextSizeChoicesFitAndRemainSelectableAtLargeFont() {
        var submitted: AccessibilityUpdateRequest? = null
        val state = ProfileState { api { request -> submitted = request; setting.copy(preferred_text_scale_hint = request.preferred_text_scale_hint) } }.apply { accessibility.value = setting }
        AccessibilitySettingsHolder.apply(false, false, "NORMAL")
        compose.setContent { Stage(large = true) { AccessibilityScreen(state, rememberCoroutineScope(), {}) } }
        val stage = compose.onNodeWithTag("settings-adaptive").fetchSemanticsNode().boundsInRoot
        listOf("보통", "크게", "아주 크게").forEach { label ->
            val node = compose.onNodeWithText(label)
            node.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Clipped choice $label", bounds.left >= stage.left && bounds.right <= stage.right)
        }
        capture("accessibility-large")
        compose.onNodeWithText("아주 크게").performClick()
        compose.onNodeWithText("아주 크게").performScrollTo().assertIsSelected()
        compose.runOnIdle { assertEquals("EXTRA_LARGE", submitted?.preferred_text_scale_hint); assertNull(submitted?.large_controls) }
    }

    @Test fun switchLabelIsOneLabeledToggleAndSavesOnlyItsPreference() {
        var calls = 0
        var submitted: AccessibilityUpdateRequest? = null
        val state = ProfileState { api { request -> calls++; submitted = request; setting.copy(large_controls = request.large_controls ?: false) } }.apply { accessibility.value = setting }
        compose.setContent { Stage { AccessibilityScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNode(hasText("큰 버튼") and isToggleable()).performScrollTo().assertIsOff().performClick()
        compose.onNode(hasText("큰 버튼") and isToggleable()).assertIsOn()
        compose.runOnIdle { assertEquals(1, calls); assertEquals(true, submitted?.large_controls); assertNull(submitted?.reduced_motion); assertNull(submitted?.preferred_text_scale_hint) }
    }

    @Test fun soundPreviewsStackAtLargeFontAndKeepLargeTargets() {
        AccessibilitySettingsHolder.apply(true, false, "NORMAL")
        TmtnAudio.setEffects(true)
        compose.setContent { Stage(large = true) { SoundSettingsScreen({}) } }
        val positions = listOf("나무 톡", "종이 사각", "재료 획득").map { label ->
            val node = compose.onNodeWithText(label)
            node.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(60.dp)
            node.fetchSemanticsNode().size.width
        }
        assertEquals(1, positions.distinct().size)
        capture("sound-large")
        compose.onNode(hasText("홈 배경음") and isToggleable()).performScrollTo().assertIsDisplayed()
    }

    private fun api(update: (AccessibilityUpdateRequest) -> AccessibilityResponse): ProfileApi =
        java.lang.reflect.Proxy.newProxyInstance(ProfileApi::class.java.classLoader, arrayOf(ProfileApi::class.java)) { _, method, args ->
            check(method.name == "updateAccessibility")
            retrofit2.Response.success(update(args!![0] as AccessibilityUpdateRequest))
        } as ProfileApi

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "settings-adaptive-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onNodeWithTag("settings-adaptive").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
