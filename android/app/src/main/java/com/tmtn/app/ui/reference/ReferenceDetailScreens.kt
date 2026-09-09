package com.tmtn.app.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma E02 · 틈튼지수 자세히. 하단 내비 없음(전면 몰입).
 * ⚠️ 2026-09-09 반영: 실모델(또래 백분위) 연동 - 기존 "관심/보통/양호" 구간 설명은 새
 * 계약에 없는 개념이라 제거하고, 대신 각 영역이 어떤 참고 표본과 비교됐는지 보여줌. */
@Composable
fun ReferenceDetailScreen(state: ReferenceState) {
    val colors = LocalTmtnColors.current
    val score = state.score.value ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "틈튼지수", onBack = { state.goBack() })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(score.compositeDisplay.text, style = TmtnType.display, color = colors.onSurface)
                Text(score.referenceCaution, style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("영역별 참고점수", style = TmtnType.title, color = colors.onSurface)
                score.components.forEach { component ->
                    InfoRow(
                        component.label,
                        component.absoluteReferenceScore?.let { "%.1f점".format(it) } ?: "계산 안 됨",
                    )
                }
                Text(
                    "참고점수가 바뀌어도 몸 상태가 바뀐 것은 아닙니다. 입력한 값과 최근 행동을 다시 계산한 결과입니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("데이터 출처", style = TmtnType.title, color = colors.onSurface)
                Text(score.modelVersion, style = TmtnType.body, color = colors.onSurface)
                Text(score.formulaVersion, style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            TmtnTextButton(text = "이번 계산에 반영된 항목 보기", onClick = { state.openFactors() })
        }
    }
}

/** Figma E03 · 이번 계산에 반영된 항목. 하단 내비 없음.
 * ⚠️ HANDOFF.md 정책: 항목별 기여도 %는 노출 안 함 — 막대 길이로만 표시. */
@Composable
fun ReferenceFactorsScreen(state: ReferenceState) {
    val colors = LocalTmtnColors.current
    val score = state.score.value ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "이번 계산에 반영된 항목", onBack = { state.goBack() })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("이번 계산에 반영된 항목", style = TmtnType.headline, color = colors.onSurface)

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                score.components.forEach { component ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(component.label, style = TmtnType.bodyLarge, color = colors.onSurface)
                        if (component.available) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)).background(colors.disabledContainer),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(
                                        fraction = ((component.absoluteReferenceScore ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
                                    )
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.onSurface),
                                )
                            }
                        }
                        Text(
                            if (component.available) {
                                "${component.rankDisplay.text} · ${COMPONENT_GUIDANCE[component.componentKey] ?: ""}"
                            } else {
                                "이번엔 값이 없어서 계산에서 뺐어요."
                            },
                            style = TmtnType.caption,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.disabledContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "점수가 낮은 영역의 안내부터 확인해 생활습관 개선 목표를 정해 보세요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
                Text(
                    "항목이 적게 반영됐다고 해서 문제가 있다는 뜻은 아닙니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            TmtnOutlinedButton(text = "값 다시 입력하기", onClick = { state.openInputs() })
        }
    }
}

/** Figma E04 · 계산에 쓰인 값. 하단 내비 없음.
 * ⚠️ 새 입력 화면을 따로 만들지 않음 — 이미 있는 F그룹(내정보)/A그룹(온보딩) 수정 화면으로
 * 보내는 걸로 뼈대를 잡음(onOpenMyInfo). 지금은 필드별로 정확히 어느 화면인지까지는 못
 * 나누고 "내 정보" 탭으로만 보냄 — F그룹 쪽 세부 화면이 준비되면 필드별 딥링크로 나눌 것. */
@Composable
fun ReferenceInputsScreen(
    state: ReferenceState,
    scope: CoroutineScope,
    onOpenHealthInfo: () -> Unit,
    onOpenExerciseInfo: () -> Unit,
) {
    val colors = LocalTmtnColors.current
    val inputs = state.scoreInputs.value

    LaunchedEffect(Unit) { state.loadInputs() }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "계산에 쓰인 값", onBack = { state.goBack() })

        if (inputs == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("틈튼지수는 아래 값으로 계산했어요.", style = TmtnType.body, color = colors.onSurfaceVariant)

            Text("몸 정보", style = TmtnType.title, color = colors.onSurface)
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InputRow("생년월일", inputs.birth_month_label ?: "입력 필요", onOpenHealthInfo)
                InputRow("성별", inputs.sex_label ?: "입력 필요", onOpenHealthInfo)
                InputRow("키", inputs.height_cm?.let { "${it.toInt()} cm" } ?: "입력 필요", onOpenHealthInfo)
                InputRow("몸무게", inputs.weight_kg?.let { "${it.toInt()} kg" } ?: "입력 필요", onOpenHealthInfo)
            }

            Text("일주일 운동량", style = TmtnType.title, color = colors.onSurface)
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InputRow("근력운동", inputs.strength_label ?: "입력 필요", onOpenExerciseInfo)
                InputRow("유산소 저강도", inputs.cardio_low_min?.let { "${it}분" } ?: "입력 필요", onOpenExerciseInfo)
                InputRow("유산소 중강도", inputs.cardio_moderate_min?.let { "${it}분" } ?: "입력 필요", onOpenExerciseInfo)
                InputRow("유산소 고강도", inputs.cardio_vigorous_min?.let { "${it}분" } ?: "입력 필요", onOpenExerciseInfo)
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.disabledContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "입력한 값은 이 계정에만 저장되고 광고나 외부 제공에 쓰지 않습니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
                Text("의료 목적으로 쓰이지 않습니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            TmtnPrimaryButton(
                text = "값 고치고 다시 계산",
                onClick = { scope.launch { state.recalculateAndReturnToSummary() } },
            )
        }
    }
}

@Composable
private fun InputRow(label: String, value: String, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier.fillMaxWidth().noRippleClickable(onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TmtnType.body, color = colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = TmtnType.body, color = colors.onSurface)
            Text(">", style = TmtnType.body, color = colors.onSurfaceVariant)
        }
    }
}

/** Figma E06 · 이 지수에 대하여. 하단 내비 없음.
 * ⚠️ HANDOFF.md: "E06 진입 경로가 없습니다" — 이 화면 자체는 만들어두지만 ReferenceFlow
 * 안에서 여기로 오는 버튼은 없음. F그룹(내정보 > 설정 > 도움말)에서 나중에 연결해야 함. */
@Composable
fun ReferenceAboutScreen(state: ReferenceState) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "이 지수에 대하여", onBack = { state.goBack() })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("틈튼지수는 진단이 아닙니다", style = TmtnType.title, color = colors.onSurface)
                Text(
                    "생활 습관 기록과 입력값을 모아 한 숫자로 보여 주는 참고 지표입니다. 몸 상태를 판단하거나 의료진의 소견을 대신하지 않습니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("이렇게 계산합니다", style = TmtnType.title, color = colors.onSurface)
                Text("· 최근 7일 행동 기록을 항목별로 모읍니다.", style = TmtnType.body, color = colors.onSurface)
                Text("· 입력한 값이 있으면 함께 넣어 다시 계산합니다.", style = TmtnType.body, color = colors.onSurface)
                Text(
                    "· 값이 없는 항목은 계산에서 빼고, 뺐다는 사실을 화면에 적습니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.disabledContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "출처 · 국민건강영양조사 기반 참고 모델 v1.2",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
                Text("문의 · 설정 > 도움말", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            TmtnTextButton(text = "개인정보 처리방침 보기", onClick = { /* TODO: F그룹 개인정보처리방침 화면과 연결 */ })
        }
    }
}
