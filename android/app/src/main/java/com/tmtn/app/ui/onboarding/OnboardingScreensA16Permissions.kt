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
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "자동 측정 권한을 확인해 주세요",
                style = TmtnType.headline, color = colors.onSurface,
            )
            Text(
                "걷기·계단·거리를 직접 입력하지 않아도 되도록 기기 센서를 씁니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            PermissionCard(title = "신체 활동", required = true, description = "걸음·계단을 세기 위해 필요합니다. 없으면 자동 측정을 켤 수 없어요.")
            PermissionCard(title = "위치", required = false, description = "걸은 거리를 계산할 때만 씁니다. 거부하면 시간·계단만 기록됩니다.")
            PermissionCard(title = "알림", required = false, description = "측정 중 상태와 하루 한 번 카드 알림을 보냅니다.")

            Text(
                "권한을 주지 않아도 앱은 그대로 씁니다. 자동 측정만 직접 기록으로 바뀝니다.",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )

            TmtnPrimaryButton(
                text = "권한 허용하기",
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
                        if (required) colors.secondary else colors.surface,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    if (required) "필수" else "선택",
                    style = TmtnType.caption,
                    color = if (required) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
        Text(description, style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}
