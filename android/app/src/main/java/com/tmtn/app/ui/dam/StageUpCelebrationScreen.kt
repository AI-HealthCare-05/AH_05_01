package com.tmtn.app.ui.dam

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.StageUpPendingResponse
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/**
 * Figma G07 · 댐 단계 상승 · 축하.
 * B07/C18(완료 화면) 뒤에 GET /companion/stage-up-pending으로 확인해서, 값이 있으면
 * 이 화면을 먼저 보여주고 POST /companion/stage-up-seen으로 봤다고 표시한 뒤 홈으로.
 */
@Composable
fun StageUpCelebrationScreen(pending: StageUpPendingResponse, onGoToDam: () -> Unit, onClose: () -> Unit) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "건너뛰기", onBack = onClose)

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(colors.secondary, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("${pending.new_stage}단계 달성", style = TmtnType.label, color = colors.onSurface)
            }

            Text("댐 몸통이\n이어졌어요", style = TmtnType.display, color = colors.onSurface)

            Box(
                modifier = Modifier.fillMaxWidth().height(132.dp).background(colors.surface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "이미지 미확정 · 댐 ${pending.new_stage}단계",
                    style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${pending.previous_stage}단계", style = TmtnType.bodyLarge, color = colors.onSurfaceVariant)
                Text("→", style = TmtnType.title, color = colors.secondary)
                Text(
                    "${pending.new_stage}단계 · ${pending.new_stage_label}",
                    style = TmtnType.bodyLarge, color = colors.onSurface,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${pending.previous_stage}단계에서 쌓은 것", style = TmtnType.label, color = colors.onSurface)
                Text(
                    "재료 ${pending.materials_gained_this_stage}개 · 실천 ${pending.days_practiced_this_stage}일 · " +
                        "쉼 ${pending.days_rested_this_stage}일",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
                if (pending.top_material_name != null) {
                    Text(
                        "가장 많이 모은 재료는 ${pending.top_material_name}였어요.",
                        style = TmtnType.caption, color = colors.onSurfaceVariant,
                    )
                }
            }

            TmtnPrimaryButton(text = "자란 댐 보러 가기", onClick = onGoToDam)
            TmtnTextButton(text = "닫기", onClick = onClose)
        }
    }
}
