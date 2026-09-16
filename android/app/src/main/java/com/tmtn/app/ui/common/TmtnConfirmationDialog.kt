package com.tmtn.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.*

/** Figma confirmation dialog: neutral surface, readable actions, feedback in the same window. */
@Composable
fun TmtnConfirmationDialog(
    title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    cancelLabel: String = "취소", busy: Boolean = false, error: String? = null,
    destructive: Boolean = true, busyMessage: String = "변경 내용을 확인하고 있어요.",
) {
    val colors = LocalTmtnColors.current
    val minTarget = 48.dp
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = colors.surface,
        title = { Text(title, style = TmtnType.title, color = colors.onSurface) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = TmtnType.body, color = colors.onSurfaceVariant)
                if (busy) Text(busyMessage, style = TmtnType.caption, color = colors.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (error != null) Text(error, style = TmtnType.caption, color = colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        },
        confirmButton = {
            TextButton(onConfirm, enabled = !busy, modifier = Modifier.heightIn(min = minTarget)) {
                Text(confirmLabel, style = TmtnType.label,
                    color = if (busy) colors.onSurfaceVariant else if (destructive) colors.error else colors.onSurface)
            }
        },
        dismissButton = {
            TextButton(onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = minTarget)) {
                Text(cancelLabel, style = TmtnType.label, color = colors.onSurface)
            }
        })
}
