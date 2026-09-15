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
 * ⚠️ 2026-09-08 추가 - 오전/점심/저녁 중 한 슬롯을 맡아, "오늘 미션이 아직 완료 안 됐고
 * 쉼·포기로도 표시 안 됐으면" 알림을 띄움. 하나의 Worker 클래스를 슬롯 3개가 공유하고
 * (KEY_SLOT/KEY_HOUR로 자기가 어느 슬롯인지 앎), 실행이 끝나면 항상 "내일 같은 시각"으로
 * 스스로 재예약함(self-rescheduling) - 그래야 한 번 설치해두면 계속 반복됨.
 */
class MissionReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SLOT = "slot"
        const val KEY_TIME = "time"
        const val SLOT_MORNING = "MORNING"
        const val SLOT_LUNCH = "LUNCH"
        const val SLOT_EVENING = "EVENING"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    override suspend fun doWork(): Result {
        val slot = inputData.getString(KEY_SLOT) ?: SLOT_MORNING
        val time = inputData.getString(KEY_TIME) ?: "08:00"

        // ⚠️ 앱을 아예 안 연 상태(프로세스 콜드 스타트)에서도 Worker는 실행될 수 있어서,
        // 저장된 토큰을 직접 다시 불러와야 함(Activity의 onCreate를 안 거치므로).
        TokenHolder.init(applicationContext)

        runCatching {
            if (TokenHolder.accessToken == null) {
                android.util.Log.w("MissionReminderWorker", "[$slot] 스킵: 로그인 토큰 없음")
                return@runCatching
            }

            val consents = ApiClient.profileApi.listConsents()
            if (!consents.isSuccessful) {
                // ⚠️ 2026-09-12 추가 - 예전엔 이 실패가 조용히 "동의 없음"과 똑같이
                // 취급돼서 알림이 안 오는데 원인을 전혀 알 수 없었음(서버 통신 실패인지,
                // 정말 동의를 안 한 건지 구분 불가). 이제 로그로 구분해서 남김.
                android.util.Log.w(
                    "MissionReminderWorker",
                    "[$slot] 동의 조회 실패(HTTP ${consents.code()}) - 서버에 못 닿았을 수 있음",
                )
                return@runCatching
            }
            val notificationConsented = consents.body()
                ?.any { it.purpose == "NOTIFICATION" && it.status == "AGREED" } == true
            if (!notificationConsented) {
                android.util.Log.i("MissionReminderWorker", "[$slot] 스킵: 알림 동의 없음")
                return@runCatching
            }
            val setting = ApiClient.profileApi.getNotificationSettings()
            if (!setting.isSuccessful || !reminderAllowed(setting.body())) return@runCatching

            val today = ApiClient.cardHomeApi.getTodayCards()
            if (!today.isSuccessful) {
                android.util.Log.w(
                    "MissionReminderWorker",
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
                    showNotification(slot)
                } else {
                    android.util.Log.i("MissionReminderWorker", "[$slot] 스킵: 오늘 미션 이미 완료/쉼/포기 상태")
                }
            } else {
                android.util.Log.w("MissionReminderWorker", "[$slot] 스킵: 오늘 카드 응답이 비어 있음")
            }
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("MissionReminderWorker", "[$slot] 예외 발생: ${e.message}", e)
        }

        // ⚠️ 성공/실패/동의없음 어떤 경우든 "내일 같은 시각"으로 재예약 - 여기서 재예약을
        // 안 하면 딱 하루만 울리고 끝나버림.
        NotificationScheduler.rescheduleTomorrow(
            applicationContext, NotificationScheduler.uniqueWorkNameFor(slot), time, slot,
        )
        return Result.success()
    }

    private fun showNotification(slot: String) {
        val context = applicationContext
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
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
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
    }
}
