package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    // ⚠️ 이 화면은 COMPLETED(완료)뿐 아니라 SKIPPED(중단으로 끝낸 미션)도 같이 씀 —
    // 둘 다 "오늘은 다시 시작 못 함"이라는 점은 같지만 문구/보상 표시는 달라야 함.
    val isSkipped = card?.state == "SKIPPED"

    // 완료 화면에 들어올 때마다 오늘 회고를 이미 남겼는지 확인 - 남겼으면 "한 줄 남기기"를 숨김.
    // SKIPPED(중단)는 애초에 회고 대상이 아니라서 확인 자체를 안 함.
    LaunchedEffect(isSkipped) {
        if (!isSkipped) state.checkTodayMemo()
        // ⚠️ 2026-09-11 추가 - 틈새 운동 섹션에 쓸 오늘 현황(used/remaining)을 같이 불러옴.
        // SKIPPED(포기)는 카드 자체가 미완료라 서버가 어차피 card_completed=false를 주므로
        // 굳이 숨기지 않고 그대로 불러도 안전함(화면에서 섹션 노출만 isSkipped로 막음).
        if (!isSkipped) state.loadExerciseMissionsToday()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 카드", onBack = { state.step.value = CardHomeStep.HOME })

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isSkipped || card == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("오늘은 여기까지", style = TmtnType.display, color = colors.onSurface, textAlign = TextAlign.Center)
                    card?.let {
                        Text(it.title, style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
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
                } // Column(surface 대체 박스) 닫기
            } else {
                // ⚠️ 2026-09-12 반영: V19 C08 - 완료 카드도 RevealScreen과 같은
                // TarotCardFrame(NoteCard)을 재사용. card.state == "COMPLETED"라서
                // NoteCard 내부에서 "실천 완료 · 받았어요" 문구로 자동 전환됨.
                NoteCard(card, state.displayDateLabel())
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

            // ⚠️ 2026-09-11 추가 - 틈새 운동(TMtn_UI_V17 §5, B17). 완료 카드 아래에
            // 추가로 노출. SKIPPED(포기)면 애초에 카드가 미완료라 서버가 시작을 막으니
            // 화면에서도 굳이 안 보여줌.
            if (!isSkipped) {
                val today = state.exerciseMissionsToday.value
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("틈새 운동", style = TmtnType.title, color = colors.onSurface)
                        if (today != null) {
                            Text("${today.used} / ${today.limit}회", style = TmtnType.body, color = colors.onSurfaceVariant)
                        }
                    }
                    Text("조금 더 움직이고 싶은 날, 재료를 하나 더.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                    androidx.compose.material3.TextButton(onClick = { state.openExerciseMissionList() }) {
                        Text("틈새 운동 둘러보기", style = TmtnType.label, color = colors.primary)
                    }
                }
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
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
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

            Text("카드를 가져오지 못했어요", style = TmtnType.headline, color = colors.onSurface)
            Text(
                "잠시 뒤 다시 열어 주세요. 지금까지의 기록은 그대로 있어요.",
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
                    state.errorMessage.value ?: "연결을 확인한 뒤 다시 눌러 주세요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            TmtnPrimaryButton(text = if (state.isLoading.value) "다시 불러오는 중…" else "다시 시도", onClick = { scope.launch { state.loadToday() } }, enabled = !state.isLoading.value)
            com.tmtn.app.ui.onboarding.TmtnTonalButton("홈으로 돌아가기", { state.errorMessage.value = null; state.step.value = CardHomeStep.HOME })
        }
    }
}
