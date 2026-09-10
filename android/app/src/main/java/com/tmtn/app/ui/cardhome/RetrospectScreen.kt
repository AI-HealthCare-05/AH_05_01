package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/** Figma C20 · 완료 후 한 줄 회고. 실제 저장은 기록 캘린더의 메모 API를 재사용함.
 *
 * ⚠️ 2026-09-04 QA(P0-5) 반영: 키보드가 열리면 "저장하기" 버튼을 그대로 가리고, 스크롤도
 * 안 돼서 키보드를 내려야만 저장할 수 있었음. windowSoftInputMode="adjustResize"는 이미
 * 설정돼 있어서(AndroidManifest.xml), Compose 쪽에 imePadding() + 스크롤만 추가하면 됨.
 */
@Composable
fun RetrospectScreen(state: CardHomeState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    var memo by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf<String?>(null) }
    val moods = listOf("좋았어", "그저 그랬어", "힘들었어")

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TmtnTopBar(title = "오늘 완료", onBack = { scope.launch { state.submitRetrospect(null) } })

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ⚠️ 2026-09-08 QA(N10) 반영: "완료하기 → 확인 → 회고 → 홈" 어디에도 보상이
            // 안 보이고 홈에 가서야 재료가 늘어난 걸 볼 수 있었음 - 여기서 바로 보여줌.
            val awardedElement = state.awardedFiveElement.value
            val material = MATERIAL_NAMES[awardedElement]
            if (material != null) {
                Row(
                    modifier = Modifier
                        .background(colors.woodContainer, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MaterialIcon(element = awardedElement!!, size = 20.dp)
                    Text("${material.first} 1개를 얻었어요", style = TmtnType.label, color = colors.onSurface)
                }
            }

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
