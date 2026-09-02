package com.tmtn.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.DayDetailResponse
import com.tmtn.app.ui.cardhome.MATERIAL_NAMES
import com.tmtn.app.ui.cardhome.MaterialIcon
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTextField
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma D03 · 하루 상세 시트 (바텀시트 - 화면 전체로 단순화) */
@Composable
fun DayDetailSheet(state: RecordState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val detail = state.dayDetail.value
    var memoText by remember(detail?.memo) { mutableStateOf(detail?.memo ?: "") }
    val material = detail?.element?.let { MATERIAL_NAMES[it] }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(colors.onSurface.copy(alpha = 0.32f)))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.width(36.dp).height(4.dp).align(Alignment.CenterHorizontally)
                    .background(colors.outline, RoundedCornerShape(2.dp)),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.selectedDate.value ?: "", style = TmtnType.title, color = colors.onSurface)
                if (detail != null) {
                    Box(
                        modifier = Modifier.background(colors.secondaryContainer, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(statusLabel(detail.status), style = TmtnType.caption, color = colors.onSurface)
                    }
                }
            }

            if (detail?.mission_title != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(detail.mission_title, style = TmtnType.bodyLarge, color = colors.onSurface)
                    Text(completionCaption(detail), style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }

            if (material != null && detail?.element != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaterialIcon(element = detail.element, size = 40.dp)
                    Text("이 날 받은 재료 · ${material.first} 1개", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }

            // 회고를 이미 남긴 날이면 입력란 대신 내용만 보여주고("완료 화면(B07)에서 이미
            // 넣었는데 여기서도 또 넣으라고 하면 헷갈림), 아직 없는 날만 추가할 수 있게 함.
            if (detail?.memo.isNullOrBlank()) {
                TmtnTextField(value = memoText, onValueChange = { if (it.length <= 100) memoText = it }, label = "메모")
                TmtnTextButton(
                    text = "메모 저장",
                    onClick = { scope.launch { state.saveMemo(memoText.ifBlank { null }) } },
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("남긴 회고", style = TmtnType.label, color = colors.onSurfaceVariant)
                    Text(detail?.memo ?: "", style = TmtnType.body, color = colors.onSurface)
                }
            }

            if (detail?.status != "REST") {
                TmtnOutlinedButton(
                    text = "쉼으로 표시",
                    onClick = { scope.launch { state.openRestSheetFor(state.selectedDate.value ?: return@launch) } },
                )
            }
            TmtnTextButton(text = "닫기", onClick = { state.closeDaySheet() })
        }
    }

    if (state.showRestSheet.value) {
        RestSheet(state, scope)
    }
}

@Composable
private fun RestSheet(state: RecordState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val streak = state.streak.value

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(colors.onSurface.copy(alpha = 0.32f)))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.width(36.dp).height(4.dp).align(Alignment.CenterHorizontally)
                    .background(colors.outline, RoundedCornerShape(2.dp)),
            )
            Text("이 날을 쉼으로 표시할까요?", style = TmtnType.title, color = colors.onSurface)
            Text(state.selectedDate.value ?: "", style = TmtnType.bodyLarge, color = colors.onSurface)

            Row(modifier = Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("이번 주 남은 쉼", style = TmtnType.body, color = colors.onSurface)
                Text("${streak?.rest_days_remaining_this_week ?: 2}회 / 2회", style = TmtnType.body, color = colors.onSurfaceVariant)
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                Text("쉼으로 표시한 날은 연속 기록을 끊지 않습니다.", style = TmtnType.body, color = colors.onSurface)
            }
            Text("한 주에 두 번까지 표시할 수 있습니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            TmtnPrimaryButton(
                text = "쉼으로 표시하기",
                onClick = { scope.launch { state.confirmRestDay() } },
                enabled = (streak?.rest_days_remaining_this_week ?: 2) > 0,
            )
            TmtnTextButton(text = "닫기", onClick = { state.closeRestSheet() })
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "COMPLETED" -> "실천"
    "REST" -> "쉼"
    else -> "미완료"
}

private fun completionCaption(detail: DayDetailResponse): String {
    val parts = mutableListOf<String>()
    detail.completed_at?.let { parts.add("$it 완료") }
    detail.duration_seconds?.let { parts.add("${it / 60}분 ${it % 60}초") }
    detail.count_achieved?.let { parts.add("${it}회") }
    return parts.joinToString(" · ")
}
