package com.tmtn.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnLayout

/** A container is not implicitly a button. Newspaper paper and illustrated cards keep their own layout. */
enum class TmtnSurfaceRole { Exercise, Panel, Summary }

@Composable
fun Modifier.tmtnSurface(
    role: TmtnSurfaceRole,
    background: Color = LocalTmtnColors.current.surface,
    outlined: Boolean = true,
): Modifier {
    val shape = when (role) {
        TmtnSurfaceRole.Exercise -> TmtnLayout.ExerciseShape
        TmtnSurfaceRole.Panel -> TmtnLayout.PanelShape
        TmtnSurfaceRole.Summary -> TmtnLayout.SummaryShape
    }
    return clip(shape).background(background, shape).then(
        if (outlined) Modifier.border(TmtnLayout.Hairline, LocalTmtnColors.current.outlineVariant, shape)
        else Modifier
    )
}

@Composable
fun TmtnSurfaceCard(
    role: TmtnSurfaceRole,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().tmtnSurface(role).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
