package com.tmtn.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.nav.BottomNavBar
import com.tmtn.app.ui.nav.MainTab
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnCheckRow
import com.tmtn.app.ui.theme.AccessibilitySettingsHolder
import com.tmtn.app.ui.theme.LocalTmtnTextScale
import com.tmtn.app.ui.theme.TMTNv1Theme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UiPolishTest {
    @get:Rule val compose = createComposeRule()

    @Test fun consentLinkIsIndependentOfCheckboxAtLargeText() {
        val checked = mutableStateOf(false)
        var toggles = 0
        var views = 0
        compose.setContent {
            TMTNv1Theme {
                CompositionLocalProvider(LocalTmtnTextScale provides 1.3f) {
                    Box(Modifier.width(280.dp).testTag("consent")) {
                        TmtnCheckRow("[필수] 건강정보 수집 · 이용 동의", checked.value,
                            { checked.value = it; toggles++ }, onViewClick = { views++ })
                    }
                }
            }
        }
        compose.onNodeWithText("[필수] 건강정보 수집 · 이용 동의").performClick().assertIsOn()
        compose.onNodeWithText("보기").performClick()
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(1, views); assertTrue(checked.value) }
        val parent = compose.onNodeWithTag("consent").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("[필수] 건강정보 수집 · 이용 동의", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(label.top >= parent.top && label.bottom <= parent.bottom && label.right <= parent.right)
    }

    @Test fun largeTextButtonGrowsWithoutClippingAndReceivesOneClick() {
        var count = 0
        compose.setContent {
            TMTNv1Theme {
                CompositionLocalProvider(LocalTmtnTextScale provides 1.3f) {
                    Box(Modifier.width(220.dp)) {
                        TmtnPrimaryButton("고른 미션 이어서 하기", { count++ }, modifier = Modifier.testTag("action"))
                    }
                }
            }
        }
        val parent = compose.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("고른 미션 이어서 하기", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(label.top >= parent.top && label.bottom <= parent.bottom)
        assertTrue(label.left >= parent.left && label.right <= parent.right)
        compose.onNodeWithText("고른 미션 이어서 하기").performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun fiveTabsFit320dpAndExposeSelection() {
        val selected = mutableStateOf(MainTab.HOME)
        compose.setContent {
            TMTNv1Theme {
                CompositionLocalProvider(LocalTmtnTextScale provides 1.3f) {
                    Box(Modifier.width(320.dp).testTag("navigation")) {
                        BottomNavBar(selected.value) { selected.value = it }
                    }
                }
            }
        }
        val parent = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        listOf("홈", "기록", "틈튼지수", "댐", "내 정보").forEach {
            val label = compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
            assertTrue("$it overflows", label.left >= parent.left && label.right <= parent.right)
        }
        compose.onNodeWithText("내 정보").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(MainTab.MY, selected.value) }
    }

    @Test fun disabledActionCannotBeSubmittedAndExplainsWhy() {
        var count = 0
        compose.setContent {
            TMTNv1Theme {
                TmtnPrimaryButton("동의하고 가입 완료", { count++ }, enabled = false, disabledReason = "필수 항목을 확인해 주세요.")
            }
        }
        compose.onNodeWithText("동의하고 가입 완료").assertIsNotEnabled()
        compose.onNodeWithText("필수 항목을 확인해 주세요.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, count) }
    }

    @Test fun reducedMotionDoesNotDelayButtonFeedback() {
        var count = 0
        AccessibilitySettingsHolder.reducedMotion.value = true
        try {
            compose.setContent { TMTNv1Theme { TmtnPrimaryButton("시작하기", { count++ }) } }
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("시작하기").performClick()
            compose.runOnIdle { assertEquals(1, count) }
        } finally {
            compose.mainClock.autoAdvance = true
            AccessibilitySettingsHolder.reducedMotion.value = false
        }
    }

    @Test fun tabsRespondWhileSelectionIndicatorIsMoving() {
        val selected = mutableStateOf(MainTab.HOME)
        val visits = mutableListOf<MainTab>()
        compose.setContent {
            TMTNv1Theme {
                BottomNavBar(selected.value) { selected.value = it; visits.add(it) }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            for (tab in listOf(MainTab.RECORD, MainTab.DAM, MainTab.MY, MainTab.HOME)) {
                compose.onNodeWithText(tab.label).performTouchInput { click() }
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithText(tab.label).assertIsSelected()
            }
            compose.runOnIdle {
                assertEquals(listOf(MainTab.RECORD, MainTab.DAM, MainTab.MY, MainTab.HOME), visits)
                assertEquals(MainTab.HOME, selected.value)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
