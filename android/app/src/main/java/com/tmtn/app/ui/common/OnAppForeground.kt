package com.tmtn.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Initial composition and transient permission dialogs do not count as an app return. */
@Composable
internal fun OnAppForeground(onReturn: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentCallback by rememberUpdatedState(onReturn)
    DisposableEffect(owner) {
        var leftForeground = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> leftForeground = true
                Lifecycle.Event.ON_RESUME -> if (leftForeground) {
                    leftForeground = false
                    currentCallback()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
