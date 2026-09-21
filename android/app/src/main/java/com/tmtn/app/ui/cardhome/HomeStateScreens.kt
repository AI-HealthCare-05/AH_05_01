package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import com.tmtn.app.ui.theme.TmtnHomeColor

/** 홈을 보면서 측정 중인 틈새 운동 세션으로 복귀한다. 새 세션을 시작하지 않는다. */
@Composable
internal fun ActiveExerciseBanner(state: CardHomeState) {
    val session = state.activeExerciseSession.value ?: return
    Row(Modifier.fillMaxWidth().testTag("home-exercise-return")
        .clip(RoundedCornerShape(20.dp)).background(TmtnHomeColor.Forest)
        .clickable { state.step.value = CardHomeStep.EXTRA_RUNNING }
        .heightIn(min = 64.dp)
        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (session.state == "PAUSED") "틈새 운동 일시정지 · 돌아가기" else "틈새 운동 진행 중 · 돌아가기",
            style = TmtnType.label, color = Color.White, modifier = Modifier.testTag("home-exercise-return-label"))
    }
}
