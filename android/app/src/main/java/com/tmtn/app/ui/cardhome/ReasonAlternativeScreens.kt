package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnChip
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/**
 * Figma B10 · 카드 상세 · 추천 이유.
 * ⚠️ "왜 이 카드를 골랐을까" 근거 문구는 아직 추천 이유 생성 로직/API가 없어서
 * Figma 예시 문구를 그대로 보여주는 정적 화면임. 실제 추천 근거 API 생기면 교체할 것.
 */
@Composable
fun ReasonDetailScreen(state: CardHomeState, onBack: () -> Unit, onStartAction: () -> Unit) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value
    // ⚠️ "다시 보기"로 들어온 완료된 미션은 여기서도 대체 미션 요청(B11)으로 못 넘어가게 막음.
    // continueTodayMission()이 완료 건은 COMPLETED로 먼저 보내지만, 이 화면에 들어온 뒤
    // 서버 쪽에서 상태가 바뀌는 경우까지 대비해 카드 자체의 state도 한 번 더 확인함.
    // COMPLETED뿐 아니라 SKIPPED(중단으로 끝낸 미션)도 대체 미션 요청으로 못 넘어가게 같이 막음.
    val isCompleted = card?.state == "COMPLETED" || card?.state == "SKIPPED"

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 카드", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("왜 이 카드를 골랐을까", style = TmtnType.label, color = colors.onSurface)
            listOf(
                "최근 7일 동안 유산소 기록이 다른 항목보다 적었어요.",
                "지금 시간대에 완료한 기록이 가장 많았어요.",
                "지난주에 비슷한 행동을 끝까지 마쳤어요.",
            ).forEach { reason ->
                Text("· $reason", style = TmtnType.body, color = colors.onSurface)
            }

            Text("목표", style = TmtnType.label, color = colors.onSurface)
            InfoCard(title = card?.title ?: "", caption = "${card?.target_value ?: ""}${card?.unit ?: ""}")

            Text("완료 기준", style = TmtnType.label, color = colors.onSurface)
            Text(card?.guide_text ?: "목표를 다 채우면 완료입니다.", style = TmtnType.body, color = colors.onSurface)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.rewardContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("안전하게 하려면", style = TmtnType.bodyLarge, color = colors.onSurface)
                Text(
                    "가슴이 아프거나 어지러우면 바로 멈추고 의료진과 상담해 주세요.",
                    style = TmtnType.body, color = colors.onSurface,
                )
                Text("몸이 불편한 날은 건너뛰어도 괜찮습니다.", style = TmtnType.body, color = colors.onSurface)
            }
            Text("추천 기준 v1.2 · 미션 규칙 v1.0", style = TmtnType.caption, color = colors.onSurfaceVariant)

            if (!isCompleted) {
                TmtnPrimaryButton(text = "이 행동 시작하기", onClick = onStartAction)
            }
            if (isCompleted) {
                Text(
                    if (card?.state == "SKIPPED") "오늘은 이미 쉬어가기로 했어요." else "오늘 몫은 이미 완료했어요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            } else {
                TmtnTextButton(text = "오늘은 하기 어려워요", onClick = { state.step.value = CardHomeStep.ALTERNATIVE_REQUEST })
            }
        }
    }
}

/** Figma B11 · 대체 미션 요청. ⚠️ 실제 대체 로직/API가 없어서 이유 선택 후 B12로 이동만 함(고정 예시). */
@Composable
fun AlternativeRequestScreen(state: CardHomeState, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    var selectedReason by remember { mutableStateOf<String?>(null) }
    val reasons = listOf("시간이 없어요", "장소가 마땅치 않아요", "몸이 무거워요", "날씨가 안 맞아요")

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 카드", onBack = onBack)

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("오늘은 어떤 게 어려워?", style = TmtnType.headline, color = colors.onSurface)
            Text(
                "이유를 알려주면 같은 재료를 받을 수 있는 다른 방법으로 바꿔 줄게.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    TmtnChip(text = reason, selected = selectedReason == reason, onClick = { selectedReason = reason })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text(
                    "카드를 다시 뽑는 것이 아니라 실행 방법만 바꿉니다. 받는 재료와 완료 기록은 그대로입니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
            }
            Text("하루에 한 번만 바꿀 수 있습니다.", style = TmtnType.body, color = colors.onSurfaceVariant)

            TmtnPrimaryButton(
                text = "다른 방법 보기",
                onClick = { state.step.value = CardHomeStep.ALTERNATIVE_APPLIED },
                enabled = selectedReason != null,
            )
            TmtnTextButton(text = "그냥 할래", onClick = onBack)
        }
    }
}

/** Figma B12 · 대체 미션 적용 완료. ⚠️ 위와 동일하게 실제 교체 API 없음, 고정 예시 문구. */
@Composable
fun AlternativeAppliedScreen(state: CardHomeState, onStartAction: () -> Unit) {
    val colors = LocalTmtnColors.current
    val card = state.revealedCard.value

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘의 카드", onBack = { state.step.value = CardHomeStep.REVEALED })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("이렇게 바꿨어", style = TmtnType.headline, color = colors.onSurface)

            InfoCard(title = card?.title ?: "", caption = "바꾸기 전", faded = true)
            InfoCard(title = "앉아서 종아리 들어올리기 ${card?.target_value ?: 8}분", caption = "바꾼 뒤")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text(
                    "받는 재료와 완료 기록은 그대로입니다. 오늘은 더 바꿀 수 없습니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
            }

            TmtnPrimaryButton(text = "이 행동 시작하기", onClick = onStartAction)
        }
    }
}

@Composable
private fun InfoCard(title: String, caption: String, faded: Boolean = false) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(caption, style = TmtnType.caption, color = colors.onSurfaceVariant)
        Text(
            title, style = TmtnType.bodyLarge,
            color = if (faded) colors.onSurfaceVariant else colors.onSurface,
        )
    }
}
