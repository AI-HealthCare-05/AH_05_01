package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.ui.common.*
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Offline UI fixtures. No API calls, preference writes or account operations. */
class DesignSystemUiTest {
    @get:Rule val compose = createComposeRule()
    private val previousSenior = AccessibilitySettingsHolder.seniorMode.value
    private val previousReduced = AccessibilitySettingsHolder.reducedMotion.value
    @After fun restore() {
        AccessibilitySettingsHolder.seniorMode.value = previousSenior
        AccessibilitySettingsHolder.reducedMotion.value = previousReduced
    }

    @Composable private fun Stage(content: @Composable ColumnScope.() -> Unit) {
        TMTNv1Theme {
            Column(Modifier.fillMaxSize().background(LocalTmtnColors.current.background)
                .testTag("design-stage").verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }

    @Test fun loadingRetainsLabelAndBlocksDuplicateActionThenRecovers() {
        var loading by mutableStateOf(true)
        var clicks = 0
        AccessibilitySettingsHolder.reducedMotion.value = true
        compose.setContent { Stage {
            TmtnPrimaryButton("동의하고 가입 완료", { clicks++ }, loading = loading)
        } }
        val button = compose.onNode(hasText("동의하고 가입 완료") and hasClickAction())
        button.assertIsNotEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "처리 중"))
        button.performClick()
        compose.runOnIdle { assertEquals(0, clicks); loading = false }
        button.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun inputErrorIsReadableAndClearsWithoutLosingTheValue() {
        var error by mutableStateOf<String?>("이메일 형식을 확인해 주세요.")
        compose.setContent { Stage {
            TmtnTextField("beaver", {}, "이메일", errorMessage = error, supportingText = "인증번호를 받을 주소")
        } }
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "이메일 형식을 확인해 주세요."))
        compose.onNodeWithText("이메일 형식을 확인해 주세요.").assertIsDisplayed()
        compose.runOnIdle { error = null }
        compose.onNodeWithText("인증번호를 받을 주소").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("beaver")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test fun highContrastReachesMaterialFieldsAndCustomComponents() {
        var materialMuted = Color.Unspecified
        var materialBorder = Color.Unspecified
        var customMuted = Color.Unspecified
        var customAccent = Color.Unspecified
        var materialAccent = Color.Unspecified
        compose.setContent { TMTNv1Theme {
            materialMuted = MaterialTheme.colorScheme.onSurfaceVariant
            materialBorder = MaterialTheme.colorScheme.outlineVariant
            customMuted = LocalTmtnColors.current.onSurfaceVariant
            customAccent = LocalTmtnColors.current.secondary
            materialAccent = MaterialTheme.colorScheme.secondary
        } }
        compose.runOnIdle { AccessibilitySettingsHolder.seniorMode.value = true }
        compose.runOnIdle {
            assertEquals(ColorOnSurface, materialMuted)
            assertEquals(customMuted, materialMuted)
            assertEquals(ColorOutline, materialBorder)
            assertEquals(ColorOnSurface, customAccent)
            assertEquals(customAccent, materialAccent)
        }
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun keyboardActivationStaysStillAndFiresOnRelease() {
        val requester = FocusRequester()
        lateinit var inputMode: InputModeManager
        var clicks = 0
        compose.setContent {
            inputMode = LocalInputModeManager.current
            Stage { TmtnActionButton("계속하기", { clicks++ }, TmtnActionStyle.Primary,
                Modifier.focusRequester(requester).testTag("key-action")) }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard); requester.requestFocus() }
        val button = compose.onNodeWithTag("key-action")
        val original = button.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        button.performKeyInput { keyDown(androidx.compose.ui.input.key.Key.Enter) }
        compose.mainClock.advanceTimeBy(110)
        val held = button.fetchSemanticsNode().boundsInRoot
        assertEquals(original.width, held.width, .5f)
        assertEquals(original.height, held.height, .5f)
        compose.runOnIdle { assertEquals(0, clicks) }
        button.performKeyInput { keyUp(androidx.compose.ui.input.key.Key.Enter) }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.mainClock.autoAdvance = true
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun keyboardFocusAndSelectedChoicesRemainDistinct() {
        val requester = FocusRequester()
        lateinit var inputMode: InputModeManager
        var selected by mutableStateOf(false)
        compose.setContent {
            inputMode = LocalInputModeManager.current
            Stage {
            Text("공통 요소 · 상태 확인", style = TmtnType.title)
            TmtnActionButton("계속하기", {}, TmtnActionStyle.Primary, Modifier.focusRequester(requester).testTag("focus-action"))
            TmtnOutlinedButton("이전 단계", {})
            TmtnTonalButton("신체 정보 확인", {})
            TmtnIntensityCard("적당히", "10~12회면 힘들어요", selected, { selected = !selected }, Modifier.fillMaxWidth().testTag("choice"))
            TmtnTextField("beaver", {}, "이메일", errorMessage = "이메일 형식을 확인해 주세요.")
            TmtnSurfaceCard(TmtnSurfaceRole.Exercise) {
                Text("유산소 운동", style = TmtnType.bodyLarge)
                Text("유산소 묶음은 20dp, 강도 선택은 16dp", style = TmtnType.caption)
            }
            TmtnPrimaryButton("저장하기", {}, enabled = false, disabledReason = "입력한 값을 확인해 주세요.")
        } }
        compose.onNodeWithTag("choice").performClick().assertIsSelected()
        compose.runOnIdle {
            inputMode.requestInputMode(InputMode.Keyboard)
            requester.requestFocus()
        }
        compose.onNodeWithTag("focus-action").assertIsFocused().assertHeightIsAtLeast(52.dp)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "design-system-qa").apply { mkdirs() }
        File(dir, "components.png").outputStream().use {
            compose.onNodeWithTag("design-stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
