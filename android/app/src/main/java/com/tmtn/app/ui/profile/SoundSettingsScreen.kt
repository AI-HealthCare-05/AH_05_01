package com.tmtn.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tmtn.app.audio.TmtnAudio
import com.tmtn.app.audio.TmtnSound
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.*

@Composable
fun SoundSettingsScreen(onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("사운드", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("소리도 편안한 만큼", style = TmtnType.headline, color = colors.onSurface)
            Text("작은 나무 소리와 조용한 배경음을 준비했어요. 원하는 소리만 켜두세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            SoundToggle("효과음", "버튼을 누르거나 카드를 펼칠 때", TmtnAudio.effectsEnabled, TmtnAudio::setEffects)
            if (TmtnAudio.effectsEnabled) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val scale = LocalDensity.current.fontScale * LocalTmtnTextScale.current
                    val sounds = listOf("나무 톡" to TmtnSound.Tap, "종이 사각" to TmtnSound.Paper, "재료 획득" to TmtnSound.Reward)
                    if (maxWidth < (240 * scale).dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sounds.forEach { (label, sound) -> SoundPreviewButton(label, sound, Modifier.fillMaxWidth()) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sounds.forEach { (label, sound) -> SoundPreviewButton(label, sound, Modifier.weight(1f)) }
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            SoundToggle("홈 배경음", "홈에 머무는 동안 조용히 흘러요", TmtnAudio.ambienceEnabled, TmtnAudio::setAmbience)
            Text("홈으로 돌아가면 들을 수 있어요. 측정을 시작하거나 다른 화면으로 이동하면 멈춰요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            Text("휴대폰이 무음·진동이면 소리도 쉬어가요. 다른 앱의 음악을 듣고 있을 때는 재생하지 않아요. 소리 크기는 휴대폰 음량으로 조절해 주세요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SoundPreviewButton(label: String, sound: TmtnSound, modifier: Modifier) {
    val colors = LocalTmtnColors.current
    val minimum = if (AccessibilitySettingsHolder.largeControlsEnabled.value) 60.dp else 48.dp
    OutlinedButton(onClick = { TmtnAudio.play(sound) }, modifier = modifier.heightIn(min = minimum),
        shape = TmtnLayout.ControlShape, border = BorderStroke(1.dp, colors.outlineVariant),
        contentPadding = PaddingValues(8.dp)) {
        Text(label, style = TmtnType.label, color = colors.onSurface)
    }
}

@Composable
private fun SoundToggle(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalTmtnColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value, role = Role.Switch, onValueChange = onChange).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = TmtnType.bodyLarge, color = colors.onSurface)
            Text(detail, style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = colors.primary))
    }
}
