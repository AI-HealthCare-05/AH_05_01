package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.tmtn.app.network.model.UserInfoResponse
import com.tmtn.app.ui.profile.AccountDeleteReauthScreen
import com.tmtn.app.ui.profile.PasswordChangeScreen
import com.tmtn.app.ui.profile.ProfileState
import com.tmtn.app.ui.theme.TMTNv1Theme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** 가짜 계정으로 화면만 확인한다. 계정 삭제나 외부 인증 요청은 실행하지 않는다. */
class GoogleAccountReauthUiTest {
    @get:Rule val compose = createComposeRule()
    private fun state() = ProfileState().apply {
        userInfo.value = Gson().fromJson(
            """{"id":1,"email":"qa@example.com","created_at":"2026-09-16","requires_google_reauth":true}""",
            UserInfoResponse::class.java,
        )
    }

    @Composable private fun Stage(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
            TMTNv1Theme { Box(Modifier.width(320.dp).fillMaxHeight()) { content() } }
        }
    }

    @Test fun googleDeletionRequiresConfirmationWithoutAnImpossiblePasswordField() {
        val state = state()
        compose.setContent { Stage { AccountDeleteReauthScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("비밀번호", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Google 확인 후 계정 삭제").assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Google 확인 후 계정 삭제").assertIsEnabled().assertIsDisplayed()
        capture("google-delete-large")
        compose.runOnIdle { state.googleReauthInProgress.value = true }
        compose.onNodeWithText("Google 확인 후 계정 삭제").assertIsNotEnabled()
    }

    @Test fun googleAccountCanEnterItsFirstPasswordAtLargeTextSize() {
        val state = state()
        compose.setContent { Stage { PasswordChangeScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("지금 비밀번호").assertDoesNotExist()
        compose.onNodeWithText("새 비밀번호", substring = false).performScrollTo().performTextInput("New-Password1!")
        compose.onNodeWithText("새 비밀번호 확인").performScrollTo().performTextInput("New-Password1!")
        compose.onNodeWithText("Google 확인 후 비밀번호 설정").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("google-password-large")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
