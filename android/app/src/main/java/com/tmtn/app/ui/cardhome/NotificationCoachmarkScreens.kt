package com.tmtn.app.ui.cardhome

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.notification.NotificationLog
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

private fun formatNotificationTime(timestampMillis: Long): String {
    val now = java.util.Calendar.getInstance()
    val when_ = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - when_.get(java.util.Calendar.DAY_OF_YEAR)
    val dayLabel = when {
        now.get(java.util.Calendar.YEAR) != when_.get(java.util.Calendar.YEAR) ->
            java.text.SimpleDateFormat("yyyy.M.d", java.util.Locale.KOREAN).format(when_.time)
        dayDiff == 0 -> "오늘"
        dayDiff == 1 -> "어제"
        else -> java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREAN).format(when_.time)
    }
    val clock = java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREAN).format(when_.time)
    return "$dayLabel $clock"
}

/**
 * Figma B13 · 알림함 / B15 · 알림함 비어있음.
 * ⚠️ 2026-09-18 교체 - 서버에 아직 "알림 이력" 조회 API가 없어서(fcm_device_tokens
 * 테이블만 있음) Figma 예시 3개를 고정으로 보여주던 것을, 이 기기에서 실제로 알림을
 * 띄운 기록(NotificationLog - MissionReminderReceiver/RestGiveUpReminderReceiver가
 * showNotification() 시점에 기록)으로 교체. 서버 이력 API가 나중에 생기면 그쪽으로
 * 교체 가능.
 */
@Composable
fun NotificationInboxScreen(onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val context = LocalContext.current
    val entries = remember { NotificationLog.readAll(context) }
    val hasNotifications = entries.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "알림", onBack = onBack)

        if (!hasNotifications) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("아직 알림이 없어요", style = TmtnType.title, color = colors.onSurface, textAlign = TextAlign.Center)
                    Text(
                        "오늘의 카드와 측정 결과를 알림으로 보내드릴게요.",
                        style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    entries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(entry.title, style = TmtnType.body, color = colors.onSurface)
                                Text(formatNotificationTime(entry.timestampMillis), style = TmtnType.caption, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text("읽은 알림은 30일 뒤 사라집니다.", style = TmtnType.body, color = colors.onSurfaceVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                ) {
                    Text(
                        "알림은 내 정보 · 생활시간 · 알림에서 켜고 끌 수 있습니다.",
                        style = TmtnType.body, color = colors.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Figma B14/B14b · 첫 진입 코치마크(튜토리얼 스포트라이트).
 * 처음 홈에 들어왔을 때만 2단계로 보여주는 오버레이. step 1: 비버 카드 안내, step 2: 하단 탭 안내.
 * onFinish 호출되면 다시는 안 보여줘야 함 - 호출부(MainActivity)에서 로컬 상태로 "한 번 봤음" 기억할 것.
 */
@Composable
fun CoachmarkOverlay(onSkip: () -> Unit, onFinish: () -> Unit) {
    val colors = LocalTmtnColors.current
    var coachStep by remember { mutableStateOf(1) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(colors.onSurface.copy(alpha = 0.72f)))

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .background(colors.background, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (coachStep == 1) "비버를 누르면 오늘의 카드가 열려." else "기록과 댐은 여기서 볼 수 있어.",
                style = TmtnType.bodyLarge, color = colors.onSurface,
            )
            Text("$coachStep / 2", style = TmtnType.caption, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (coachStep == 1) {
                    TmtnTextButton(text = "건너뛰기", onClick = onSkip)
                    TmtnPrimaryButton(text = "다음", onClick = { coachStep = 2 })
                } else {
                    TmtnPrimaryButton(text = "시작하기", onClick = onFinish)
                }
            }
        }
    }
}
