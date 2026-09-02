package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnChip
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTextField
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma C20 · 완료 후 한 줄 회고. 실제 저장은 기록 캘린더의 메모 API를 재사용함. */
@Composable
fun RetrospectScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    var memo by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf<String?>(null) }
    val moods = listOf("좋았어", "그저 그랬어", "힘들었어")

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "오늘 완료", onBack = { scope.launch { state.submitRetrospect(null) } })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("오늘 어땠어?", style = TmtnType.headline, color = colors.onSurface)
            Text(
                "한 줄만 남겨도 좋아요. 나중에 기록에서 다시 볼 수 있습니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            TmtnTextField(
                value = memo, onValueChange = { if (it.length <= 100) memo = it },
                label = "한 줄 회고",
            )
            Text(
                "${memo.length} / 100", style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
            )

            Text("오늘 기분", style = TmtnType.label, color = colors.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moods.forEach { m ->
                    TmtnChip(text = m, selected = mood == m, onClick = { mood = m })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "건너뛰어도 완료 기록은 그대로 저장됩니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            TmtnPrimaryButton(
                text = "저장하기",
                onClick = {
                    scope.launch {
                        val fullMemo = if (mood != null) "[$mood] $memo" else memo
                        state.submitRetrospect(fullMemo)
                    }
                },
            )
            TmtnTextButton(text = "건너뛰기", onClick = { scope.launch { state.submitRetrospect(null) } })
        }
    }
}
