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
import com.tmtn.app.network.*
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsAndRecoveryUiTest {
    @get:Rule val compose = createComposeRule()
    private var hostingView: android.view.View? = null
    private val setting = NotificationSettingResponse("Asia/Seoul", listOf("07:00", "13:00", "21:00"), emptyList(), null, false, "2026-09-14T12:00:00Z")
    private val card = CardRevealResponse("offline-timer", "TIMER", "잠들기 전 편안하게 목과 어깨 풀어주기", "앉아서 어깨를 천천히 돌려요. 편안한 범위에서 움직여 주세요.", "EARTH", "생활 리듬", 5, "분", "PAUSED", fortune_text = null, lucky_location = null, line_text = null)

    @Composable private fun Stage(large: Boolean = false, content: @Composable () -> Unit) {
        val context = LocalContext.current
        val view = LocalView.current
        SideEffect { hostingView = view }
        val resolver = remember(context) {
            val config = android.content.res.Configuration(context.resources.configuration).apply { fontWeightAdjustment = 0 }
            androidx.compose.ui.text.font.createFontFamilyResolver(context.createConfigurationContext(config))
        }
        val density = LocalDensity.current
        CompositionLocalProvider(LocalFontFamilyResolver provides resolver, LocalDensity provides Density(density.density, if (large) 1.8f else density.fontScale)) {
            TMTNv1Theme { Box(Modifier.then(if (large) Modifier.width(320.dp) else Modifier.fillMaxWidth()).fillMaxHeight().background(LocalTmtnColors.current.background).testTag("settings-stage")) { content() } }
        }
    }

    @Test fun notificationSettingsShowActualOffStateAndOpenSchedule() {
        val state = ProfileState().apply { notificationSetting.value = setting }
        compose.setContent { Stage { NotificationSettingScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("꺼짐").assertIsDisplayed()
        compose.onNodeWithText("카드가 찾아올 시간").performScrollTo().assertIsDisplayed()
        capture("notification-settings")
        compose.onNodeWithText("생활시간 바꾸기").performClick()
        compose.runOnIdle { assertEquals(ProfileScreenKey.WAKE_SLEEP, state.screen.value) }
    }

    @Test fun scheduleUsesExistingPatchWithoutEnablingNotifications() {
        var submitted: NotificationSettingUpdateRequest? = null
        val api = proxy<ProfileApi> { method, args ->
            check(method == "updateNotificationSettings")
            submitted = args!![0] as NotificationSettingUpdateRequest
            retrofit2.Response.success(setting)
        }
        val state = ProfileState { api }.apply { notificationSetting.value = setting; screen.value = ProfileScreenKey.WAKE_SLEEP }
        compose.setContent { Stage { WakeSleepEditScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("기상 시간").assertIsDisplayed()
        capture("schedule")
        compose.onNodeWithText("이 시간으로 맞추기").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(setting.slots, submitted?.slots)
            assertNull(submitted?.enabled)
            assertEquals(false, state.notificationSetting.value?.enabled)
            assertEquals(ProfileScreenKey.NOTIFICATION, state.screen.value)
        }
    }

    @Test fun largeTimePickerRejectsInvalidHoursAndAcceptsValidTime() {
        val state = ProfileState().apply { notificationSetting.value = setting }
        compose.setContent { Stage(large = true) { WakeSleepEditScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("기상 시간").performScrollTo().performClick()
        compose.onNodeWithText("시 · 0–23").performTextReplacement("25")
        compose.onNodeWithText("이 시간으로").assertIsNotEnabled()
        compose.onNodeWithText("시 · 0–23").performTextReplacement("8")
        compose.onNodeWithText("이 시간으로").assertIsEnabled().performClick()
        compose.onNodeWithText("오전 8:00").assertExists()
        capture("schedule-large-320")
    }

    @Test fun timerRetryFailureKeepsPausedTimeAndOffersRetry() {
        val api = proxy<CardHomeApi> { method, _ ->
            check(method == "startChallenge")
            retrofit2.Response.error<Any>(503, okhttp3.ResponseBody.create(null, ""))
        }
        val state = CardHomeState { api }.apply {
            setId.value = "offline-timer-set"
            revealedCard.value = card; timerElapsedSeconds.value = 84; timerIsPaused.value = true
            step.value = CardHomeStep.CHALLENGE_TIMER_PAUSED
        }
        compose.setContent { Stage { CardHomeFlow(state, { true }, onStartSensorTracking = { _, _, _, _ -> }, onStopSensorTracking = {}, onOpenSettings = {}) } }
        compose.onNodeWithText("이어서 하기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(84, state.timerElapsedSeconds.value)
            assertTrue(state.timerIsPaused.value)
            assertEquals("PAUSED", state.revealedCard.value?.state)
            assertEquals(CardHomeStep.CHALLENGE_TIMER_PAUSED, state.step.value)
        }
        compose.onNodeWithText("이어서 하기").performScrollTo().assertIsEnabled()
        capture("timer-retry")
    }

    @Test fun longTimerInstructionsAndRestSheetRemainReachableAtLargeText() {
        val state = CardHomeState().apply { revealedCard.value = card.copy(state = "READY"); restDaysRemainingThisWeek.value = 2 }
        compose.setContent { Stage(large = true) {
            TimerStartScreen(state, rememberCoroutineScope())
            if (state.showRestDaySheet.value) RestDaySheetScreen(state, rememberCoroutineScope())
        } }
        compose.onNodeWithText("시작하기").performScrollTo().assertIsDisplayed()
        capture("timer-start-large-320")
        compose.runOnIdle { state.showRestDaySheet.value = true }
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("닫기").performScrollTo().assertIsDisplayed()
        capture("rest-large-320", "tmtn-sheet-dialog")
        compose.onNodeWithText("닫기").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun cardErrorReturnsHomeInsteadOfOfferingANoopAction() {
        val state = CardHomeState().apply { step.value = CardHomeStep.ERROR; errorMessage.value = "연결을 확인해 주세요." }
        compose.setContent { Stage { CardErrorScreen(state, rememberCoroutineScope()) } }
        compose.onNodeWithText("오늘은 직접 행동 고르기").assertDoesNotExist()
        compose.onNodeWithText("홈으로 돌아가기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CardHomeStep.HOME, state.step.value); assertNull(state.errorMessage.value) }
    }

    @Test fun failedRestSaveKeepsTheModalAndItsRetryAction() {
        val api = proxy<CardHomeApi> { method, _ ->
            check(method == "markRestDay")
            retrofit2.Response.error<Any>(503, okhttp3.ResponseBody.create(null, ""))
        }
        val state = CardHomeState(serviceDateProvider = { "2026-09-14" }, missionApiProvider = { api }).apply { showRestDaySheet.value = true }
        compose.setContent { Stage {
            if (state.showRestDaySheet.value) RestDaySheetScreen(state, rememberCoroutineScope())
        } }
        compose.onNodeWithText("오늘 쉬어가기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("오늘 쉬어가기").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertFalse(state.isTodayRestDay.value); assertEquals(2, state.restDaysRemainingThisWeek.value); assertNotNull(state.errorMessage.value) }
        compose.onNodeWithText("닫기").performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun deletedAccountLeavesForLoginAndHidesNavigationAtLargeText() {
        var immersive = false
        var leftForLogin = false
        val state = ProfileState().apply { screen.value = ProfileScreenKey.DELETE_DONE }
        compose.setContent { Stage(large = true) {
            ProfileFlow(onOpenDam = {}, onOpenSettings = {}, onLoggedOut = { leftForLogin = true },
                onSaveCsv = { _, _ -> }, onImmersiveChange = { immersive = it }, state = state, onLoad = {})
        } }
        compose.runOnIdle { assertTrue(immersive) }
        compose.onNodeWithText("로그인 화면으로").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(leftForLogin) }
    }

    private inline fun <reified T> proxy(crossinline block: (String, Array<out Any?>?) -> Any): T =
        java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> block(method.name, args) } as T

    private fun capture(name: String, tag: String = "settings-stage") {
        compose.mainClock.advanceTimeBy(800)
        // Platform dialog fade runs on the device clock, outside Compose's test clock.
        if (tag == "tmtn-sheet-dialog") android.os.SystemClock.sleep(450)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "settings-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            val bitmap = if (tag == "tmtn-sheet-dialog") InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                else compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            if (tag == "tmtn-sheet-dialog" && android.os.Build.VERSION.SDK_INT >= 30) {
                val root = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
                val close = compose.onNodeWithText("닫기").fetchSemanticsNode().boundsInWindow
                compose.runOnIdle {
                    val dialogRoot = android.view.inspector.WindowInspector.getGlobalWindowViews().first { it.hasWindowFocus() }
                    val screenOrigin = IntArray(2); val windowOrigin = IntArray(2)
                    dialogRoot.getLocationOnScreen(screenOrigin); dialogRoot.getLocationInWindow(windowOrigin)
                    val offset = screenOrigin[1] - windowOrigin[1]
                    val nav = hostingView?.rootWindowInsets?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
                    val safeBottom = bitmap.height - nav
                    File(dir, "sheet-bounds.txt").writeText("root=$root\nclose=$close\nwindowOffsetY=$offset\nsafeBottom=$safeBottom\n")
                    assertTrue("Close action overlaps the system navigation region", close.bottom + offset <= safeBottom)
                }
            }
        }
    }
}
