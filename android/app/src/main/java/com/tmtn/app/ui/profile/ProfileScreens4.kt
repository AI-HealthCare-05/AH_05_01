package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TimeWheelPicker
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val SLOT_LABELS = listOf("아침 준비", "점심 뒤", "자기 전")

/** Figma F02 · 생활시간 · 알림 설정 */
@Composable
fun NotificationSettingScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit, onEditWakeSleep: () -> Unit) {
    val colors = LocalTmtnColors.current
    val setting = state.notificationSetting.value

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "생활시간 · 알림", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("하루 중 언제가 편한지 알려 주시면 그 시간에만 알립니다.", style = TmtnType.body, color = colors.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("알림 전체", style = TmtnType.label, color = colors.onSurface)
                Switch(
                    checked = setting?.enabled ?: true,
                    onCheckedChange = { scope.launch { state.toggleNotificationsEnabled(it) } },
                )
            }

            ProfileListItem("자고 일어나는 시각", "이 시각에 맞춰 알림 시간을 잡아요") { onEditWakeSleep() }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                setting?.slots?.forEachIndexed { index, time ->
                    ProfileListItem(SLOT_LABELS.getOrElse(index) { "알림 ${index + 1}" }, formatTimeKorean(time)) {
                        state.editingSlotIndex.value = index
                        state.screen.value = ProfileScreenKey.NOTIFICATION_TIME
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("알림은 이렇게 옵니다", style = TmtnType.label, color = colors.onSurface)
                Text(
                    "하루 최대 3번, 켜 둔 시간에만 옵니다.\n시각을 직접 고치면 생활 패턴이 바뀌어도 그대로 둡니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                Text("알림이 부담되면 다 꺼도 돼. 그래도 카드는 그 자리에 있어.", style = TmtnType.body, color = colors.onSurface)
            }
        }
    }
}

private fun formatTimeKorean(time: String): String {
    val parts = time.split(":")
    val hour24 = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val amPm = if (hour24 < 12) "오전" else "오후"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "$amPm $hour12:${"%02d".format(minute)}"
}

/** Figma F23 · 알림 시각 선택 */
@Composable
fun NotificationTimeEditScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val index = state.editingSlotIndex.value
    val currentTime = state.notificationSetting.value?.slots?.getOrNull(index) ?: "07:00"
    var time by remember(currentTime) { mutableStateOf(currentTime) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = SLOT_LABELS.getOrElse(index) { "알림 시각" }, onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("이 알림을 보낼 시각을 골라 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TimeWheelPicker(timeString = time, onTimeChange = { time = it })

            TmtnPrimaryButton(
                text = "이 시간으로 저장",
                onClick = { scope.launch { state.updateSlotTime(index, time) } },
            )
        }
    }
}
