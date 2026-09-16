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
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class GoogleEntryUiTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Stage(large: Boolean = false, content: @Composable () -> Unit) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val resolver = remember(context) {
            val config = android.content.res.Configuration(context.resources.configuration).apply { fontWeightAdjustment = 0 }
            androidx.compose.ui.text.font.createFontFamilyResolver(context.createConfigurationContext(config))
        }
        CompositionLocalProvider(LocalFontFamilyResolver provides resolver,
            LocalDensity provides Density(density.density, if (large) 1.8f else 1f)) {
            TMTNv1Theme { Box(Modifier.then(if (large) Modifier.width(320.dp) else Modifier.fillMaxWidth())
                .fillMaxHeight().background(LocalTmtnColors.current.background).testTag("google-entry-stage")) { content() } }
        }
    }

    @Test fun absentClientIdShowsAnHonestEmailRouteWithoutOpeningGoogle() {
        var email = 0
        compose.setContent { Stage { AuthChoiceScreen({ fail("Not configured") }, { email++ }, {}, {}, googleConfigured = false) } }
        compose.onNodeWithText("Google로 계속하기").assertIsNotEnabled()
        compose.onNodeWithText("지금은 이메일로 계속할 수 있어요.").assertIsDisplayed()
        capture("auth-choice")
        compose.onNodeWithText("이메일로 시작하기").performClick()
        compose.runOnIdle { assertEquals(1, email) }
    }

    @Test fun providerChoiceAndReadingRemainReachableAtLargeText() {
        var google = 0
        compose.setContent { Stage(large = true) { AuthChoiceScreen({ google++ }, {}, {}, {}, googleConfigured = true) } }
        compose.onNodeWithText("Google로 계속하기").assertIsDisplayed().performClick()
        compose.onNodeWithText("이미 계정이 있어요").assertIsDisplayed()
        compose.onNodeWithText("작은 실천, 틈튼이와 함께해요.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, google) }
        capture("auth-choice-large")
    }

    @Test fun signupBackReturnsToTheSavedStoryInsteadOfRestartingIt() {
        compose.setContent { Stage { OnboardingFlow({}, systemSplashShown = true) } }
        compose.onNodeWithText("틈튼이 만나기").performClick()
        compose.onNodeWithText("어떤 댐인데?").performClick()
        compose.onNodeWithText("바로 시작할게").performClick()
        compose.onNodeWithText("이메일로 시작하기").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("내 댐 기억하기").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("바쁜 하루 사이에\n작은 틈이 생겼더라.").assertIsDisplayed()
    }

    @Test fun linkFailureStaysInTheSheetAndCanRetryOrChooseAnotherMethod() {
        var retries = 0
        var visible by mutableStateOf(true)
        compose.setContent { Stage(large = true) {
            if (visible) GoogleLinkDialog("member@example.test", false, "인터넷 연결을 확인해 주세요.", { retries++ }, { visible = false })
        } }
        compose.onNodeWithText("인터넷 연결을 확인해 주세요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("기존 계정에 연결하기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("다른 방법으로 로그인").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("기존 계정에 연결하기").assertDoesNotExist()
    }

    @Test fun emailLoginAlsoOffersGoogleWithoutRequiringEmailFields() {
        compose.setContent { Stage { A05LoginScreen(OnboardingState(), rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("Google로 계속하기").performScrollTo().assertIsDisplayed()
        capture("login-providers")
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "google-entry-qa").apply { mkdirs() }
        val bitmap = compose.onNodeWithTag("google-entry-stage").captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
