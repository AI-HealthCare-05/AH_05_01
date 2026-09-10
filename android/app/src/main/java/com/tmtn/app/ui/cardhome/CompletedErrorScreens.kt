package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma B07 · 오늘 완료 (하단 내비 있음 - CardHomeFlow에서 이 단계는 isImmersive=false) */
@Composable
fun CompletedScreen(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value
    val material = card?.let { MATERIAL_NAMES[it.five_element] }
    // ⚠️ 이 화면은 COMPLETED(완료)뿐 아니라 SKIPPED(중단으로 끝낸 미션)도 같이 씀 —
    // 둘 다 "오늘은 다시 시작 못 함"이라는 점은 같지만 문구/보상 표시는 달라야 함.
    val isSkipped = card?.state == "SKIPPED"

    // 완료 화면에 들어올 때마다 오늘 회고를 이미 남겼는지 확인 - 남겼으면 "한 줄 남기기"를 숨김.
    // SKIPPED(중단)는 애초에 회고 대상이 아니라서 확인 자체를 안 함.
    LaunchedEffect(isSkipped) {
        if (!isSkipped) state.checkTodayMemo()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 카드", onBack = { state.step.value = CardHomeStep.HOME })

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    if (isSkipped) "오늘은 여기까지" else "오늘도 하나 쌓았어요",
                    style = TmtnType.display, color = colors.onSurface, textAlign = TextAlign.Center,
                )
                card?.let {
                    Text(it.title, style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }

                // SKIPPED는 재료를 받지 못했으므로 보상 표시 자체를 생략.
                if (!isSkipped && material != null && card != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MaterialIcon(element = card.five_element, size = 88.dp)
                        Text(material.first + " 1개", style = TmtnType.title, color = colors.onSurface)
                        Text(material.second, style = TmtnType.caption, color = colors.onSurfaceVariant)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))

                if (isSkipped) {
                    Text(
                        "괜찮아요. 오늘은 여기까지만 하고, 내일 다시 해봐요.",
                        style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                } else {
                    Text("✓ 틈튼카드첩에 저장했어요", style = TmtnType.body, color = colors.onSurface)
                    Text(
                        "내일도 작은 행동 하나면 충분해요.",
                        style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }

            TmtnOutlinedButton(text = "오늘 카드 다시 보기", onClick = { state.step.value = CardHomeStep.REVEALED })
            // 아직 오늘 회고를 안 남겼을 때만 노출 (회고를 남기면 checkTodayMemo/submitRetrospect가
            // hasMemoToday를 true로 갱신해서 자동으로 사라짐). SKIPPED는 애초에 대상 아님.
            if (!isSkipped && state.hasMemoToday.value == false) {
                TmtnOutlinedButton(
                    text = "한 줄 남기기",
                    onClick = { state.step.value = CardHomeStep.CHALLENGE_RETROSPECT },
                )
            }
            Text(
                "내일 또 새로운 카드로 만나요.",
                style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Figma B09 · 카드 덱 오류 */
@Composable
fun CardErrorScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 카드", onBack = { state.step.value = CardHomeStep.HOME })

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(colors.surface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // ⚠️ 2026-09-06 반영: B09(카드 덱 오류) 배치표 그대로 - "빈 카드첩을 들고
                // 있는" beaver_empty.
                Image(
                    painter = painterResource(com.tmtn.app.R.drawable.beaver_empty),
                    contentDescription = "빈 카드첩을 들고 있는 비버",
                    modifier = Modifier.height(160.dp),
                )
            }

            Text("카드를 가져오지 못했습니다", style = TmtnType.title, color = colors.onSurface)
            Text(
                "잠시 뒤 다시 시도해 주세요. 어제까지의 기록은 그대로 남아 있습니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    state.errorMessage.value ?: "오류 코드 CARD-503",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            TmtnPrimaryButton(text = "다시 시도", onClick = { scope.launch { state.loadToday() } })
            // TODO: C01(챌린지 진행 화면, C그룹) 아직 안 만들어서 지금은 동작 없음
            androidx.compose.material3.TextButton(onClick = { }) {
                Text("오늘은 직접 행동 고르기", style = TmtnType.label, color = colors.primary)
            }
        }
    }
}
