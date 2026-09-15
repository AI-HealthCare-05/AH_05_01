package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.ProfileApi
import com.tmtn.app.network.model.ConsentResponse
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.theme.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Fake APIs only: no account, consent or stored records are changed. */
class ProfileConfirmationUiTest {
    @get:Rule val compose = createComposeRule()
    private val originalLargeControls = AccessibilitySettingsHolder.largeControlsEnabled.value
    @After fun restorePreferences() { AccessibilitySettingsHolder.largeControlsEnabled.value = originalLargeControls }

    @Composable private fun Stage(large: Boolean = false, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.8f else 1f)) {
            TMTNv1Theme { Box(Modifier.fillMaxSize()) { content() } }
        }
    }
    @Composable private fun Flow(state: ProfileState) {
        ProfileFlow(onOpenDam = {}, onOpenSettings = {}, onLoggedOut = {}, onSaveCsv = { _, _ -> }, state = state, onLoad = {})
    }

    @Test fun withdrawalFailureKeepsConsentAndOneRecoverableDialog() {
        var attempts = 0
        val state = ProfileState { failingApi("withdrawConsent") { attempts++ } }.apply {
            screen.value = ProfileScreenKey.CONSENT
            consents.value = listOf(ConsentResponse("offline", "NOTIFICATION", "v1", "AGREED", "2026-09-14T00:00:00Z", null))
            pendingWithdrawal.value = "NOTIFICATION"
        }
        compose.setContent { Stage { Flow(state) } }
        compose.onNodeWithText("철회하기").assertHeightIsAtLeast(48.dp).performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        val error = state.errorMessage.value!!
        compose.onAllNodesWithText(error).assertCountEquals(1)
        compose.onNodeWithText(error).assertIsDisplayed()
        compose.runOnIdle { assertEquals("AGREED", state.consents.value.single().status); assertEquals(1, attempts) }
        capture("consent-retry")
        compose.onNodeWithText("철회하기").performClick()
        compose.runOnIdle { assertEquals(2, attempts) }
        compose.onNodeWithText("취소").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertNull(state.errorMessage.value); assertFalse(state.confirmationOwnsFeedback) }
    }

    @Test fun recordDeletionFailureStaysInPlaceUntilCancelled() {
        var attempts = 0
        val state = ProfileState { failingApi("deleteRecordsOnly") { attempts++ } }.apply { screen.value = ProfileScreenKey.PRIVACY_DATA }
        compose.setContent { Stage { Flow(state) } }
        compose.onNodeWithText("기록만 삭제").performScrollTo().performClick()
        compose.onNodeWithText("기록 지우기").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("기록 지우기").assertIsEnabled()
        compose.onAllNodesWithText(state.errorMessage.value!!).assertCountEquals(1)
        compose.runOnIdle { assertEquals(1, attempts); assertFalse(state.recordsDeletedDone.value); assertTrue(state.confirmRecordDeletion.value) }
        compose.onNodeWithText("그만두기").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("기록만 삭제").assertIsDisplayed()
    }

    @Test fun pendingConfirmationKeepsOneModalAndDisablesBothActions() {
        val state = ProfileState { failingApi("No request expected") {} }.apply {
            screen.value = ProfileScreenKey.PRIVACY_DATA
            confirmRecordDeletion.value = true; isLoading.value = true
        }
        compose.setContent { Stage { Flow(state) } }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("기록 지우기").assertIsNotEnabled()
        compose.onNodeWithText("그만두기").assertIsNotEnabled()
        compose.onNodeWithText("변경 내용을 확인하고 있어요.").assertIsDisplayed()
        compose.runOnIdle { state.isLoading.value = false }
        compose.onNodeWithText("그만두기").assertIsEnabled().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun logoutAtLargeTextHasReadableMessageAndLargeCancelTarget() {
        var visible by mutableStateOf(true)
        var confirms = 0
        AccessibilitySettingsHolder.largeControlsEnabled.value = true
        compose.setContent { Stage(large = true) {
            if (visible) LogoutConfirmDialog({ confirms++ }, { visible = false })
        } }
        compose.onNodeWithText("기록은 그대로 남습니다. 같은 계정으로 다시 로그인하면 이어서 볼 수 있습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("로그아웃").assertHeightIsAtLeast(60.dp).assertIsDisplayed()
        compose.onNodeWithText("그만두기").assertHeightIsAtLeast(60.dp).assertIsDisplayed()
        capture("logout-large")
        compose.onNodeWithText("그만두기").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, confirms) }
    }

    private fun failingApi(expected: String, onCall: () -> Unit): ProfileApi =
        java.lang.reflect.Proxy.newProxyInstance(ProfileApi::class.java.classLoader, arrayOf(ProfileApi::class.java)) { _, method, _ ->
            check(method.name == expected) { "Unexpected API ${method.name}" }
            onCall()
            retrofit2.Response.error<Any>(503, okhttp3.ResponseBody.create(null, ""))
        } as ProfileApi

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(800)
        android.os.SystemClock.sleep(400) // Native window entry animation runs outside the Compose clock.
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "confirmation-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
