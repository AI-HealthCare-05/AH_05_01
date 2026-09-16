package com.tmtn.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** A separate window keeps background tabs out of touch, keyboard and accessibility focus. */
@Composable
fun TmtnSheetDialog(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
            // Compose 1.7 otherwise remeasures to screenHeightDp and shifts content under system bars.
            usePlatformDefaultWidth = true, decorFitsSystemWindows = false,
        )) {
            val dialogView = LocalView.current
            LaunchedEffect(dialogView) {
                (dialogView.parent as? DialogWindowProvider)?.window?.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            CompositionLocalProvider(LocalDensity provides density) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                    // The platform supplies the dim layer. This is also the tap-outside target.
                    Box(Modifier.fillMaxSize().clickable(onClickLabel = "시트 닫기", onClick = onDismiss))
                    Box(Modifier.width(availableWidth).fillMaxHeight().testTag("tmtn-sheet-dialog")) { content() }
                }
            }
        }
    }
}
