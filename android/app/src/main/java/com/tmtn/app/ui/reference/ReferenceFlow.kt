package com.tmtn.app.ui.reference

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.launch

/** Presentation only. Score calculation, input APIs and the existing back stack are unchanged. */
@Composable
fun ReferenceFlow(
    onGoPickCard: () -> Unit,
    onOpenMyInfo: () -> Unit,
    onOpenHealthInfo: () -> Unit,
    onOpenExerciseInfo: () -> Unit,
    onImmersiveChange: (Boolean) -> Unit,
    state: ReferenceState = remember { ReferenceState() },
) {
    val scope = rememberCoroutineScope()
    val waist = remember { WaistEstimateState() }
    LaunchedEffect(Unit) { waist.load() }
    LaunchedEffect(Unit) {
        // Returning from an editor keeps the reading stack; the inputs page reloads its own values.
        if (state.step.value in setOf(ReferenceStep.LOADING, ReferenceStep.SUMMARY, ReferenceStep.INELIGIBLE)) state.loadScore()
    }
    LaunchedEffect(state.step.value) {
        if (state.step.value in setOf(ReferenceStep.DETAIL, ReferenceStep.FACTORS)) state.editorial.load()
    }
    var focusedArea by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val immersive = state.step.value in setOf(
        ReferenceStep.DETAIL, ReferenceStep.FACTORS, ReferenceStep.INPUTS, ReferenceStep.ABOUT, ReferenceStep.WAIST,
    )
    LaunchedEffect(immersive) { onImmersiveChange(immersive) }
    BackHandler(enabled = immersive) { state.goBack() }
    ReferencePageTransition(state.step.value) { step ->
        when (step) {
            ReferenceStep.LOADING -> ReferenceLoadingScreen(
                error = state.errorMessage.value.takeUnless { state.isLoading.value },
                onRetry = { scope.launch { state.loadScore() } },
            )
            ReferenceStep.SUMMARY -> ReferenceSummaryScreen(state, onOpenExerciseInfo,
                onOpenArea = { focusedArea = it; state.openFactors() }, waist = waist.ui.value)
            ReferenceStep.INELIGIBLE -> ReferenceIneligibleScreen(state, onOpenMyInfo, waist.ui.value)
            ReferenceStep.DETAIL -> ReferenceDetailScreen(state, waist = waist.ui.value,
                onRetryWaist = { scope.launch { waist.load() } })
            ReferenceStep.FACTORS -> ReferenceFactorsScreen(state, focusedArea)
            ReferenceStep.INPUTS -> ReferenceInputsScreen(state, scope, onOpenHealthInfo, onOpenExerciseInfo,
                onRefresh = { scope.launch { state.recalculateAndReturnToSummary(); waist.load() } })
            ReferenceStep.WAIST -> ReferenceWaistScreen(state, waist.ui.value, onRetry = { scope.launch { waist.load() } })
            ReferenceStep.ABOUT -> ReferenceAboutScreen(state)
        }
    }
}

/** A short reading transition; scroll and disclosure state survive a round trip. */
@Composable
fun ReferencePageTransition(step: ReferenceStep, content: @Composable (ReferenceStep) -> Unit) {
    val savedPages = rememberSaveableStateHolder()
    val reduced = rememberTmtnReducedMotion()
    AnimatedContent(
        targetState = step, modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(if (reduced) 0 else 160, easing = TmtnMotion.EaseOut)) +
                scaleIn(tween(if (reduced) 0 else TmtnMotion.EnterMillis, easing = TmtnMotion.EaseOut),
                    initialScale = if (reduced) 1f else .985f)) togetherWith
                fadeOut(tween(if (reduced) 0 else 100))
        },
        label = "score reading transition",
    ) { page ->
        savedPages.SaveableStateProvider(page) { content(page) }
    }
}

@Composable
fun ReferenceSummaryScreen(state: ReferenceState, onOpenExerciseInfo: () -> Unit = { state.openInputs() },
    onOpenArea: (String) -> Unit = { state.openFactors() }, waist: WaistEstimateUi = WaistEstimateUi.Unavailable) {
    ScoreNewspaperSummary(state, onOpenExerciseInfo, onOpenArea, waist)
}

@Composable
fun ReferenceIneligibleScreen(state: ReferenceState, onOpenMyInfo: () -> Unit, waist: WaistEstimateUi = WaistEstimateUi.Unavailable) {
    val colors = LocalTmtnColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("틈튼지수", style = TmtnType.headline, color = colors.onSurface)
        TmtnMascot(R.drawable.beaver_waiting, null, Modifier.fillMaxWidth().height(192.dp))
        Text("첫 지수를 준비해요", style = TmtnType.headline, color = colors.onSurface)
        Text("신체 정보와 운동 정보를 입력해 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
        Text("정보가 없는 영역은 비교에서 제외해요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        TmtnPrimaryButton("정보 입력하기", onOpenMyInfo)
        ScoreRule()
        WaistEstimateSummary(waist, onOpen = { state.openWaist() })
        Text("비진단용 참고 정보", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

@Composable
fun ReferenceLoadingScreen(error: String?, onRetry: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (error == null) {
            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            Text("틈튼지수를 불러오고 있어요", style = TmtnType.body, color = colors.onSurfaceVariant)
        } else {
            TmtnMascot(R.drawable.beaver_waiting, null, Modifier.size(152.dp), greet = false)
            Text("지수를 불러오지 못했어요", style = TmtnType.title, color = colors.onSurface)
            Text("연결 상태를 확인하고 다시 시도해 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TmtnPrimaryButton("다시 시도", onRetry)
        }
    }
}
