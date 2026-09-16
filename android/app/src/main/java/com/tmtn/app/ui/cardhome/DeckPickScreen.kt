package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.text.style.TextAlign
import com.tmtn.app.ui.theme.TmtnMotion
import com.tmtn.app.ui.theme.rememberTmtnReducedMotion
import com.tmtn.app.ui.theme.tmtnPressFeedback
import com.tmtn.app.ui.theme.tmtnFocusOutline

// Simplified Figma-approved card back: forest ground, ivory border, one beaver emblem.
private val CardWoodDark = Color(0xFF12352A)
private val CardWoodGrain = Color(0xFF2F6B4C)
private val CardGold = Color(0xFFF5B71E)
private val CardRadioUnselected = Color(0xFFF4F4F0)

/** Figma B03(고르기 전)·B04(한 장 선택됨)·B05(확정 다이얼로그)를 로컬 상태로 통합. */
@Composable
fun DeckPickScreen(state: CardHomeState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val picked = state.pickedIndex.value

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 카드", onBack = onBack)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.displayDateLabel().toKoreanDateLabel(), style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text(
                // ⚠️ 2026-09-06 QA(P1-1) 반영: "가운데 카드로 정할까?"가 리터럴로 박혀
                // 있어서, 첫/세 번째를 골라도 항상 "가운데"라고 말했음(바로 아래 칩은
                // "${ordinal} 번째"로 정확한데 제목만 틀림). 같은 서수 매핑을 재사용.
                if (picked == null) {
                    "카드 한 장을 골라 주세요"
                } else {
                    "${listOf("첫", "두", "세").getOrElse(picked) { "그" }} 번째 카드로 정할까?"
                },
                style = TmtnType.headline, color = colors.onSurface, textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.optionIds.value.forEachIndexed { index, _ ->
                    CardBackTile(
                        selected = picked == index,
                        enabled = (picked == null || picked == index) && !state.isLoading.value,
                        onClick = { state.pickCard(index) },
                        modifier = Modifier.weight(1f), index = index,
                    )
                }
            }

            if (picked == null) {
                Text(
                    "마음이 가는 한 장을 골라보세요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            } else {
                SelectionIndicatorPill(index = picked)
                Text(
                    // ⚠️ 2026-09-06 QA(P1-2) 반영: 이미 고른 뒤 다른 카드를 눌러도 선택이
                    // 안 바뀌는 것 자체는 의도된 동작인데(하루 한 장 확정 전 실수 방지),
                    // 그 안내가 없어서 "눌렀는데 반응이 없다"로 오해했음.
                    "확정하면 오늘은 이 카드와 함께해요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
        Column(Modifier.fillMaxWidth().background(colors.background).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("확정한 카드는 오늘 바꿀 수 없어요.", style = TmtnType.caption,
                color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp))
            TmtnPrimaryButton(text = "이 카드로 확정", onClick = { state.openConfirmDialog() },
                enabled = picked != null && !state.isLoading.value)
            if (picked != null) TmtnTextButton(text = "다시 고르기", onClick = { state.resetPick() }, enabled = !state.isLoading.value)
        }
    }

    // Keep opening, failure and retry in this one confirmation window.
    if (state.showConfirmDialog.value) {
        com.tmtn.app.ui.common.TmtnConfirmationDialog(
            title = "이 카드로 확정할까요?", message = "확정하면 오늘은 다른 카드로 바꿀 수 없어요.",
            confirmLabel = if (state.isLoading.value) "카드 여는 중…" else "확정하기",
            cancelLabel = "다시 고르기", busy = state.isLoading.value, error = state.errorMessage.value,
            destructive = false, busyMessage = "선택한 카드를 확인하고 있어요.",
            onConfirm = { scope.launch { state.confirmCard() } },
            onDismiss = { state.resetPick() },
        )
    }
}

@Composable
private fun SelectionIndicatorPill(index: Int) {
    val colors = LocalTmtnColors.current
    val ordinal = listOf("첫", "두", "세").getOrElse(index) { "그" }
    Row(
        modifier = Modifier
            .background(colors.secondaryContainer, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = colors.onSurface)
        Text("${ordinal} 번째 카드를 골랐어요", style = TmtnType.label, color = colors.onSurface)
    }
}

/** Figma "오늘의 카드 · 뒷면" - 초록 나무결 패턴 + 금색 TMTN 로고 알약 + 라디오 선택 표시. */
@Composable
private fun CardBackTile(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, index: Int = 0, enabled: Boolean = true) {
    val colors = LocalTmtnColors.current
    val interactions = remember { MutableInteractionSource() }
    val still = rememberTmtnReducedMotion() || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val lift by animateFloatAsState(if (selected && !still) 1f else 0f,
        animationSpec = if (still) snap() else spring(dampingRatio = 1f, stiffness = TmtnMotion.TouchStiffness), label = "picked card lift")
    Box(
        modifier = modifier
            .aspectRatio(.54f)
            .graphicsLayer { translationY = -10.dp.toPx() * lift; scaleX = 1f + .025f * lift; scaleY = scaleX }
            .tmtnPressFeedback(interactions, enabled)
            .tmtnFocusOutline(interactions, RoundedCornerShape(12.dp), enabled)
            .background(CardWoodDark, RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.border(2.dp, colors.secondary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected, enabled = enabled, role = Role.RadioButton, interactionSource = interactions, indication = null, onClick = onClick)
            .semantics { contentDescription = "${index + 1}번째 카드" },
    ) {
        Image(painterResource(com.tmtn.app.R.drawable.mission_tarot_back), null, Modifier.fillMaxSize().padding(if (selected) 3.dp else 0.dp))
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(7.dp)
            .size(24.dp).background(colors.secondary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = colors.onSurface)
        }

    }
}
