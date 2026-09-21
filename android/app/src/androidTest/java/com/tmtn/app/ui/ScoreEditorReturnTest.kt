package com.tmtn.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScoreEditorReturnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editorToolbarReturnsToScoreCaller() {
        var returned = 0
        compose.setContent { TMTNv1Theme {
            ProfileFlow({}, {}, {}, { _, _ -> }, initialScreen = ProfileScreenKey.HEALTH,
                onBackToOrigin = { returned++ }, onLoad = {})
        } }
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.runOnIdle { assertEquals(1, returned) }
    }

    @Test fun successfulExerciseSaveReturnsToScoreCaller() {
        val state = ProfileState().apply { screen.value = ProfileScreenKey.EXERCISE }
        var returned = 0
        compose.setContent { TMTNv1Theme {
            ProfileFlow({}, {}, {}, { _, _ -> }, initialScreen = ProfileScreenKey.EXERCISE,
                onBackToOrigin = { returned++ }, state = state, onLoad = {})
        } }
        // This is the existing successful API-save signal. No user data is submitted by the test.
        compose.runOnIdle { state.screen.value = ProfileScreenKey.HOME }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, returned) }
    }
}
