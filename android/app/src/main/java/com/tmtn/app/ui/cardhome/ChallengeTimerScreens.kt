package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTonalButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun formatMmSs(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

/** ⚠️ 2026-09-04 반영: "타이머형은 무조건 분 단위"라고 target_value * 60을 그대로 썼는데,
 * CSV 200개 중 SELF_TIMER 42개 가운데 4개는 단위가 "초"였음(예: "주먹 쥐고 잠깐 버티기"
 * 목표 3초) - 그 4개가 3초 목표인데 3분(180초)으로 계산되던 버그. unit 문자열을 실제로
 * 보고 "초"면 그대로, 그 외(분 등)면 60을 곱함. */
private fun targetSecondsFor(card: com.tmtn.app.network.model.CardRevealResponse): Int =
    if (card.unit.contains("초")) card.target_value else card.target_value * 60

/** Figma C01 · 체크형 챌린지 - 자가 확인 후 직접 "완료하기" 눌러야 기록됨. */
@Composable
fun CheckChallengeScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    val material = MATERIAL_NAMES[card.five_element]

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 행동", onBack = { state.step.value = CardHomeStep.REVEALED })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(card.title, style = TmtnType.headline, color = colors.onSurface)

            if (material != null) {
                Row(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${material.first} 1개", style = TmtnType.label, color = colors.onSurface)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("완료 기준", style = TmtnType.label, color = colors.onSurfaceVariant)
                // ⚠️ 2026-09-06 QA(P1-5) 반영: card.target_value/unit이 카드·추천 이유
                // 화면엔 나오는데 정작 실행 화면(여기)에선 한 번도 안 쓰였음("5회" 목표가
                // 실행 중엔 안 보임) - TIMER형이 이미 쓰는 "목표 N" 패턴을 그대로 재사용.
                Text("목표 ${card.target_value}${card.unit}", style = TmtnType.body, color = colors.onSurface)
                Text(card.guide_text, style = TmtnType.body, color = colors.onSurface)
            }

            // ⚠️ 2026-09-06 QA(P2) 반영: 바로 위 박스의 "직접 확인해 주세요"(제거함)와
            // 아래 문구가 같은 말을 두 번 하고 있었음 - 하나로 정리.
            Text(
                "자동으로 완료되지 않습니다. 직접 확인해야 기록됩니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))
            TmtnPrimaryButton(
                text = "${card.title} · 완료하기",
                // ⚠️ 예전엔 여기서 바로 completeTimerChallenge()를 불러서 클릭 한 번에 실제
                // 완료 처리(서버 반영)까지 끝나버렸음. 자가진단 없이 바로 완료돼서 실수로
                // 누르기 쉬웠음 - C01b(자가진단 확인) 화면을 하나 끼워서 한 번 더 확인하게 함.
                onClick = { state.step.value = CardHomeStep.CHALLENGE_CHECK_CONFIRM },
            )
            // ⚠️ 2026-09-04 멘토링 반영: "오늘은 쉬어가기"는 홈 화면에만 남기고 다른 화면
            // 전부에서 없애기로 방향이 정해짐 - 여기(CHECK형 미션 화면)도 제거.
        }
    }
}

/** Figma C01b(신규) · CHECK형 자가진단 확인 - "완료하기"를 누른 뒤 실제로 서버에 완료 처리를
 * 보내기 전에 한 번 더 "정말 했는지" 확인하는 단계. 여기서 확정해야만 completeTimerChallenge가
 * 호출됨(그 전까진 서버에 아무 것도 반영되지 않음). */
@Composable
fun CheckCompleteConfirmScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "완료 확인", onBack = { state.step.value = CardHomeStep.CHALLENGE_CHECK })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("정말 완료했나요?", style = TmtnType.headline, color = colors.onSurface)
            Text(card.title, style = TmtnType.bodyLarge, color = colors.onSurfaceVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("완료 기준", style = TmtnType.label, color = colors.onSurfaceVariant)
                Text(card.guide_text, style = TmtnType.body, color = colors.onSurface)
            }
            Text(
                "한 번 더 확인하고 완료해 주세요. 완료 후에는 되돌릴 수 없어요.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))
            TmtnPrimaryButton(
                text = "네, 완료했어요",
                onClick = { scope.launch { state.completeTimerChallenge() } },
            )
            TmtnTextButton(
                text = "아니요, 다시 확인할게요",
                onClick = { state.step.value = CardHomeStep.CHALLENGE_CHECK },
            )
        }
    }
}

/** Figma C02 · 타이머형 · 시작 전 */
@Composable
fun TimerStartScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    val material = MATERIAL_NAMES[card.five_element]

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 행동", onBack = { state.step.value = CardHomeStep.REVEALED })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(card.title, style = TmtnType.headline, color = colors.onSurface)
            if (material != null) {
                Text(
                    "${material.first} 1개 · ${material.second}",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "아직 시작하지 않았어요", style = TmtnType.label, color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .background(colors.background, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Text("0:00", style = TmtnType.display, color = colors.onSurface)
                Text("목표 ${card.target_value}${card.unit}", style = TmtnType.body, color = colors.onSurfaceVariant)
                // ⚠️ 2026-09-06 QA(P1-6) 반영: 진행 중 화면의 "빈 트랙" 배경과 똑같은 색이라
                // 시작 전인데도 "이미 꽉 찬 것"처럼 보였음 - 더 옅은 색으로 구분되게 함.
                Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(colors.outlineVariant, RoundedCornerShape(4.dp)))
                Text("0% · 시작하면 시간이 쌓여요", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("완료 기준", style = TmtnType.label, color = colors.onSurfaceVariant)
                Text(
                    "목표 ${card.target_value}${card.unit}을 모두 채운 뒤 완료 버튼을 눌러야 기록됩니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
                Text("시간이 다 되어도 자동으로 완료되지 않습니다.", style = TmtnType.body, color = colors.onSurface)
            }

            TmtnPrimaryButton(text = "시작하기", onClick = { scope.launch { state.startTimer() } })
            // ⚠️ 2026-09-06 QA(P2) 반영: 같은 화면 안에서 "쌓여요"(반말)와 "기록됩니다"(존댓말)가
            // 섞여있던 것 중 하나 - 존댓말로 통일.
            TmtnTextButton(text = "오늘은 하기 어려워요", onClick = { state.step.value = CardHomeStep.REVEALED })
        }
    }
}

/** Figma C03 · 타이머형 · 진행 중 (+ C19 중단 확인 다이얼로그 통합) */
@Composable
fun TimerRunningScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    val targetSeconds = targetSecondsFor(card)
    val elapsed = state.timerElapsedSeconds.value
    val progress = (elapsed.toFloat() / targetSeconds).coerceIn(0f, 1f)
    val remaining = (targetSeconds - elapsed).coerceAtLeast(0)

    // ⚠️ 2026-09-06 QA(P0-1) 반영: 앱이 백그라운드로 갔다 돌아올 때(Activity onResume)
    // 서버의 실제 경과 시간으로 다시 맞춤 - 화면이 안 보이던 동안 로컬 카운트가 멈춰있던
    // 문제를 보정. Activity가 재생성되지 않고 그냥 pause/resume만 되는 흔한 경우(다른 앱
    // 잠깐 봤다가 돌아오기)에 특히 중요함 - 그 경우는 REVEALED를 거치지 않아서 기존
    // refreshRevealedCard() 자동 새로고침도 안 걸림.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { state.syncTimerElapsedFromServer() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.timerIsPaused.value) {
        // ⚠️ 2026-09-04 반영: 목표 시간을 다 채운 뒤에도 타이머가 계속 흘렀음(QA에서도
        // 지적됐던 부분) - 목표에 도달하면 멈추게 함.
        while (!state.timerIsPaused.value && state.timerElapsedSeconds.value < targetSeconds) {
            delay(1000)
            state.timerElapsedSeconds.value += 1
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(
            title = "오늘의 행동",
            onBack = { state.step.value = CardHomeStep.REVEALED },
            trailing = {
                TextButton(onClick = { state.showQuitDialog.value = true }) {
                    // ⚠️ 2026-09-06 QA(P1-6) 반영: 앱 팔레트에서 빨강은 error 전용인데,
                    // "중단"은 실제 오류가 아니라 그냥 진행을 멈추는 액션이라 error색이 안 맞음.
                    Text("중단", style = TmtnType.label, color = colors.onSurfaceVariant)
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(card.title, style = TmtnType.headline, color = colors.onSurface)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    // ⚠️ 2026-09-06 QA(P1-6) 반영: 주황은 "오늘"에만 쓰는 색인데 이 카드
                    // 테두리에도 쓰여서 규칙 위반이었음.
                    .border(3.dp, colors.onSurface, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "시간이 흐르고 있어요", style = TmtnType.label, color = colors.onSurface,
                    modifier = Modifier
                        .background(colors.rewardContainer, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Text(formatMmSs(elapsed), style = TmtnType.display, color = colors.onSurface)
                Text(
                    "목표 ${card.target_value}${card.unit} · 남은 시간 ${formatMmSs(remaining)}",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
                Box(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(colors.outline, RoundedCornerShape(4.dp)))
                    Box(
                        // ⚠️ 2026-09-06 QA(P1-6) 반영: 진행바 채움도 주황이었음 - 무채색으로.
                        modifier = Modifier.fillMaxWidth(progress).height(10.dp)
                            .background(colors.onSurface, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    // ⚠️ 2026-09-04 반영: 100%를 채웠는데도 "채우면"이라고 하던 문구 정정.
                    if (progress >= 1f) {
                        "100% · 목표를 다 채웠어요. 완료 버튼을 눌러주세요."
                    } else {
                        "${(progress * 100).toInt()}% · 목표를 다 채우면 완료할 수 있어요"
                    },
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "앱을 잠시 닫아도 지금까지 쌓인 시간은 남습니다.\n다시 열면 이어서 진행합니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TmtnTonalButton(
                text = "일시정지",
                onClick = { scope.launch { state.pauseTimer() } },
                // ⚠️ 2026-09-04 반영: 목표 시간을 다 채우면 "완료하기"만 누를 수 있게, 일시정지는 막음.
                enabled = elapsed < targetSeconds,
            )
            TmtnPrimaryButton(
                text = "완료하기",
                onClick = { scope.launch { state.completeTimerChallenge() } },
                enabled = elapsed >= targetSeconds,
            )
        }
    }

    if (state.showQuitDialog.value) {
        QuitDialog(state, scope)
    }
}

/** Figma C04 · 타이머형 · 일시정지 */
@Composable
fun TimerPausedScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value ?: return
    val targetSeconds = targetSecondsFor(card)
    val elapsed = state.timerElapsedSeconds.value
    val progressPercent = ((elapsed.toFloat() / targetSeconds) * 100).toInt()

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 행동", onBack = { scope.launch { state.resumeTimer() } })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(card.title, style = TmtnType.headline, color = colors.onSurface)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "잠시 멈춰 있어요", style = TmtnType.label, color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .background(colors.background, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Text(formatMmSs(elapsed), style = TmtnType.display, color = colors.onSurface)
                Text("목표 ${card.target_value}${card.unit}", style = TmtnType.body, color = colors.onSurfaceVariant)
                Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(colors.outline, RoundedCornerShape(4.dp)))
                Text("${progressPercent}%에서 멈춰 있어요", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "지금까지 기록된 ${formatMmSs(elapsed)}은 그대로 남습니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TmtnPrimaryButton(text = "이어서 하기", onClick = { scope.launch { state.resumeTimer() } })
            TmtnOutlinedButton(text = "오늘은 여기까지 할래", onClick = { state.showQuitDialog.value = true })
        }
    }

    if (state.showQuitDialog.value) {
        QuitDialog(state, scope)
    }
}

/** Figma C05 · 완료 처리 중 */
@Composable
fun ChallengeProcessingScreen(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(card?.title ?: "", style = TmtnType.headline, color = colors.onSurface)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ⚠️ 2026-09-04 반영: SENSOR형(걷기/뛰기/계단)도 완료 처리는 이 화면을 그대로
            // 거치는데, 여기서 무조건 분:초 타이머를 보여줘서 "300m 뛰기" 같은 거리 미션에도
            // 엉뚱한 시간이 표시되고 있었음. exec_type이 TIMER일 때만 시간을 보여줌.
            if (card?.exec_type == "TIMER") {
                Text(formatMmSs(state.timerElapsedSeconds.value), style = TmtnType.display, color = colors.onSurface)
            }
            Text("목표를 모두 채웠어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
        }
        Text("기록을 저장하고 있어요…\n잠시만 기다려 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
    }
}

/** Figma C19 · 챌린지 중단 확인 다이얼로그 */
@Composable
private fun QuitDialog(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    AlertDialog(
        onDismissRequest = { state.showQuitDialog.value = false },
        containerColor = colors.surface,
        title = { Text("오늘 미션을 그만둘까요?", style = TmtnType.bodyLarge, color = colors.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "한 번 그만두면 오늘은 이 카드를 다시 시작할 수 없어요. " +
                        "지금까지의 ${formatMmSs(state.timerElapsedSeconds.value)}로는 완료가 되지 않습니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
                Text(
                    "오늘은 미완료로 남아 연속 기록이 끊깁니다. " +
                        "기록을 이어가고 싶으면 홈에서 '오늘은 쉬어가기'를 눌러 주세요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Text(
                "오늘은 그만두기", style = TmtnType.label, color = colors.error,
                // ⚠️ 2026-09-06 QA(접근성) 반영: 터치 영역이 48dp 미만이었음 - 최소 영역 확보.
                modifier = Modifier.clickable { scope.launch { state.quitChallenge() } }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center)
                    .padding(8.dp),
            )
        },
        dismissButton = {
            Text(
                "계속하기", style = TmtnType.label, color = colors.primary,
                modifier = Modifier.clickable { state.showQuitDialog.value = false }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center)
                    .padding(8.dp),
            )
        },
    )
}
