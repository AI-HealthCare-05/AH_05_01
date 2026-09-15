package com.tmtn.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.ProfileApi
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

class HelpAndExportUiTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Stage(large: Boolean = true, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.8f else 1f)) {
            TMTNv1Theme { Box(Modifier.width(320.dp).height(590.dp).testTag("help-stage")) { content() } }
        }
    }

    @Test fun largeHelpKeepsTheContactActionVisibleAndChoicesInsideThePage() {
        var contacts = 0
        compose.setContent { Stage { HelpDetailScreen({}, { contacts++ }) } }
        compose.onNodeWithText("문의 남기기").assertIsDisplayed()
        val frame = compose.onNodeWithTag("help-stage").fetchSemanticsNode().boundsInRoot
        listOf("도움이 됐어요", "잘 모르겠어요").forEach { label ->
            val choice = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val bounds = choice.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= frame.left && bounds.right <= frame.right)
        }
        compose.onNodeWithText("문의 남기기").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, contacts) }
    }

    @Test fun inquiryDeviceInformationUsesItsWholeLabeledRowAndKeepsSendReachable() {
        val state = ProfileState()
        compose.setContent { Stage { InquiryScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("기기 정보 함께 보내기").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("보내기").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { state.inquirySubmitted.value = true }
        compose.onNodeWithText("닫기").performScrollTo().assertIsDisplayed()
    }

    @Test fun aNewInquiryOpensAnEmptyFormAfterThePreviousAcknowledgement() {
        val state = ProfileState().apply { screen.value = ProfileScreenKey.INQUIRY; inquirySubmitted.value = true }
        compose.setContent { Stage(large = false) {
            ProfileFlow({}, {}, {}, { _, _ -> }, state = state, onLoad = {})
        } }
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("문의 남기기").performClick()
        compose.onNodeWithText("무엇에 대한 문의인가요?").assertIsDisplayed()
        compose.onNodeWithText("보내기").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertFalse(state.inquirySubmitted.value) }
    }

    @Test fun exportOnlyShowsSavedAfterTheFileCallbackSucceeds() {
        val api = java.lang.reflect.Proxy.newProxyInstance(ProfileApi::class.java.classLoader, arrayOf(ProfileApi::class.java)) { _, method, _ ->
            check(method.name == "exportMyData")
            Response.success("date,status\n".toResponseBody())
        } as ProfileApi
        val state = ProfileState { api }
        var failWrite = true
        var saved = 0
        compose.setContent { Stage(large = false) {
            ExportDataScreen(state, rememberCoroutineScope(), {}, { _, _ ->
                if (failWrite) error("test disk unavailable") else saved++
            })
        } }
        compose.onNodeWithText("CSV 파일 받기").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, saved); assertNotNull(state.errorMessage.value); failWrite = false }
        compose.onNodeWithText("다운로드 폴더에 CSV 파일을 저장했어요.").assertDoesNotExist()
        compose.onNodeWithText("CSV 파일 받기").performClick()
        compose.onNodeWithText("다운로드 폴더에 CSV 파일을 저장했어요.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saved) }
    }
}
