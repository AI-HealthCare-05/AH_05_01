package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.common.TmtnActionButton
import com.tmtn.app.ui.common.TmtnActionStyle
import com.tmtn.app.ui.reference.WaistEstimateArticle
import com.tmtn.app.ui.reference.WaistEstimateState
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.launch

/** An independent home result. Opening it never navigates into the newspaper or recalculates a score. */
@Composable
internal fun HomeWaistPanel(state: WaistEstimateState, loadOnEntry: Boolean = true) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(state, loadOnEntry) { if (loadOnEntry) state.load() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    HomeWaistCard(state.ui.value, expanded, { expanded = !expanded }, { scope.launch { state.load() } })
}

@Composable
internal fun HomeWaistCard(result: WaistEstimateUi, expanded: Boolean, onToggle: () -> Unit, onRetry: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().testTag("home-waist"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val shape = RoundedCornerShape(20.dp)
        if (!expanded && result is WaistEstimateUi.Available) {
            HomeWaistSummary(result, onToggle)
        } else if (!expanded) {
            Column(Modifier.fillMaxWidth().background(colors.background, shape)
                .border(TmtnLayout.Hairline, colors.outlineVariant, shape).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("허리둘레", style = TmtnType.missionName, color = colors.onSurface, modifier = Modifier.semantics { heading() })
                Text(when (result) {
                    WaistEstimateUi.Loading -> "추정값을 확인하고 있어요."
                    WaistEstimateUi.Failed -> "허리둘레를 불러오지 못했어요."
                    else -> "아직 준비된 추정값이 없어요."
                }, style = TmtnType.caption, color = colors.onSurfaceVariant)
                if (result != WaistEstimateUi.Loading) TmtnActionButton("허리둘레 자세히 보기", onToggle, TmtnActionStyle.Text)
            }
        } else {
            Column(Modifier.fillMaxWidth().background(colors.background, shape)
                .border(TmtnLayout.Hairline, colors.outlineVariant, shape).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WaistEstimateArticle(result, onRetry)
                if (result != WaistEstimateUi.Loading) TmtnActionButton("설명 접기", onToggle, TmtnActionStyle.Text)
            }
        }
    }
}
