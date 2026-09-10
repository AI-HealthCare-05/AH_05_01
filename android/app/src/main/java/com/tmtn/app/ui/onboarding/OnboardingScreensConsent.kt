package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Figma A06 · 필수 동의 (node 100:107)
 * ⚠️ 2026-09-02: 화면문구_동의.md 기준으로 건강정보·위치정보 동의 항목 추가함.
 * 건강정보(필수)와 위치정보(선택)는 각각 개인정보보호법 §23, 위치정보법 §15로
 * 근거 법이 달라서 절대 한 줄로 합치면 안 됨 - 문서에 명시된 원칙.
 * GPS 기능이 없는 빌드에서는 "위치정보 수집·이용" 줄을 통째로 빼야 함(안 쓰는 동의는 받으면 안 됨).
 */
@Composable
fun A06ConsentScreen(state: OnboardingState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    var tos by state.agreeTermsOfService
    var privacy by state.agreePrivacyPolicy
    var age14 by state.agreeAgeOver14
    var healthUsage by state.agreeHealthDataUsage
    var locationUsage by state.agreeLocationUsage
    var indexAnalysis by state.agreeHealthDataAnalysis
    var push by state.agreeMarketingPush

    val allChecked = tos && privacy && age14 && healthUsage && locationUsage && indexAnalysis && push

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "약관 동의", onBack = if (state.accountCreated) null else {
            { state.step.value = OnboardingStep.A04_VERIFY }
        })
        StepProgressHeader(3, 3, "약관 동의")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 모두 동의
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .toggleable(value = allChecked, role = Role.Checkbox, onValueChange = {
                        tos = it; privacy = it; age14 = it
                        healthUsage = it; locationUsage = it; indexAnalysis = it; push = it
                    })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allChecked,
                    onCheckedChange = null,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("모두 동의합니다", style = TmtnType.bodyLarge, color = colors.onSurface)
            }

            Column {
                TmtnCheckRow(
                    label = "[필수] 만 14세 이상입니다", checked = age14, onCheckedChange = { age14 = it },
                )
                TmtnCheckRow(
                    label = "[필수] 서비스 이용약관", checked = tos, onCheckedChange = { tos = it },
                    onViewClick = { state.step.value = OnboardingStep.A14_TERMS_DETAIL },
                )
                Text(
                    "틈튼을 이용하는 데 필요한 기본 약속입니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                TmtnCheckRow(
                    label = "[필수] 개인정보 수집 · 이용 동의", checked = privacy, onCheckedChange = { privacy = it },
                    onViewClick = { state.step.value = OnboardingStep.A14_TERMS_DETAIL },
                )
                Text(
                    "이름 · 생년월일(연·월) · 성별을 받습니다. 광고나 외부 제공에 쓰지 않습니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                TmtnCheckRow(
                    label = "[필수] 건강정보 수집 · 이용 동의", checked = healthUsage,
                    onCheckedChange = { healthUsage = it },
                    onViewClick = { state.step.value = OnboardingStep.A14_TERMS_DETAIL },
                )
                Text(
                    "키 · 몸무게 · 운동 습관을 받습니다. 이용자에게 맞는 난이도를 고르기 위해서입니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                TmtnCheckRow(
                    label = "[선택] 위치정보 수집 · 이용 동의", checked = locationUsage,
                    onCheckedChange = { locationUsage = it }, optional = true,
                    onViewClick = { state.step.value = OnboardingStep.A14_TERMS_DETAIL },
                )
                Text(
                    "달리기·걷기 미션을 하는 동안에만 위치를 받아 거리를 잽니다. 어디를 걸었는지 경로는 저장하지 않습니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                TmtnCheckRow(
                    label = "[선택] 틈튼지수 산출을 위한 분석", checked = indexAnalysis,
                    onCheckedChange = { indexAnalysis = it }, optional = true,
                    onViewClick = { state.step.value = OnboardingStep.A14_TERMS_DETAIL },
                )
                Text(
                    "동의하지 않으셔도 챌린지는 그대로 이용하실 수 있습니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                TmtnCheckRow(
                    label = "[선택] 카드 · 미션 알림 받기", checked = push,
                    onCheckedChange = { push = it }, optional = true,
                )
            }

            TmtnPrimaryButton(
                text = if (state.isLoading.value) "가입을 마무리하고 있어요" else "동의하고 가입 완료",
                onClick = { scope.launch { state.submitConsents() } },
                enabled = state.allMandatoryAgreed && !state.isLoading.value,
                disabledReason = if (!state.allMandatoryAgreed) "필수 항목을 확인해 주세요." else null,
            )
        }
    }
}
