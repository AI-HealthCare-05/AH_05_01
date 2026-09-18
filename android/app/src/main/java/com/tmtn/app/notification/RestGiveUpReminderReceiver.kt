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

/** ⚠️ 2026-09-18 교체 - RestGiveUpReminderWorker(WorkManager)를 대체.
 * MissionReminderReceiver와 같은 이유(WorkManager 지연·재예약 체인 취약성). */
class RestGiveUpReminderReceiver : BroadcastReceiver() {

    private companion object {
        const val NOTIFICATION_ID = 2099
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runWork(appContext)
            } finally {
                NotificationScheduler.rescheduleTomorrowExact(
                    appContext, NotificationScheduler.restGiveUpWorkName,
                    NotificationScheduler.REST_GIVEUP_TIME, null,
                )
                pending.finish()
            }
        }
    }

    private suspend fun runWork(context: Context) {
        TokenHolder.init(context)
        runCatching {
            if (TokenHolder.accessToken == null) return@runCatching

            val consents = ApiClient.profileApi.listConsents()
            val notificationConsented = consents.body()
                ?.any { it.purpose == "NOTIFICATION" && it.status == "AGREED" } == true
            if (!notificationConsented) return@runCatching
            val setting = ApiClient.profileApi.getNotificationSettings()
            if (!setting.isSuccessful || !reminderAllowed(setting.body())) return@runCatching

            val today = ApiClient.cardHomeApi.getTodayCards()
            val card = today.body()
            val isRestingOrGivenUp = card != null &&
                (card.is_rest_day || card.is_given_up || card.challenge_state == "SKIPPED")
            if (isRestingOrGivenUp) {
                showNotification(context)
            }
        }.onFailure { e -> android.util.Log.e("RestGiveUpReminderReceiver", "예외 발생: ${e.message}", e) }
    }

    private fun showNotification(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val title = "틈튼이는 여기서 기다릴게요"
        val body = "쉬어 가도 좋아요. 오늘 다시 해보고 싶다면 카드를 펼쳐 주세요."
        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        NotificationLog.record(context, title, body)
    }
}
