package com.tmtn.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** Keep the original artwork unobstructed; show a heading only when there is a daily status. */
@Composable
fun TmtnHomeHero(@DrawableRes image: Int, description: String, status: String, title: String) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(232.dp), contentAlignment = Alignment.Center) {
            TmtnMascot(image, description, Modifier.widthIn(max = 292.dp).fillMaxWidth().height(228.dp), reactToTap = true)
        }
        if (title.isNotBlank()) Text(title.replace('\n', ' '), style = TmtnType.title,
            color = colors.onSurface, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        else Spacer(Modifier.height(12.dp))
    }
}
