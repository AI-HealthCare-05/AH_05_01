package com.tmtn.app.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tmtn.app.R
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.TokenHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ⚠️ 2026-09-18 교체(홈 알림함이 실제로는 고정 예시였고, 실제 알림도 조용히 끊길 수 있던
 * 문제 대응) - MissionReminderWorker(WorkManager)를 대체. WorkManager는 "정확한 시각"을
 * 보장하지 않고(배터리 최적화·Doze로 지연/스킵될 수 있음), 이 기능은 실행이 끝날 때마다
 * "내일 같은 시각"으로 스스로 재예약하는 체인이라 - 단 한 번이라도 스킵되면 그 뒤로 영구히
 * 알림이 멈춤. AlarmManager.setExactAndAllowWhileIdle()로 예약하는 BroadcastReceiver로
 * 바꿔서 이 두 문제를 없앰. BroadcastReceiver.onReceive()는 오래 걸리는 작업(네트워크
 * 호출)을 직접 하면 안 되므로 goAsync()로 완료를 미뤄두고 코루틴에서 처리.
 */
class MissionReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_SLOT = "slot"
        const val EXTRA_TIME = "time"
        const val SLOT_MORNING = "MORNING"
        const val SLOT_LUNCH = "LUNCH"
        const val SLOT_EVENING = "EVENING"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(EXTRA_SLOT) ?: SLOT_MORNING
        val time = intent.getStringExtra(EXTRA_TIME) ?: "08:00"
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                runWork(appContext, slot)
            } finally {
                // ⚠️ 성공/실패 어떤 경우든 "내일 같은 시각"으로 재예약 - 여기서 재예약을
                // 안 하면 딱 하루만 울리고 끝나버림. AlarmManager는 WorkManager처럼
                // "값이 안 바뀌었으면 스킵" 최적화가 필요 없음(정확한 시각으로 매번
                // 새로 걸어도 안전 - setExactAndAllowWhileIdle이 같은 requestCode면
                // 자동으로 이전 알람을 교체함).
                NotificationScheduler.rescheduleTomorrowExact(
                    appContext, NotificationScheduler.uniqueWorkNameFor(slot), time, slot,
                )
                pending.finish()
            }
        }
    }

    private suspend fun runWork(context: Context, slot: String) {
        TokenHolder.init(context)
        runCatching {
            if (TokenHolder.accessToken == null) {
                android.util.Log.w("MissionReminderReceiver", "[$slot] 스킵: 로그인 토큰 없음")
                return@runCatching
            }

            val consents = ApiClient.profileApi.listConsents()
            if (!consents.isSuccessful) {
                android.util.Log.w(
                    "MissionReminderReceiver",
                    "[$slot] 동의 조회 실패(HTTP ${consents.code()}) - 서버에 못 닿았을 수 있음",
                )
                return@runCatching
            }
            val notificationConsented = consents.body()
                ?.any { it.purpose == "NOTIFICATION" && it.status == "AGREED" } == true
            if (!notificationConsented) {
                android.util.Log.i("MissionReminderReceiver", "[$slot] 스킵: 알림 동의 없음")
                return@runCatching
            }
            val setting = ApiClient.profileApi.getNotificationSettings()
            if (!setting.isSuccessful || !reminderAllowed(setting.body())) return@runCatching

            val today = ApiClient.cardHomeApi.getTodayCards()
            if (!today.isSuccessful) {
                android.util.Log.w(
                    "MissionReminderReceiver",
                    "[$slot] 오늘 카드 조회 실패(HTTP ${today.code()}) - 서버에 못 닿았을 수 있음",
                )
                return@runCatching
            }
            val card = today.body()
            if (card != null) {
                val isFinishedOrOff = card.challenge_state == "COMPLETED" ||
                    card.challenge_state == "SKIPPED" ||
                    card.is_rest_day ||
                    card.is_given_up
                if (!isFinishedOrOff) {
                    showNotification(context, slot)
                } else {
                    android.util.Log.i("MissionReminderReceiver", "[$slot] 스킵: 오늘 미션 이미 완료/쉼/포기 상태")
                }
            } else {
                android.util.Log.w("MissionReminderReceiver", "[$slot] 스킵: 오늘 카드 응답이 비어 있음")
            }
        }.onFailure { e ->
            android.util.Log.e("MissionReminderReceiver", "[$slot] 예외 발생: ${e.message}", e)
        }
    }

    private fun showNotification(context: Context, slot: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val (title, body) = when (slot) {
            SLOT_MORNING -> "틈튼이가 카드를 준비했어요" to "오늘 하루에 어울리는 실천을 골라 봐요."
            SLOT_LUNCH -> "점심 뒤, 작은 실천 하나" to "잠깐 여유가 생겼다면 오늘의 카드를 펼쳐 봐요."
            else -> "하루를 마무리하며" to "오늘의 카드가 기다려요. 편한 만큼 함께해요."
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_BASE + slot.hashCode() % 100, notification)
        // ⚠️ 실제로 알림을 띄운 순간을 로컬에 기록 - NotificationInboxScreen이 더 이상
        // Figma 고정 예시가 아니라 이 기록을 읽어서 보여줄 수 있게 함(NotificationLog 참고).
        NotificationLog.record(context, title, body)
    }
}
