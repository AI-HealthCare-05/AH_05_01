package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.common.TmtnActionButton
import com.tmtn.app.ui.common.TmtnActionStyle
import com.tmtn.app.ui.reference.WaistEstimateArticle
import com.tmtn.app.ui.reference.WaistEstimateState
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.launch

/** 저장된 추정값과 같은 위치를 가리키는 줄자. 상세 열기로 재계산하지 않는다. */
@Composable
internal fun HomeWaistPanel(state: WaistEstimateState, loadOnEntry: Boolean = true) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(state, loadOnEntry) { if (loadOnEntry) state.load() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    HomeWaistCard(state.ui.value, expanded, { expanded = !expanded }, { scope.launch { state.load() } })
}

@Composable
internal fun HomeWaistCard(result: WaistEstimateUi, expanded: Boolean, onToggle: () -> Unit, onRetry: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().testTag("home-waist").clip(shape).background(ColorBackground)
        .border(1.dp, TmtnHomeColor.Border, shape)
        .then(if (!expanded && result != WaistEstimateUi.Loading) Modifier.tmtnClickable(onClick = onToggle)
            .semantics { onClick(label = "허리둘레 자세히 보기") { onToggle(); true } } else Modifier)
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (expanded) {
            WaistEstimateArticle(result, onRetry)
            TmtnActionButton("설명 접기", onToggle, TmtnActionStyle.Text)
        } else {
            val heading: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("허리둘레", style = TmtnType.missionName, color = TmtnHomeColor.Forest, modifier = Modifier.semantics { heading() })
                    Text("신체 정보로 계산한 추정값", style = TmtnType.caption, color = ColorOnSurfaceVariant)
                }
            }
            val value: @Composable () -> Unit = {
                if (result is WaistEstimateUi.Available) {
                    val unitStyle = TmtnType.caption
                    Text(buildAnnotatedString {
                        append(result.displayValue)
                        withStyle(SpanStyle(fontSize = unitStyle.fontSize, color = ColorOnSurfaceVariant)) { append(" cm") }
                    }, style = TmtnType.display, color = TmtnHomeColor.Forest)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 300.dp || LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.25f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { heading(); value() }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { heading() }
                    value()
                }
            }
            if (result is WaistEstimateUi.Available) {
                WaistEstimateTape(result.displayValue)
            } else Text(when (result) {
                WaistEstimateUi.Loading -> "추정값을 확인하고 있어요."
                WaistEstimateUi.Failed -> "허리둘레를 불러오지 못했어요. 눌러서 다시 확인해 주세요."
                else -> "아직 준비된 추정값이 없어요. 눌러서 안내를 확인해 주세요."
            }, style = TmtnType.body, color = ColorOnSurfaceVariant)
        }
    }
}
