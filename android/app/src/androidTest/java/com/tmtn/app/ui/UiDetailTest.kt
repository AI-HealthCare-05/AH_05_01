package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** UI fixtures only: no account creation, save requests, or changes to the user's data. */
class UiDetailTest {
    @get:Rule val compose = createComposeRule()

    private fun onboarding() = OnboardingState().apply {
        birthYear.value = 1993; birthMonth.value = 7; gender.value = "MALE"
        heightCm.value = "173"; weightKg.value = "73"
        strengthWeeklyCount.value = 2; strengthIntensity.value = "MODERATE"
        aerobicLowMinutes.value = 60; aerobicModerateMinutes.value = 90
    }
    private fun profile() = ProfileState().apply {
        userInfo.value = UserInfoResponse(0L, "틈튼", "틈튼", "beaver@example.com", null, 1993, 7, "MALE", null, "2026-09-10T00:00:00Z")
        healthInput.value = HealthInputResponse("fixture", "2026-09-10", mapOf("height_cm" to 173, "weight_kg" to 73), emptyMap(), "MANUAL", "2026-09-10")
        exerciseHabits.value = ExerciseHabitsResponse("fixture", 2, "MODERATE", 60, 90, 0, "2026-09-10")
    }
    @Composable private fun Stage(content: @Composable () -> Unit) {
        TMTNv1Theme { Box(Modifier.fillMaxSize().background(LocalTmtnColors.current.background).testTag("detail-stage")) { content() } }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-detail-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onNodeWithTag("detail-stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun loginAndSignupExplainInvalidInputsBeforeAnyRequest() {
        val state = onboarding()
        var signup by mutableStateOf(false)
        compose.setContent { Stage {
            if (signup) A03SignupScreen(state, rememberCoroutineScope())
            else A05LoginScreen(state, rememberCoroutineScope(), {})
        } }
        compose.onNode(hasText("로그인") and hasClickAction()).assertIsNotEnabled()
        capture("15-login")
        compose.runOnIdle { signup = true }
        compose.onNode(hasText("이메일") and hasSetTextAction()).performTextInput("invalid-address")
        compose.onNodeWithText("이메일 형식이 올바르지 않아요. 예: name@example.com").assertIsDisplayed()
        compose.onNodeWithText("인증번호 받기").performScrollTo().assertIsNotEnabled()
        capture("16-signup-validation")
    }

    @Test fun requiredProfileProgressAndResetUnavailableStateAreTruthful() {
        val state = onboarding()
        var reset by mutableStateOf(false)
        compose.setContent { Stage {
            if (reset) A12PasswordResetRequestScreen(state)
            else A07ProfileScreen(state, rememberCoroutineScope())
        } }
        compose.onNodeWithText("1 / 2단계 · 신체 정보", useUnmergedTree = true).assertIsDisplayed()
        capture("17-required-profile")
        compose.runOnIdle { reset = true }
        compose.onNodeWithText("재설정 링크 받기").assertDoesNotExist()
        compose.onNodeWithText("로그인으로 돌아가기").performClick()
        compose.runOnIdle { assertEquals(OnboardingStep.A05_LOGIN, state.step.value) }
        capture("18-reset-unavailable")
    }

    @Test fun signupProgressStaysAboveTheFormAndAppearsOnEveryAccountStep() {
        val state = onboarding()
        var page by mutableIntStateOf(1)
        compose.setContent { Stage {
            val scope = rememberCoroutineScope()
            when (page) {
                1 -> A03SignupScreen(state, scope)
                2 -> A04VerifyScreen(state, scope)
                else -> A06ConsentScreen(state, scope)
            }
        } }
        compose.onNodeWithText("1 / 3단계 · 계정 정보", useUnmergedTree = true).assertIsDisplayed()
        val top = compose.onNodeWithTag("signup-progress").fetchSemanticsNode().boundsInRoot.top
        capture("01-signup")
        compose.onNodeWithText("인증번호 받기").performScrollTo()
        assertEquals(top, compose.onNodeWithTag("signup-progress").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { state.email.value = "beaver@example.com"; page = 2 }
        compose.onNodeWithText("2 / 3단계 · 이메일 인증", useUnmergedTree = true).assertIsDisplayed()
        capture("02-verification")
        compose.runOnIdle { page = 3 }
        compose.onNodeWithText("3 / 3단계 · 약관 동의", useUnmergedTree = true).assertIsDisplayed()
        capture("03-consent")
    }

    @Test fun signupAndProfileUseEqualIntensityChoicesAndAllFrequencyOptions() {
        val signup = onboarding()
        val profile = profile()
        var editing by mutableStateOf(false)
        compose.setContent { Stage {
            if (editing) ExerciseEditScreen(profile, rememberCoroutineScope(), {})
            else A08ExerciseScreen(signup, rememberCoroutineScope(), { false })
        } }
        fun verify() {
            compose.onNodeWithTag("strength-count-5").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            compose.onNodeWithTag("strength-intensity-MODERATE").performScrollTo().assertIsDisplayed()
            val bounds = listOf("LIGHT", "MODERATE", "HARD").map {
                compose.onNodeWithTag("strength-intensity-$it").fetchSemanticsNode().boundsInRoot
            }
            bounds.forEach { assertEquals(bounds.first().height, it.height, 1f); assertEquals(bounds.first().width, it.width, 1f) }
            compose.onNodeWithText("10~12회면\n힘들어요", useUnmergedTree = true).assertIsDisplayed()
        }
        verify()
        capture("04-signup-exercise")
        compose.runOnIdle { editing = true }
        verify()
        capture("05-profile-exercise")
        compose.onNodeWithText("숨이 조금 차요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("빠르게 걷기 · 자전거").assertIsDisplayed()
        capture("06-aerobic")
    }

    @Test fun exerciseChoicesRemainReadableAt320dpAndTwoHundredPercentText() {
        val state = profile()
        compose.setContent { Stage {
            CompositionLocalProvider(LocalTmtnTextScale provides 2f) {
                Box(Modifier.width(320.dp)) { ExerciseEditScreen(state, rememberCoroutineScope(), {}) }
            }
        } }
        compose.onNodeWithTag("strength-count-5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("strength-intensity-HARD").performScrollTo().assertIsDisplayed()
        assertTextFits()
        capture("07-large-text")
        compose.onNodeWithText("달리기 · 등산").performScrollTo().assertIsDisplayed()
        assertTextFits()
    }

    @Test fun requiredBadgeHasSpaceAndSkipIsNotDuplicated() {
        compose.setContent { Stage { A16PermissionsScreen(onboarding(), {}) } }
        val label = compose.onNodeWithText("신체 활동").fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithText("필수").fetchSemanticsNode().boundsInRoot
        assertTrue("Required badge must be separate from its heading", badge.left > label.right + 10f)
        compose.onAllNodesWithText("나중에 하기").assertCountEquals(1)
        capture("08-permissions")
    }

    @Test fun completionUsesUnframedLargerCharacterAndReadableSummary() {
        compose.setContent { Stage { A15CompleteScreen(onboarding(), {}) } }
        compose.onNodeWithContentDescription("손 흔들며 인사하는 비버").assertIsDisplayed()
        compose.onNodeWithText("준비가 끝났어요").assertIsDisplayed()
        capture("09-complete")
    }

    @Test fun homeKeepsIndexDatesAndDamAsDistinctSections() {
        val today = LocalDate.now()
        val state = CardHomeState().apply {
            tuntunIndexValue.value = 77; tuntunIndexPresentationValue.value = 77.3
            companionStage.value = 2
            recentWeek.value = (6L downTo 0L).map { offset ->
                CalendarDayItem(today.minusDays(offset).toString(), if (offset % 2L == 0L) "COMPLETED" else "REST")
            }
        }
        compose.setContent { Stage {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TmtnIndexSummaryCard(state)
                RecentSummaryListCard(state)
            }
        } }
        compose.onNodeWithContentDescription("틈튼지수 77.3점").assertIsDisplayed()
        compose.onNodeWithText("최근 7일").assertIsDisplayed()
        compose.onNodeWithText("댐 2단계").performScrollTo().assertIsDisplayed()
        capture("10-home-sections")
    }

    @Test fun profileBodyEditorAndMenusAreConsistentWithoutDuplicateDamEntry() {
        val state = profile()
        var editing by mutableStateOf(true)
        compose.setContent { Stage {
            if (editing) HealthEditScreen(state, rememberCoroutineScope(), { editing = false })
            else ProfileHomeScreen(state, {}, {})
        } }
        compose.onNodeWithText("생년월").assertIsDisplayed()
        compose.onNodeWithText("1993년 7월").assertIsDisplayed()
        capture("11-body-information")
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("캐릭터 · 댐").assertDoesNotExist()
        compose.onNodeWithText("신체 정보").assertIsDisplayed()
        capture("12-profile")
    }

    private fun assertTextFits() {
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        repeat(texts.fetchSemanticsNodes().size) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { layout ->
                assertFalse("Height clipped: ${layout.layoutInput.text.text}", layout.didOverflowHeight)
                repeat(layout.lineCount) { line ->
                    assertTrue("Right clipped: ${layout.layoutInput.text.text}", layout.getLineRight(line) <= layout.layoutInput.constraints.maxWidth + 1f)
                    assertTrue("Left clipped: ${layout.layoutInput.text.text}", layout.getLineLeft(line) >= -1f)
                }
            }
        }
    }
}
