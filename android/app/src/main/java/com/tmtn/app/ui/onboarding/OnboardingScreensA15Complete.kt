package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** Figma A15 · 온보딩 완료 (node 282:4788). A09/A16(건너뛰기·나중에)와 A10(맞추기)이 전부 여기로 옴. */
@Composable
fun A15CompleteScreen(state: OnboardingState, onOnboardingComplete: () -> Unit) {
    val colors = LocalTmtnColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(212.dp),
            contentAlignment = Alignment.Center,
        ) {
            // ⚠️ 2026-09-06 반영: A15(온보딩 완료) 배치표 그대로 - "손 흔들기" beaver_wave.
            Image(
                painter = painterResource(com.tmtn.app.R.drawable.beaver_wave),
                contentDescription = "손 흔들며 인사하는 비버",
                modifier = Modifier.height(212.dp),
            )
        }

        Text("준비가 끝났어요", style = TmtnType.display, color = colors.onSurface, textAlign = TextAlign.Center)
        Text(
            "오늘의 카드를 골라보세요.",
            style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("반영한 값", style = TmtnType.label, color = colors.onSurface)
            SummaryRow("생년월", "${state.birthYear.value}년 ${state.birthMonth.value}월")
            SummaryRow("성별", if (state.gender.value == "MALE") "남성" else "여성")
            SummaryRow("키", "${state.heightCm.value.ifBlank { "-" }} cm")
            SummaryRow("몸무게", "${state.weightKg.value.ifBlank { "-" }} kg")
            SummaryRow(
                "일주일 운동량",
                "근력 ${strengthLabel(state.strengthWeeklyCount.value)} · 유산소 " +
                    "${state.aerobicLowMinutes.value + state.aerobicModerateMinutes.value + state.aerobicHighMinutes.value}분",
            )
        }

        Text(
            "값은 내 정보에서 언제든 고칠 수 있어요.",
            style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        TmtnPrimaryButton(text = "오늘의 카드 보러 가기", onClick = onOnboardingComplete)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val colors = LocalTmtnColors.current
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale * com.tmtn.app.ui.theme.LocalTmtnTextScale.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (scale > 1.3f || maxWidth < 300.dp) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text(value, style = TmtnType.body, color = colors.onSurface)
            }
        } else Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TmtnType.body, color = colors.onSurfaceVariant)
            Text(value, style = TmtnType.body, color = colors.onSurface, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

private fun strengthLabel(count: Int): String = if (count == 0) "안 함" else "주 ${count}회"
