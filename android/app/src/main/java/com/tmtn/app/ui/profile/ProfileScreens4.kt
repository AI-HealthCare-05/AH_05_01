package com.tmtn.app.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tmtn.app.notification.NotificationScheduler
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime

private val SLOT_LABELS = listOf("아침 준비", "점심 뒤", "자기 전")

/** Uses the existing enabled flag, consent, and three slots. No new notification endpoint. */
internal fun syncDeviceNotifications(context: Context, state: ProfileState) {
    if (state.errorMessage.value != null) return
    val setting = state.notificationSetting.value ?: return
    val consented = state.consents.value.any { it.purpose == "NOTIFICATION" && it.status == "AGREED" }
    if (setting.enabled && consented) NotificationScheduler.scheduleAllExact(context.applicationContext, setting.slots)
    else NotificationScheduler.cancelAll(context.applicationContext)
}

/** Figma F05 1314:7160. Permission state comes from the device, never a sample. */
@Composable
fun NotificationSettingScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val setting = state.notificationSetting.value
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permitted by remember { mutableStateOf(NotificationScheduler.areNotificationsPermitted(context)) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = NotificationScheduler.areNotificationsPermitted(context)
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = NotificationScheduler.areNotificationsPermitted(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun openSettings() { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("생활시간 · 알림", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("네 하루에 맞춰\n소식을 전할게.", style = TmtnType.headline, color = colors.onSurface)
            Text("바쁜 시간은 피하고, 실천하기 편할 때 만나요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            NotificationPanel {
                Text("오늘의 카드 알림", style = TmtnType.label, color = colors.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (setting == null) "설정을 불러오고 있어요" else if (setting.enabled) "켜짐" else "꺼짐", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = setting?.enabled == true, enabled = setting != null && !state.isLoading.value,
                        onCheckedChange = { enabled -> scope.launch { state.toggleNotificationsEnabled(enabled); syncDeviceNotifications(context, state) } })
                }
                if (setting?.enabled == true && state.consents.value.none { it.purpose == "NOTIFICATION" && it.status == "AGREED" }) {
                    Text("알림을 받으려면 동의 관리에서 알림 동의도 켜 주세요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                    TmtnTextButton("동의 관리 열기", { state.screen.value = ProfileScreenKey.CONSENT })
                }
            }
            if (!setting?.slots.isNullOrEmpty()) NotificationPanel {
                Text("카드가 찾아올 시간", style = TmtnType.label, color = colors.onSurface)
                setting!!.slots.forEachIndexed { index, time ->
                    ProfileListItem(SLOT_LABELS.getOrElse(index) { "알림 ${index + 1}" }, formatTimeKorean(time)) {
                        state.editingSlotIndex.value = index; state.screen.value = ProfileScreenKey.NOTIFICATION_TIME
                    }
                }
            }
            NotificationPanel {
                ProfileListItem("휴대전화 알림 권한", if (permitted) "허용됨" else "꺼져 있어요") {
                    if (!permitted && Build.VERSION.SDK_INT >= 33) permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS) else openSettings()
                }
                TmtnTextButton("휴대전화 알림 설정 열기", { openSettings() })
                Text("움직임을 측정하는 동안의 알림도 휴대전화 설정에서 조절할 수 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }
            Text("알림을 꺼도 카드는 그 자리에 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            if (setting?.enabled == true) Text("쉬어가는 날에는 저녁 7시에 짧은 소식이 올 수 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            TmtnTonalButton("내 정보로", onBack)
        }
        Box(Modifier.fillMaxWidth().padding(20.dp)) {
            TmtnPrimaryButton("생활시간 바꾸기", { state.screen.value = ProfileScreenKey.WAKE_SLEEP })
        }
    }
}

@Composable
private fun NotificationPanel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

internal fun formatTimeKorean(time: String): String {
    val parsed = runCatching { LocalTime.parse(time) }.getOrNull() ?: return time
    val hour = parsed.hour % 12
    return "${if (parsed.hour < 12) "오전" else "오후"} ${if (hour == 0) 12 else hour}:${"%02d".format(parsed.minute)}"
}

@Composable
fun NotificationTimeEditScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val context = LocalContext.current
    val index = state.editingSlotIndex.value
    val currentTime = state.notificationSetting.value?.slots?.getOrNull(index)
    var time by rememberSaveable(index, currentTime) { mutableStateOf(currentTime ?: "07:00") }
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar(SLOT_LABELS.getOrElse(index) { "알림 시간" }, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("카드가 오기 좋은\n시간을 알려주세요.", style = TmtnType.headline, color = colors.onSurface)
            Text("이 알림의 시간만 바꿔요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TimeSettingField("알림 시간", time, { time = it })
            TmtnTonalButton("취소", onBack)
        }
        Box(Modifier.fillMaxWidth().padding(20.dp)) {
            TmtnPrimaryButton("이 시간으로 저장", { scope.launch { state.updateSlotTime(index, time); syncDeviceNotifications(context, state) } },
                enabled = currentTime != null && !state.isLoading.value)
        }
    }
}

private fun shiftTime(time: String, hours: Int): String = runCatching { LocalTime.parse(time).plusHours(hours.toLong()).toString() }.getOrDefault(time)

/** Figma F06 1314:7202, preserving the existing server's lunch field. */
@Composable
fun WakeSleepEditScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val context = LocalContext.current
    val slots = state.notificationSetting.value?.slots
    var wake by rememberSaveable(slots) { mutableStateOf(slots?.getOrNull(0) ?: "07:00") }
    var lunch by rememberSaveable(slots) { mutableStateOf(slots?.getOrNull(1)?.let { shiftTime(it, -1) } ?: "12:00") }
    var sleep by rememberSaveable(slots) { mutableStateOf(slots?.getOrNull(2)?.let { shiftTime(it, 2) } ?: "23:00") }
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("생활시간", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("카드가 오기 좋은\n시간을 알려주세요.", style = TmtnType.headline, color = colors.onSurface)
            Text("평소 하루에 맞춰 알림 시간을 조정해요. 지금 알림 시간을 바탕으로 채워 뒀어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TimeSettingField("기상 시간", wake, { wake = it })
            TimeSettingField("점심 시간", lunch, { lunch = it })
            TimeSettingField("취침 시간", sleep, { sleep = it })
            NotificationPanel {
                Text("이 시간에 찾아갈게요", style = TmtnType.label, color = colors.onSurface)
                Text("아침 준비 · ${formatTimeKorean(wake)}\n점심 뒤 · ${formatTimeKorean(shiftTime(lunch, 1))}\n자기 전 · ${formatTimeKorean(shiftTime(sleep, -2))}", style = TmtnType.body, color = colors.onSurface)
            }
            Text("저장하면 카드 알림 세 개가 위 시간으로 바뀌어요. 알림 켜짐·꺼짐 설정은 유지해요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            TmtnTonalButton("취소", onBack)
        }
        Box(Modifier.fillMaxWidth().padding(20.dp)) {
            TmtnPrimaryButton("이 시간으로 맞추기", { scope.launch { state.saveWakeSleep(wake, lunch, sleep); syncDeviceNotifications(context, state) } }, enabled = !state.isLoading.value)
        }
    }
}

@Composable
private fun TimeSettingField(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalTmtnColors.current
    var choosing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.3f
    Row(Modifier.fillMaxWidth().border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
        .tmtnClickable { draft = value; choosing = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text(formatTimeKorean(value), style = TmtnType.bodyLarge, color = colors.onSurface)
        }
        Text("변경", style = TmtnType.label, color = colors.onSurface)
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, containerColor = colors.background,
        title = { Text(label, style = TmtnType.title, color = colors.onSurface) },
        text = { if (largeText) AccessibleTimeInput(draft, { draft = it }) else TimeWheelPicker(draft, { draft = it }) },
        confirmButton = { TextButton({ onChange(draft); choosing = false }, enabled = runCatching { LocalTime.parse(draft) }.isSuccess) { Text("이 시간으로", style = TmtnType.label, color = colors.onSurface) } },
        dismissButton = { TextButton({ choosing = false }) { Text("취소", style = TmtnType.label, color = colors.onSurfaceVariant) } })
}

@Composable
private fun AccessibleTimeInput(value: String, onChange: (String) -> Unit) {
    var hour by remember { mutableStateOf(value.substringBefore(":")) }
    var minute by remember { mutableStateOf(value.substringAfter(":")) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TmtnTextField(hour, { hour = it.filter(Char::isDigit).take(2); onChange("${hour.padStart(2, '0')}:${minute.padStart(2, '0')}") },
            "시 · 0–23", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        TmtnTextField(minute, { minute = it.filter(Char::isDigit).take(2); onChange("${hour.padStart(2, '0')}:${minute.padStart(2, '0')}") },
            "분 · 0–59", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
    }
}
