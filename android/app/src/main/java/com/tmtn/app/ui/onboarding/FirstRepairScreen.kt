package com.tmtn.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.DamArtwork
import com.tmtn.app.ui.common.damRepairLabel
import com.tmtn.app.ui.common.tmtnMaterialDrawable
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun FirstDamRoute(state: OnboardingState, onContinue: () -> Unit) {
    val repair = remember { FirstRepairState() }
    val scope = rememberCoroutineScope()
    val reduced = rememberTmtnReducedMotion()
    val duration = if (reduced) TmtnMotion.SheetReducedMillis
        else TmtnMotion.FirstRepairTravelMillis + TmtnMotion.FirstRepairSettleMillis
    LaunchedEffect(Unit) { repair.refresh() }
    if (repair.phase == FirstRepairPhase.Legacy) {
        LegacyFirstDamRoute(state, onContinue)
        return
    }
    BackHandler(repair.busy) { /* 저장 중 뒤로 이동해 중복 작업을 시작하지 않는다. */ }
    FirstRepairScreen(
        phase = repair.phase, busy = repair.busy, failedOperation = repair.failedOperation,
        completedStage = repair.data?.companion?.current_stage ?: 1,
        reducedMotion = reduced,
        onBack = if (!repair.busy && state.canReturnToInputSummary) ({ state.returnToInputSummary() }) else null,
        onAction = {
            when (repair.phase) {
                FirstRepairPhase.Welcome -> scope.launch { repair.receiveGift() }
                FirstRepairPhase.Gift -> scope.launch { repair.fillGap(duration) }
                FirstRepairPhase.Error -> scope.launch { repair.retry(duration) }
                FirstRepairPhase.Complete -> onContinue()
                else -> Unit
            }
        },
    )
}

private data class FirstRepairCopy(val eyebrow: String, val title: String, val body: String, val guide: String, val action: String)

private fun firstRepairCopy(phase: FirstRepairPhase, failure: FirstRepairOperation, stage: Int): FirstRepairCopy = when {
    phase == FirstRepairPhase.Welcome -> FirstRepairCopy("첫 만남", "작은 틈 하나,\n같이 메워볼까요?",
        "첫 재료는 틈튼이가 준비했어요.\n나뭇가지 하나로 시작해 봐요.", "잘 왔어! 첫 재료는 내가 줄게.", "첫 재료 받기")
    phase == FirstRepairPhase.Complete -> FirstRepairCopy("첫 복구 완료", "첫 틈을 메웠어요!\n이제 ${stage}단계예요.",
        "오늘의 작은 실천으로 재료를 모아\n다음 빈틈도 천천히 메워가요.", "이렇게 하나씩, 우리 같이 채워가자.", "오늘의 카드 고르기")
    phase == FirstRepairPhase.Error && failure == FirstRepairOperation.Complete -> FirstRepairCopy("잠깐 연결이 끊겼어요",
        "첫 복구를\n저장하지 못했어요.", "받은 재료는 사라지지 않아요.\n연결을 확인하고 다시 시도해 주세요.",
        "괜찮아. 같은 재료로 다시 해보자.", "다시 틈 메우기")
    phase == FirstRepairPhase.Error -> FirstRepairCopy("잠깐 연결이 끊겼어요", "첫 재료를\n확인하지 못했어요.",
        "연결을 확인하고 다시 시도해 주세요.\n같은 선물이 두 번 지급되지는 않아요.", "괜찮아. 천천히 다시 해보자.", "다시 확인하기")
    else -> FirstRepairCopy(if (phase == FirstRepairPhase.Placing) "첫 복구" else "첫 재료를 받았어요",
        "이 나뭇가지로\n첫 틈을 메워봐요.", "작은 재료 하나가\n내 댐의 첫 변화를 만들어요.",
        if (phase == FirstRepairPhase.Placing) "좋아, 첫 빈틈이 채워지고 있어!" else "준비됐지? 저 빈틈에 함께 놓아보자.",
        if (phase == FirstRepairPhase.Placing) "틈을 메우는 중" else "이 재료로 틈 메우기")
}

/** 승인된 A21-01~04와 실패 상태. 본문은 스크롤하고 주 행동은 하단에 유지한다. */
@Composable
internal fun FirstRepairScreen(
    phase: FirstRepairPhase, busy: Boolean, failedOperation: FirstRepairOperation = FirstRepairOperation.Read,
    completedStage: Int = 1, reducedMotion: Boolean = false, onBack: (() -> Unit)? = null, onAction: () -> Unit,
) {
    val colors = LocalTmtnColors.current
    if (phase == FirstRepairPhase.Loading) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            TmtnTopBar("내 댐 첫 복구", onBack)
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
                Text("첫 재료를 준비하고 있어요.", style = TmtnType.firstRepairBody, color = colors.onSurfaceVariant)
            }
        }
        return
    }
    val copy = firstRepairCopy(phase, failedOperation, completedStage)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("first-repair-screen")) {
        TmtnTopBar("내 댐 첫 복구", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(copy.eyebrow, style = TmtnType.firstRepairEyebrow, color = colors.onSurfaceVariant)
                Text(copy.title, style = TmtnType.firstRepairHeadline, color = colors.onSurface,
                    modifier = Modifier.fillMaxWidth().semantics { heading() })
                Text(copy.body, style = TmtnType.firstRepairBody, color = colors.onSurfaceVariant)
            }
            if (phase != FirstRepairPhase.Error || failedOperation == FirstRepairOperation.Complete) {
                FirstRepairScene(phase, completedStage, reducedMotion)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.beaver_cheer), null, Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("틈튼이", style = TmtnType.firstRepairEyebrow, color = colors.onSurface)
                    Text(copy.guide, style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TmtnPrimaryButton(copy.action, onAction, enabled = !busy, modifier = Modifier.testTag("first-repair-action"))
            Text(when (phase) {
                FirstRepairPhase.Complete -> "이제 오늘의 작은 실천을 골라봐요."
                FirstRepairPhase.Error -> "같은 재료로 이어갈 수 있어요."
                else -> "첫 선물은 가입할 때 한 번만 받아요."
            }, style = TmtnType.navigationLabel, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FirstRepairScene(phase: FirstRepairPhase, completedStage: Int, reducedMotion: Boolean) {
    val colors = LocalTmtnColors.current
    val done = phase == FirstRepairPhase.Complete
    val placing = phase == FirstRepairPhase.Placing
    val travel by animateFloatAsState(if (placing || done) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(TmtnMotion.FirstRepairTravelMillis, easing = TmtnMotion.EaseOut),
        label = "첫 재료 이동")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (done) "${completedStage}단계 · ${damRepairLabel(completedStage)}" else "복구 전 · 0단계",
            style = TmtnType.firstRepairEyebrow, color = colors.onSurface,
            modifier = Modifier.background(colors.surface, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sceneWidth = maxWidth
            // 비트맵과 이동 경로만 비례 축소하며 글자 영역에는 고정 높이를 주지 않는다.
            val damHeight = sceneWidth / com.tmtn.app.ui.common.DamArtworkAspectRatio
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Crossfade(if (done) completedStage else 0,
                    animationSpec = tween(if (reducedMotion) TmtnMotion.SheetReducedMillis else TmtnMotion.EnterMillis), label = "첫 복구 결과") { stage ->
                    DamArtwork(stage, Modifier.testTag("repair-dam-$stage"), "내 댐 ${stage}단계")
                }
                Row(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(tmtnMaterialDrawable("WOOD")), null,
                        Modifier.size(56.dp).graphicsLayer { alpha = if (placing && !reducedMotion) 0f else 1f })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (done) "첫 복구 완료" else if (placing) "첫 빈틈에 더하는 중" else "첫 만남 선물",
                            style = TmtnType.firstRepairEyebrow, color = colors.onSurfaceVariant)
                        Text(if (done) "댐에 더한 재료 1개" else "나뭇가지 1개", style = TmtnType.missionName, color = colors.onSurface)
                    }
                }
            }
            if (placing && !reducedMotion) {
                Image(painterResource(tmtnMaterialDrawable("WOOD")), null,
                    Modifier.size(56.dp).graphicsLayer {
                        translationX = (16.dp + (sceneWidth * .42f - 16.dp) * travel).toPx()
                        translationY = (damHeight + 32.dp + (damHeight * .53f - damHeight - 32.dp) * travel).toPx()
                        rotationZ = -16f * travel
                        scaleX = 1f - .07f * travel
                        scaleY = scaleX
                    })
            }
        }
    }
}
