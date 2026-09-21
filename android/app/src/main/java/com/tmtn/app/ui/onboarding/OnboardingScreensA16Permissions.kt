package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/**
 * Figma A16 · 자동 측정 권한 요청 (node 474:4241).
 * ⚠️ FLOWS.md엔 이 화면으로 들어오는 연결선이 안 그려져 있음 — A08 완료 시점에
 * 센서 권한이 이미 있으면 건너뛰고, 없을 때만 보여주는 조건부 화면으로 해석해서
 * OnboardingState.goToPermissionsOrSchedule()에서 그렇게 분기함.
 */
@Composable
fun A16PermissionsScreen(
    state: OnboardingState,
    onRequestPermissions: () -> Unit,
) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(
            title = "자동 측정 준비",
            onBack = { state.step.value = OnboardingStep.A08_EXERCISE },
            trailing = {
                TextButton(onClick = { state.step.value = OnboardingStep.A15_COMPLETE }) {
                    Text("나중에 하기", style = TmtnType.label, color = colors.primary)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "움직임을 함께\n셀 준비를 해요.",
                style = TmtnType.headline, color = colors.onSurface,
            )
            Text(
                "걷거나 계단을 오르는 움직임을 알아차리고, 실천한 만큼 기록할게요.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            PermissionCard(title = "신체 활동", required = true, description = "걸음과 움직이는 시간을 셀 때 사용해요. 아래 버튼을 누르면 휴대전화의 권한 창이 열려요.")
            PermissionCard(title = "위치", required = false, description = "거리를 재는 미션을 시작할 때 따로 요청해요. 걷는 시간이나 계단을 셀 때는 필요 없어요.")

            Text(
                "지금 건너뛰어도 괜찮아요. 미션을 할 때 허용하거나, 직접 체크를 선택할 수 있어요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )

            TmtnPrimaryButton(
                text = "신체 활동 권한 확인",
                onClick = {
                    onRequestPermissions()
                    state.step.value = OnboardingStep.A15_COMPLETE
                },
            )

        }
    }
}

@Composable
private fun PermissionCard(title: String, required: Boolean, description: String) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = TmtnType.label, color = colors.onSurface)
            Box(
                modifier = Modifier
                    .background(
                        if (required) colors.primary else colors.surface,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    if (required) "자동 측정에 필요" else "거리 미션에서",
                    style = TmtnType.caption,
                    color = if (required) colors.background else colors.onSurfaceVariant,
                )
            }
        }
        Text(description, style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}
