package com.tmtn.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Related fields share a row until labels need the full width at larger text sizes. */
@Composable
internal fun ResponsiveFieldPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale * com.tmtn.app.ui.theme.LocalTmtnTextScale.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp || fontScale >= 1.4f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}
