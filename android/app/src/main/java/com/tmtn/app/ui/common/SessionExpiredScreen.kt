package com.tmtn.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** Figma H07 · 세션 만료 · 재로그인. 401 감지되면(SessionManager) MainActivity가 이 화면을 띄움. */
@Composable
fun SessionExpiredScreen(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp).background(colors.surface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // ⚠️ 2026-09-06 반영: H07(세션 만료) 배치표 그대로 - "문 앞에서 기다리는" beaver_waiting.
                Image(
                    painter = painterResource(com.tmtn.app.R.drawable.beaver_waiting),
                    contentDescription = "문 앞에서 기다리는 비버",
                    modifier = Modifier.height(200.dp),
                )
            }
            Text("다시 로그인해 주세요", style = TmtnType.title, color = colors.onSurface)
            Text(
                // ⚠️ 2026-09-06 QA(P2) 반영: "기록은 그대로 남아있다"를 여기서 한 번,
                // 아래 강조 배지에서 또 한 번("기록은 모두 저장돼 있습니다") 말하고 있었음 -
                // 여기서는 왜 로그인이 풀렸는지만 설명하고, 기록 보존 안내는 배지 하나로 통일.
                "보안을 위해 일정 기간이 지나면 로그인이 풀립니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.onSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("기록은 모두 저장돼 있습니다", style = TmtnType.body, color = colors.background)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TmtnPrimaryButton(text = "로그인하기", onClick = onLogin)
            TmtnTextButton(text = "다른 계정으로 로그인", onClick = onLogin)
        }
    }
}
