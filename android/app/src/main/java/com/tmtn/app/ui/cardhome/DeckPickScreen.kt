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
                style = TmtnType.headline, color = colors.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
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
                    "오늘은 어떤 실천을 만나게 될까요? 고른 한 장은 오늘 내내 함께해요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
                TmtnPrimaryButton(text = "이 카드로 확정", onClick = {}, enabled = false)
                Text(
                    "카드를 한 장 고르면 확정할 수 있어요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            } else {
                SelectionIndicatorPill(index = picked)
                Text(
                    // ⚠️ 2026-09-06 QA(P1-2) 반영: 이미 고른 뒤 다른 카드를 눌러도 선택이
                    // 안 바뀌는 것 자체는 의도된 동작인데(하루 한 장 확정 전 실수 방지),
                    // 그 안내가 없어서 "눌렀는데 반응이 없다"로 오해했음.
                    "다른 카드를 고르려면 ‘다시 고르기’를 눌러 주세요. 확정한 뒤에는 오늘 바꿀 수 없어요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
                TmtnPrimaryButton(text = "이 카드로 확정", onClick = { state.openConfirmDialog() })
                TmtnTextButton(text = "다시 고르기", onClick = { state.resetPick() })
            }
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
            .background(colors.surface, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(12.dp).border(2.dp, colors.onSurface, CircleShape))
        Text("${ordinal} 번째 카드를 골랐어요", style = TmtnType.label, color = colors.onSurface)
    }
}

/** Figma "오늘의 카드 · 뒷면" - 초록 나무결 패턴 + 금색 TMTN 로고 알약 + 라디오 선택 표시. */
@Composable
private fun CardBackTile(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, index: Int = 0, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .aspectRatio(.54f)
            .background(CardWoodDark, RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.border(2.dp, Color(0xFF315342), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${index + 1}번째 카드" },
    ) {
        Image(painterResource(com.tmtn.app.R.drawable.mission_tarot_back), null, Modifier.fillMaxSize())
        if (selected) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            .background(Color(0xFF315342), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text("선택", style = TmtnType.label, color = Color.White)
        }

    }
}
