package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.theme.ColorBrandForest

/** Uses the supplied splash_v1 artwork and proportions. No artificial loading progress. */
@Composable
fun TmtnLaunchArtwork(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(ColorBrandForest)) {
        val headWidth = minOf(maxWidth * 0.46f, 200.dp)
        val headTop = maxHeight * 0.475f - headWidth / 2
        TmtnMascot(R.drawable.splash_beaver_head, "틈튼 비버",
            Modifier.align(Alignment.TopCenter).offset(y = headTop).width(headWidth).height(headWidth))
        Image(painterResource(R.drawable.splash_wordmark), "TMTN",
            Modifier.align(Alignment.BottomCenter).offset(y = -(maxHeight * 0.09f)).width(105.dp))
    }
}
