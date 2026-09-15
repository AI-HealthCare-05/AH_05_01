package com.tmtn.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tmtn.app.ui.common.OnAppForeground
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ForegroundRefreshUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyActualAppReturnsRefreshAndAlwaysUseTheLatestCallback() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        var previousCalls = 0
        var currentCalls = 0
        val callback = mutableStateOf<() -> Unit>({ previousCalls++ })
        val show = mutableStateOf(true)
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (show.value) OnAppForeground(callback.value)
            }
        }
        compose.runOnIdle { assertEquals(0, previousCalls) }
        compose.runOnUiThread {
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle { assertEquals(0, previousCalls); callback.value = { currentCalls++ } }
        compose.waitForIdle()
        compose.runOnUiThread {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle { assertEquals(0, previousCalls); assertEquals(1, currentCalls); show.value = false }
        compose.waitForIdle()
        compose.runOnUiThread {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle { assertEquals(1, currentCalls) }
    }
}
