package com.tmtn.app.ui.reference

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.PeerComponent
import com.tmtn.app.network.model.TuntunScorePeerV2Response
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReferenceDetailScreen(state: ReferenceState, peerPositions: List<ScorePeerPositionUi> = emptyList(),
    waist: WaistEstimateUi = WaistEstimateUi.Unavailable, onRetryWaist: () -> Unit = {}) {
    // Waist remains on its separate cm screen; it is not part of the newspaper.
    ScoreNewspaperScreen(state, peerPositions = peerPositions)
}

@Composable
fun ReferenceFactorsScreen(state: ReferenceState, initialArea: String? = null) {
    // A fresh summary-row action opens its requested area; editor round trips retain the current article.
    key(state.factorOpenRequest.intValue) {
        ScoreNewspaperScreen(state, initialArea = initialArea ?: "physical")
    }
}

@Composable
fun ReferenceInputsScreen(
    state: ReferenceState,
    scope: CoroutineScope,
    onOpenHealthInfo: () -> Unit,
    onOpenExerciseInfo: () -> Unit,
    onLoad: suspend () -> Unit = { state.loadInputs() },
    onRefresh: () -> Unit = { scope.launch { state.recalculateAndReturnToSummary() } },
) {
    val colors = LocalTmtnColors.current
    val inputs = state.scoreInputs.value
    var requesting by remember { mutableStateOf(false) }
    // Error messages belong to the request that produced them; do not expose raw HTTP text.
    suspend fun load() {
        requesting = true
        state.errorMessage.value = null
        try { onLoad() } finally { requesting = false }
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("입력 정보", onBack = { state.goBack() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("계산에 쓰인 값", style = TmtnType.headline, color = colors.onSurface)
            if (inputs == null) {
                if (requesting || state.isLoading.value) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = colors.primary, strokeWidth = 2.dp)
                    Text("입력 정보를 불러오고 있어요", style = TmtnType.body, color = colors.onSurfaceVariant)
                } else {
                    Text("입력 정보를 불러오지 못했어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                    TmtnPrimaryButton("다시 시도", { scope.launch { load() } })
                }
            } else {
                if (state.errorMessage.value != null && !requesting && !state.isLoading.value) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("갱신하지 못해 이전 정보를 표시하고 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                        TmtnOutlinedButton("다시 불러오기", { scope.launch { load() } })
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScoreActionRow("신체 정보", onClick = onOpenHealthInfo)
                    ScoreRule()
                    InfoRow("생년월", inputs.birth_month_label ?: "입력 필요")
                    InfoRow("성별", inputs.sex_label ?: "입력 필요")
                    InfoRow("키", inputs.height_cm?.let { readableNumber(it) + " cm" } ?: "입력 필요")
                    InfoRow("몸무게", inputs.weight_kg?.let { readableNumber(it) + " kg" } ?: "입력 필요")
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScoreActionRow("운동 정보", "입력한 주당 운동량", onOpenExerciseInfo)
                    ScoreRule()
                    InfoRow("유산소 · 저강도", inputs.cardio_low_min?.let { "$it" + "분" } ?: "입력 필요")
                    InfoRow("유산소 · 중강도", inputs.cardio_moderate_min?.let { "$it" + "분" } ?: "입력 필요")
                    InfoRow("유산소 · 고강도", inputs.cardio_vigorous_min?.let { "$it" + "분" } ?: "입력 필요")
                    InfoRow("근력운동", inputs.strength_label ?: "입력 필요")
                }
                TmtnPrimaryButton(
                    text = if (state.isLoading.value) "지수 불러오는 중" else "최신 정보로 지수 보기",
                    onClick = onRefresh, enabled = !state.isLoading.value && !requesting,
                )
            }
        }
    }
}

private fun readableNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun ScoreNotices(score: TuntunScorePeerV2Response, includeGeneralNotice: Boolean = true) {
    val colors = LocalTmtnColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (includeGeneralNotice && score.notice.isNotBlank()) Text(score.notice, style = TmtnType.caption, color = colors.onSurfaceVariant)
        // ⚠️ 2026-09-10 반영: 새 계약엔 olderAdultNotice/missionIntegrationStatus가 없음 -
        // referenceCaution(참조 표본 관련 안내)으로 대체함.
        if (score.referenceCaution.isNotBlank()) Text(score.referenceCaution, style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

@Composable
internal fun ScoreMethodDialog(score: TuntunScorePeerV2Response, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 지수에 대하여", style = TmtnType.title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { ScoreMethodContent(score) } },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") } },
        containerColor = LocalTmtnColors.current.background,
    )
}

@Composable
private fun ScoreMethodContent(score: TuntunScorePeerV2Response?) {
    val colors = LocalTmtnColors.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("비진단용 참고 정보", style = TmtnType.bodyLarge, color = colors.onSurface)
        Text("입력한 신체 정보와 평소 운동량을 바탕으로 계산한 지수예요. 의료 진단을 대신하지 않아요.",
            style = TmtnType.body, color = colors.onSurfaceVariant)
        Text("종합 지수는 계산 가능한 영역을 모아 보여줘요. 영역별 안내도 함께 확인해 주세요.",
            style = TmtnType.body, color = colors.onSurfaceVariant)
        ScoreRule()
        if (ScorePercentilePresentation.fromCurrent(score?.peerCompositeScore)?.isPreview == false) {
            Text("비교 위치 읽기", style = TmtnType.bodyLarge, color = colors.onSurface)
            Text("‘100명 중 몇 번째쯤’은 비교 집단에서의 대략적인 위치예요. 실제 사람 수나 정확한 등수를 뜻하지 않아요.",
                style = TmtnType.body, color = colors.onSurfaceVariant)
        }
        Text("허리둘레는 cm 단위의 추정값으로 따로 표시해요. 둘레만으로 백분위나 건강 순위를 만들지 않아요.",
            style = TmtnType.body, color = colors.onSurfaceVariant)
        if (score != null) {
            // The product disclaimer is already above; retain age-specific and integration notices.
            // Server implementation notes are recorded in the QA report, not repeated in this dialog.
            ScoreNotices(score, includeGeneralNotice = false)
            // Keep the server's interpretation/restrictions reachable without crowding the newspaper.
            score.components.filter { it.available }.forEach { component ->
                ScoreRule()
                Text(component.label, style = TmtnType.bodyLarge, color = colors.onSurface)
                Text(COMPONENT_GUIDANCE[component.componentKey] ?: "", style = TmtnType.body, color = colors.onSurfaceVariant)
            }

        }
    }
}

@Composable
fun ReferenceAboutScreen(state: ReferenceState) {
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("이 지수에 대하여", onBack = { state.goBack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            ScoreMethodContent(state.score.value)
        }
    }
}
