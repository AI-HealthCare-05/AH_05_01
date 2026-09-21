package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

@Composable
internal fun StepProgressHeader(step: Int, total: Int, label: String) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
        .testTag("signup-progress").semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..total.toFloat(), total - 1)
        }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$step / ${total}단계 · $label", style = TmtnType.caption, color = colors.onSurfaceVariant)
        TmtnStepBars(totalSteps = total, currentStep = step)
    }
}
