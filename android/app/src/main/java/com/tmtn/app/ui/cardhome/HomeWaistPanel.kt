package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(18.dp)
        .testTag("home-waist"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!expanded) {
            Text("허리둘레", style = TmtnType.sectionHeading, color = colors.onSurface, modifier = Modifier.semantics { heading() })
            if (result is WaistEstimateUi.Available) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(result.displayValue, style = TmtnType.editorialHeadline, color = colors.wood)
                    Text("cm · 추정값", style = TmtnType.caption, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text("입력한 신체 정보로 추정한 값이에요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            } else {
                Text(when (result) {
                    WaistEstimateUi.Loading -> "추정값을 확인하고 있어요."
                    WaistEstimateUi.Failed -> "허리둘레를 불러오지 못했어요."
                    else -> "아직 준비된 추정값이 없어요."
                }, style = TmtnType.body, color = colors.onSurfaceVariant)
            }
        }
        if (expanded) WaistEstimateArticle(result, onRetry)
        if (result != WaistEstimateUi.Loading) {
            TmtnActionButton(if (expanded) "설명 접기" else "허리둘레 자세히 보기", onToggle, TmtnActionStyle.Text)
        }
    }
}
