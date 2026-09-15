package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** Reserves space below the form, so validation never covers the submit action. */
@Composable
internal fun OnboardingErrorMessage(message: String?) {
    if (message.isNullOrBlank()) return
    val colors = LocalTmtnColors.current
    Surface(color = colors.errorContainer, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(max = 160.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(message, style = TmtnType.caption, color = colors.error,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp))
    }
}

/** A modal owns input until the existing request resolves; no timed fake progress. */
@Composable
internal fun OnboardingSavingDialog() {
    val colors = LocalTmtnColors.current
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(color = colors.background, shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.onSurface, strokeWidth = 2.dp)
                Text("잠시만 기다려 주세요.", style = TmtnType.body, color = colors.onSurface,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}
