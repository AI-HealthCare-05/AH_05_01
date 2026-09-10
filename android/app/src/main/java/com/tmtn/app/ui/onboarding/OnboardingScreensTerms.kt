package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** Figma A14 · 약관 상세 보기 (node 282:4683). A06 여러 항목의 "보기"가 전부 여기로 옴. */
@Composable
fun A14TermsDetailScreen(state: OnboardingState) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "서비스 이용약관", onBack = { state.step.value = OnboardingStep.A06_CONSENT })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("버전 1.0 · 2026. 8. 1. 시행", style = TmtnType.caption, color = colors.onSurfaceVariant)

            TermsSection(
                title = "수집하는 항목",
                body = "이메일, 이름, 생년월일(연·월), 성별, 키, 몸무게, 주당 운동량, 그리고 행동 기록(완료 여부·시각·걸린 시간)을 받습니다.",
            )
            TermsSection(
                title = "이용 목적",
                body = "오늘의 카드를 고르고, 행동 기록을 보관하고, 틈튼지수를 계산하는 데 씁니다. 광고나 외부 제공에는 쓰지 않습니다.",
            )
            TermsSection(
                title = "보관 기간",
                body = "계정을 지우면 함께 지웁니다. 법령이 정한 최소 보관 기간이 있는 항목은 그 기간이 끝난 뒤 지웁니다.",
            )
            TermsSection(
                title = "동의를 거부할 권리",
                body = "필수 항목에 동의하지 않으면 서비스를 이용할 수 없습니다. 선택 항목은 거부해도 카드와 챌린지를 그대로 쓸 수 있습니다.",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "전문은 내 정보 · 개인정보 · 내 데이터에서 다시 볼 수 있습니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            TmtnOutlinedButton(text = "닫기", onClick = { state.step.value = OnboardingStep.A06_CONSENT })
        }
    }
}

@Composable
private fun TermsSection(title: String, body: String) {
    val colors = LocalTmtnColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = TmtnType.label, color = colors.onSurface)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(body, style = TmtnType.body, color = colors.onSurfaceVariant)
        }
    }
}
