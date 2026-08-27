package kr.tmtn.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun TmtnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TmtnColorScheme,
        typography = TmtnTypography,
        shapes = TmtnShapes,
        content = content,
    )
}
