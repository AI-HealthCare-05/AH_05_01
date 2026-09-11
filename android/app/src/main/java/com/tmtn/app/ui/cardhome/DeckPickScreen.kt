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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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

// Figma 카드 뒷면 실측 색 - 앱 전역 테마(검정/주황)와는 별개로, 카드 자체는 계속
// 초록 나무결 + 금색 TMTN 로고 디자인을 씀 (B03/B04/B05 XML 그대로).
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                state.optionIds.value.forEachIndexed { index, _ ->
                    CardBackTile(
                        selected = picked == index,
                        onClick = { state.pickCard(index) },
                    )
                }
            }

            if (picked == null) {
                Text(
                    "고르기 전에는 어떤 카드인지 보이지 않습니다. 하루에 한 번만 고를 수 있고, 고른 카드는 앱을 다시 열어도 그대로 남습니다.",
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
                    "확정한 카드는 오늘 바꿀 수 없어요. 고르지 않은 카드는 공개되지 않아요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
                TmtnPrimaryButton(text = "이 카드로 확정", onClick = { state.openConfirmDialog() })
                TmtnTextButton(text = "다시 고르기", onClick = { state.resetPick() })
            }
        }
    }

    // B05: 확정 다이얼로그
    if (state.showConfirmDialog.value) {
        AlertDialog(
            onDismissRequest = { state.showConfirmDialog.value = false },
            containerColor = colors.surface,
            title = { Text("이 카드로 확정할까요?", style = TmtnType.title, color = colors.onSurface) },
            text = {
                // ⚠️ 2026-09-06 QA(P2) 반영: 바로 위 화면 본문 안내와 한 글자도 다르지
                // 않아서, 다이얼로그가 새로운 정보 없이 그냥 반복이었음 - "한 번 더 확인"
                // 이라는 다이얼로그 본연의 역할에 맞게 되돌릴 수 없다는 점만 짧게 강조.
                Text(
                    "확정하면 되돌릴 수 없어요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            },
            confirmButton = {
                Text(
                    "확정하기", style = TmtnType.label, color = colors.primary,
                    // ⚠️ 2026-09-06 QA(접근성) 반영: clickable{}.padding(8.dp)만 있어서
                    // 실제 터치 영역이 약 36dp였음(48dp 미만) - 최소 터치 영역 확보.
                    modifier = Modifier.clickable { scope.launch { state.confirmCard() } }
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .wrapContentSize(Alignment.Center)
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    "다시 고르기", style = TmtnType.label, color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable { state.showConfirmDialog.value = false }
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .wrapContentSize(Alignment.Center)
                        .padding(8.dp),
                )
            },
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
private fun CardBackTile(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(104.dp)
            .height(152.dp)
            .background(CardWoodDark, RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.border(3.dp, CardGold, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
    ) {
        // 나무결 패턴(가로줄 몇 개로 단순화 - Figma 원본은 무작위 폭의 줄 15개)
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(0.6f, 0.5f, 0.7f, 0.35f, 0.55f).forEach { widthFraction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .height(2.dp)
                        .background(CardWoodGrain),
                )
            }
        }

        // TMTN 로고 알약 (상단 중앙)
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .align(Alignment.TopCenter)
                .background(CardGold, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text("TMTN", style = TmtnType.caption, color = CardWoodDark)
        }

        // 라디오 선택 표시 (하단 중앙)
        Box(
            modifier = Modifier
                .padding(bottom = 14.dp)
                .align(Alignment.BottomCenter)
                .size(22.dp)
                .then(
                    if (selected) {
                        Modifier.background(CardGold, CircleShape)
                    } else {
                        Modifier.border(2.dp, CardRadioUnselected, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(modifier = Modifier.size(9.dp).background(CardWoodDark, CircleShape))
            }
        }
    }
}
