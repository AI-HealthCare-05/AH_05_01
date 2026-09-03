package com.tmtn.app.ui.reference

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** E01~E06 "참고"(틈튼지수) 탭 전체를 관리하는 최상위 컴포저블. 다른 탭 흐름과 같은 패턴.
 *
 * ⚠️ 2026-09-02: 뼈대(스켈레톤) 단계 — score.is_mock이 항상 true. 실제 예측 모델이
 * 확정되면 서비스 레이어(백엔드)만 바뀌고 이 화면들은 그대로 쓸 수 있게 필드 계약을
 * Figma 핸드오프(tmtn-handoff/HANDOFF.md §3.7, FIELDS.csv) 그대로 맞춰서 만듦.
 *
 * ⚠️ E06("이 지수에 대하여")은 HANDOFF.md 기준 이 플로우 안에 진입 버튼이 없음(마이/설정
 * 쪽에 있을 것으로 추정) — 화면 자체는 ReferenceDetailScreens.kt에 만들어뒀지만 아직
 * 어디서도 안 불러서, F그룹(내정보) 작업할 때 연결해야 함.
 */
@Composable
fun ReferenceFlow(onGoPickCard: () -> Unit, onOpenMyInfo: () -> Unit, onImmersiveChange: (Boolean) -> Unit) {
    val state = remember { ReferenceState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { state.loadScore() }

    val immersiveSteps = setOf(ReferenceStep.DETAIL, ReferenceStep.FACTORS, ReferenceStep.INPUTS, ReferenceStep.ABOUT)
    LaunchedEffect(state.step.value) {
        onImmersiveChange(state.step.value in immersiveSteps)
    }

    BackHandler(enabled = state.step.value != ReferenceStep.SUMMARY && state.step.value != ReferenceStep.INELIGIBLE) {
        state.goBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.step.value) {
            ReferenceStep.LOADING -> LoadingBox()
            ReferenceStep.SUMMARY -> ReferenceSummaryScreen(state)
            ReferenceStep.INELIGIBLE -> ReferenceIneligibleScreen(state, onGoPickCard)
            ReferenceStep.DETAIL -> ReferenceDetailScreen(state)
            ReferenceStep.FACTORS -> ReferenceFactorsScreen(state)
            ReferenceStep.INPUTS -> ReferenceInputsScreen(state, scope, onOpenMyInfo)
            ReferenceStep.ABOUT -> ReferenceAboutScreen(state)
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalTmtnColors.current.primary)
    }
}

/** Figma E01 · 참고 · 홈. 하단 내비 있음. */
@Composable
fun ReferenceSummaryScreen(state: ReferenceState) {
    val colors = LocalTmtnColors.current
    val score = state.score.value ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "참고", style = TmtnType.title, color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("틈튼지수", style = TmtnType.title, color = colors.onSurface)
                    Text(
                        "자세히 >", style = TmtnType.label, color = colors.onSurfaceVariant,
                        modifier = Modifier.noRippleClickable { state.openDetail() },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${score.value}", style = TmtnType.display, color = colors.onSurface)
                    Box(
                        modifier = Modifier.background(colors.secondaryContainer, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(score.band_label + " 구간", style = TmtnType.caption, color = colors.onSurface)
                    }
                }
                ScoreGaugeBar(value = score.value)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("관심", style = TmtnType.caption, color = colors.onSurfaceVariant)
                    Text("보통", style = TmtnType.caption, color = colors.onSurfaceVariant)
                    Text("양호", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
                InfoRow("기간", score.period_label)
                score.change_reason?.let { InfoRow("달라진 이유", it) }
                InfoRow("데이터 출처", score.data_source)
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.disabledContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("이 수치는 건강 상태를 진단하지 않습니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text("생활 습관을 돌아보는 참고용 지표입니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            TmtnOutlinedButton(
                text = "계산에 쓰인 값 보기",
                onClick = { state.openInputs() },
            )
        }
    }
}

/** Figma E05 · 데이터 부족 · 산출 불가. 하단 내비 있음. */
@Composable
fun ReferenceIneligibleScreen(state: ReferenceState, onGoPickCard: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "참고", style = TmtnType.title, color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("아직 지수를 낼 수 없어요", style = TmtnType.headline, color = colors.onSurface)
            Text(
                "행동 기록이 아직 모자랍니다. 최근 7일 중 ${state.eligibleRequiredDaysLabel.value} 이상 기록되면 지수를 낼 수 있어요.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoRow("최근 7일 기록", state.eligibleRecordedDaysLabel.value)
                InfoRow("필요한 기록", state.eligibleRequiredDaysLabel.value)
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.disabledContainer, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                Text(
                    "몸 정보와 운동량은 이미 받았습니다. 오늘 카드를 하나 완료하면 기록이 하루 늘어나요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
            TmtnPrimaryButton(text = "오늘의 카드 고르러 가기", onClick = onGoPickCard)
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    val colors = LocalTmtnColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TmtnType.body, color = colors.onSurfaceVariant)
        Text(
            value, style = TmtnType.body, color = colors.onSurface, textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** E01/E02 공용 게이지 - 관심(0~39)/보통(40~69)/양호(70~100) 3구간 트랙 + 현재값 마커.
 * ⚠️ marker_left_px를 서버가 내려줄 수 있게 DTO엔 넣어뒀지만 지금은 항상 null(뼈대 단계)
 * 이라 클라이언트에서 value 비율로 직접 계산함. */
@Composable
internal fun ScoreGaugeBar(value: Int) {
    val colors = LocalTmtnColors.current
    val markerFraction = (value.coerceIn(0, 100) / 100f)
    Box(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp).align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(0.39f, 0.30f, 0.31f).forEach { weight ->
                Box(
                    modifier = Modifier.weight(weight).fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp)).background(colors.disabledContainer),
                )
            }
        }
        // 마커: 전체 폭의 markerFraction 지점에 오도록, 그 폭만큼의 상자를 만들고
        // 그 안에서 오른쪽 끝에 얇은 막대를 붙임.
        Box(modifier = Modifier.fillMaxWidth(fraction = markerFraction.coerceIn(0.02f, 1f)).height(14.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.secondary),
            )
        }
    }
}

/** 클릭 시 잔물결(ripple) 효과 없이 텍스트/아이콘 링크처럼 쓰기 위한 확장. */
@Composable
internal fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

