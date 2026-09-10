package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tmtn.app.notification.NotificationScheduler
import com.tmtn.app.ui.onboarding.TimeWheelPicker
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ⚠️ 2026-09-08 원복: "정해진 3개 옵션 중 선택"으로 바꿨었는데, 실제로 필요했던 건
// "자고 일어나는 시각 기반 자동 계산" 쪽이라 원래의 자유 시간 선택(TimeWheelPicker)으로
// 되돌림. 슬롯 자체는 WakeSleepEditScreen(기상+2시간/점심 직접입력/취침-2시간)에서 계산됨 -
// 이 화면은 그렇게 계산된 값을 개별로 미세 조정하고 싶을 때만 씀.
private val SLOT_LABELS = listOf("아침 준비", "점심", "자기 전")

/** Figma F02 · 생활시간 · 알림 설정 */
@Composable
fun NotificationSettingScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val setting = state.notificationSetting.value

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "생활시간 · 알림", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
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

            // ⚠️ 2026-09-08 QA 반영: 여기가 MainActivity에서 빈 람다({})로 넘어와서 눌러도
            // 아무 일도 안 일어났음. 내 정보 탭 안의 화면으로 직접 이동하게 바꿈.
            ProfileListItem("자고 일어나는 시각", "이 시각에 맞춰 알림 시간을 잡아요") {
                state.screen.value = ProfileScreenKey.WAKE_SLEEP
            }

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
                // ⚠️ 2026-09-08 QA 반영: 앱 전체가 존댓말인데 이 문구만 반말이었음.
                Text("알림이 부담되면 모두 꺼도 됩니다. 그래도 카드는 그 자리에 있어요.", style = TmtnType.body, color = colors.onSurface)
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
    val context = LocalContext.current
    val index = state.editingSlotIndex.value
    val currentTime = state.notificationSetting.value?.slots?.getOrNull(index) ?: "07:00"
    var time by remember(currentTime) { mutableStateOf(currentTime) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = SLOT_LABELS.getOrElse(index) { "알림 시각" }, onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("이 알림을 보낼 시각을 골라 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TimeWheelPicker(timeString = time, onTimeChange = { time = it })

            TmtnPrimaryButton(
                text = "이 시간으로 저장",
                onClick = {
                    scope.launch {
                        state.updateSlotTime(index, time)
                        // ⚠️ 2026-09-08 반영: 서버 저장만으로는 실제 알림이 안 바뀜(로컬
                        // 스케줄링 방식이라) - 저장 직후 기기에 예약된 알림도 새 시각으로
                        // 다시 등록함. 여기서는 "분"까지 정확히 반영(방금 고른 값을 그대로).
                        val slots = state.notificationSetting.value?.slots
                        if (slots != null) {
                            NotificationScheduler.scheduleAllExact(context.applicationContext, slots)
                        }
                    }
                },
            )
        }
    }
}

/** "HH:MM"을 시간 단위로 밀어줌(음수면 앞으로). 자정을 넘어가면 24로 감싸서 계산. */
private fun shiftTime(time: String, hours: Int): String {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val shifted = ((hour + hours) % 24 + 24) % 24
    return "%02d:%02d".format(shifted, minute)
}

/**
 * ⚠️ 2026-09-08 QA 반영(신규): 내 정보 · 생활시간 알림 · "자고 일어나는 시각".
 *
 * 예전엔 이 행을 눌러도 아무 반응이 없었음 - MainActivity에서 빈 람다({})로 넘어오고 있었고,
 * "A10(생활패턴 시간 선택)은 온보딩 흐름 안에서만 동작해서 재사용하려면 별도 작업이 필요함"
 * 이라는 TODO가 그대로 남아 있었음. 온보딩 A10과 같은 TimeWheelPicker·같은 엔드포인트를 쓰되
 * 내 정보 탭 안에서 완결되는 화면으로 새로 만듦.
 *
 * 서버는 기상/취침 시각 자체를 저장하지 않고 알림 슬롯 3개(기상 / 기상+6시간 / 취침-1시간)로
 * 환산해서만 보관하므로(notification_setting_service.py), 지금 값은 그 슬롯에서 거꾸로
 * 되돌려서 채움 - 슬롯을 개별로 직접 고친 사용자는 원래 기상/취침과 다를 수 있어서 화면에
 * 그 점을 같이 안내함.
 */
@Composable
fun WakeSleepEditScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val context = LocalContext.current
    val slots = state.notificationSetting.value?.slots
    // ⚠️ 2026-09-08 개정: 오전 슬롯 = 기상+2시간(예전엔 기상 그대로)으로, 저녁 슬롯 =
    // 취침-2시간(예전엔 -1시간)으로 계산식이 바뀌어서, 저장된 값에서 원래 기상/취침
    // 시각을 되짚어 보여줄 때도 그만큼 반대로 밀어야 함. 점심은 더 이상 자동 계산이
    // 아니라 사용자가 직접 입력한 값이라 그대로 씀.
    // ⚠️ 2026-09-08 재개정: 아침 슬롯 = 기상 그대로(더 이상 +2시간 아님), 점심 슬롯 =
    // 점심+1시간, 저녁 슬롯 = 취침-2시간. 저장된 값에서 원래 입력값을 되짚어 보여줄 때도
    // 그에 맞게 반대로 계산해야 함.
    val initialWake = slots?.getOrNull(0) ?: "07:00"
    val initialLunch = slots?.getOrNull(1)?.let { shiftTime(it, -1) } ?: "12:00"
    val initialSleep = slots?.getOrNull(2)?.let { shiftTime(it, 2) } ?: "23:00"

    var wakeTime by remember(initialWake) { mutableStateOf(initialWake) }
    var lunchTime by remember(initialLunch) { mutableStateOf(initialLunch) }
    var sleepTime by remember(initialSleep) { mutableStateOf(initialSleep) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "자고 일어나는 시각", onBack = onBack)
        Column(
            // ⚠️ 2026-09-08 QA 반영: 스크롤이 없어서 TimeWheelPicker 3개 + 미리보기 박스 +
            // 안내문구를 다 쌓으면 화면 높이를 넘어 "이 시간으로 맞추기" 버튼이 화면 밖으로
            // 밀려났음(버튼이 안 보임). 다른 온보딩 화면(A10ScheduleScreen)엔 이미 있던
            // verticalScroll이 여기만 빠져 있었음.
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "보통 일어나는 시각·점심시간·잠드는 시각을 알려 주시면 그에 맞춰 알림 시간을 다시 잡습니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            Text("일어나는 시각", style = TmtnType.label, color = colors.onSurface)
            TimeWheelPicker(timeString = wakeTime, onTimeChange = { wakeTime = it })

            Text("점심 시각", style = TmtnType.label, color = colors.onSurface)
            TimeWheelPicker(timeString = lunchTime, onTimeChange = { lunchTime = it })

            Text("잠드는 시각", style = TmtnType.label, color = colors.onSurface)
            TimeWheelPicker(timeString = sleepTime, onTimeChange = { sleepTime = it })

            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("알림은 이렇게 맞춰집니다", style = TmtnType.label, color = colors.onSurface)
                Text(
                    "아침 준비 · ${formatTimeKorean(wakeTime)} · 일어난 직후\n" +
                        "점심 뒤 · ${formatTimeKorean(shiftTime(lunchTime, 1))} · 점심 1시간 후\n" +
                        "자기 전 · ${formatTimeKorean(shiftTime(sleepTime, -2))} · 잠들기 2시간 전",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            Text(
                "저장하면 알림 시각 3개가 위 기준으로 다시 계산됩니다. 개별 시각을 직접 고쳐 두셨다면 그 값은 덮어써집니다.",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )

            TmtnPrimaryButton(
                text = "이 시간으로 맞추기",
                onClick = {
                    scope.launch {
                        state.saveWakeSleep(wakeTime, lunchTime, sleepTime)
                        // ⚠️ 2026-09-08 반영: 여기가 바로 "알람이 안 온다"는 QA의 원인이었음
                        // - 서버 저장(saveWakeSleep)만 하고 기기의 실제 로컬 알림 예약은
                        // 전혀 안 하고 있었음. 저장 성공 후 최신 slots(서버가 계산해서
                        // 돌려준 값 - 입력한 시각을 그대로 반영한 것)로 다시 예약함.
                        state.notificationSetting.value?.slots?.let { newSlots ->
                            NotificationScheduler.scheduleAllExact(context.applicationContext, newSlots)
                        }
                    }
                },
            )
        }
    }
}
