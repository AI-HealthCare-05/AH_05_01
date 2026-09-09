package com.tmtn.app.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tmtn.app.R
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.TokenHolder

/**
 * ⚠️ 2026-09-08 추가 - 하루 1번, 오후 7시 고정(사용자가 못 바꾸는 시각). 오늘이 쉼(REST) 또는
 * 포기(GIVE_UP) 상태면 "자정 전이면 아직 다시 도전할 수 있다"는 걸 알려줌. MissionReminderWorker와
 * 반대 조건(완료/미완료가 아니라 "쉼·포기인지"만 봄) - 완료됐거나 아직 아무 선택도 안 한
 * 날에는 안 울림.
 */
class RestGiveUpReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private companion object {
        const val NOTIFICATION_ID = 2099
    }

    override suspend fun doWork(): Result {
        TokenHolder.init(applicationContext)

        runCatching {
            if (TokenHolder.accessToken == null) return@runCatching

            val consents = ApiClient.profileApi.listConsents()
            val notificationConsented = consents.body()
                ?.any { it.purpose == "NOTIFICATION" && it.status == "AGREED" } == true
            if (!notificationConsented) return@runCatching

            val today = ApiClient.cardHomeApi.getTodayCards()
            val card = today.body()
            val isRestingOrGivenUp = card != null &&
                (card.is_rest_day || card.is_given_up || card.challenge_state == "SKIPPED")
            if (isRestingOrGivenUp) {
                showNotification()
            }
        }

        NotificationScheduler.rescheduleTomorrow(
            applicationContext, NotificationScheduler.restGiveUpWorkName,
            NotificationScheduler.REST_GIVEUP_TIME, null,
        )
        return Result.success()
    }

    private fun showNotification() {
        val context = applicationContext
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("오늘, 아직 자정 전이에요")
            .setContentText("마음이 바뀌면 지금이라도 다시 도전할 수 있어요.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
