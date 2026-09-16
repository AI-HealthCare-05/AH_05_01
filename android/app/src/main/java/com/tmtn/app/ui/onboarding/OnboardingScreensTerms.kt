package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.legal.*
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

@Composable
fun A14TermsDetailScreen(state: OnboardingState) {
    TermsDetailScreen(state.legalDocument.value) { state.step.value = OnboardingStep.A06_CONSENT }
}

/** Reading a document never changes its consent. Each document opens at the top. */
@Composable
fun TermsDetailScreen(document: LegalDocument = LegalDocument.TERMS, onBack: () -> Unit) = key(document) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize().testTag("legal-${document.name}")) {
        TmtnTopBar(title = document.title, onBack = onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("항목별 안내 · 운영자 검토본", style = TmtnType.caption, color = colors.onSurfaceVariant)
            document.sections().forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(section.title, style = TmtnType.sectionHeading, color = colors.onSurface,
                        modifier = Modifier.semantics { heading() })
                    Text(section.body, style = TmtnType.body, color = colors.onSurface)
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Text("운영자 $LegalOperator\n개인정보·서비스 문의 $LegalContact", style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text("이 안내는 검토본이에요. 확정본의 시행일과 변경 내용은 공개 시 안내할 예정이에요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant)
            TmtnOutlinedButton("닫기", onClick = onBack)
        }
    }
}
