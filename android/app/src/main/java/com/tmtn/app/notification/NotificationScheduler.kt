package com.tmtn.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * ⚠️ 2026-09-08 추가, 2026-09-18 재작성 - 미션 미완료(오전/점심/저녁 3회) · 쉼·포기(오후
 * 7시 고정) 리마인더 로컬 알림. 서버가 실제 FCM 발송 인프라를 아직 안 갖고 있어서(토큰
 * 저장 모델만 있고 실제 전송 로직·스케줄러는 없음), 기기에서 직접 스케줄링하는 로컬
 * 알림으로 구현함 - 서버 인프라가 나중에 생기면 이 부분만 FCM 수신으로 바꾸면 됨.
 *
 * ⚠️ 2026-09-18 재작성 이유 - WorkManager(OneTimeWorkRequest + self-rescheduling) 방식은
 * 두 가지 문제가 있었음: (1) WorkManager의 지연 실행은 "정확한 시각"을 보장하지 않아서
 * 배터리 최적화·Doze 모드에 걸리면 실제 알림이 늦게 오거나 아예 스킵될 수 있었음.
 * (2) "실행이 끝나면 스스로 내일을 재예약"하는 체인 구조라, 단 한 번이라도 실행이
 * 스킵되면 그 뒤로 영구히 알림이 멈춤(재예약 자체가 안 일어나므로). AlarmManager.
 * setExactAndAllowWhileIdle()로 예약하면 (1)이 해결되고, 매번 다음 알람도 함께 새로
 * 걸어두면(리시버 쪽에서) (2)도 해결됨 - 그리고 재부팅 시 알람이 전부 사라지는 문제는
 * BootCompletedReceiver가 커버함.
 */
object NotificationScheduler {

    const val CHANNEL_ID = "mission_reminder_channel"
    private const val REQUEST_CODE_MORNING = 3001
    private const val REQUEST_CODE_LUNCH = 3002
    private const val REQUEST_CODE_EVENING = 3003
    private const val REQUEST_CODE_REST_GIVEUP = 3004
    private const val PREFS_NAME = "notification_scheduler_prefs"
    private const val KEY_LAST_SCHEDULED_SLOTS = "last_scheduled_slots"

    // 쉼·포기 알림은 사용자가 못 바꾸는 고정 시각(요구사항: "하루 1번 오후 7시").
    const val REST_GIVEUP_TIME = "19:00"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "오늘의 미션 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "오늘 미션을 아직 안 했거나, 쉬어가기·포기 상태일 때 하루에 몇 번 알려드려요."
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun areNotificationsPermitted(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** ⚠️ 2026-09-18 추가 - Android 12(API 31)부터 정확한 알람은 이 권한이 별도로 필요.
     * 대부분 기기는 설치 시 자동 허용되지만, 일부 제조사/설정에서 꺼져 있을 수 있음 - 꺼져
     * 있으면 setExactAndAllowWhileIdle()가 SecurityException을 던지므로 미리 확인. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 31) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    /** 3개 리마인더(오전/점심/저녁) + 쉼·포기 알림(오후 7시 고정)을 전부 등록.
     * times는 [오전, 점심, 저녁] 순서 3개, 각 "HH:MM" 문자열. */
    fun scheduleAll(context: Context, morningTime: String, lunchTime: String, eveningTime: String) {
        ensureChannel(context)
        scheduleMissionAlarm(context, REQUEST_CODE_MORNING, morningTime, MissionReminderReceiver.SLOT_MORNING)
        scheduleMissionAlarm(context, REQUEST_CODE_LUNCH, lunchTime, MissionReminderReceiver.SLOT_LUNCH)
        scheduleMissionAlarm(context, REQUEST_CODE_EVENING, eveningTime, MissionReminderReceiver.SLOT_EVENING)
        scheduleRestGiveUpAlarm(context)
    }

    /** slots = [오전, 점심, 저녁] "HH:MM" 리스트(서버 NotificationSetting.slots 그대로).
     * 순서가 안 맞거나 값이 없으면 기본값(08:00/12:00/18:00)으로 채움. */
    fun scheduleAllExact(context: Context, slots: List<String>) {
        val normalized = listOf(
            slots.getOrNull(0) ?: "08:00",
            slots.getOrNull(1) ?: "12:00",
            slots.getOrNull(2) ?: "18:00",
        )
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_SCHEDULED_SLOTS, normalized.joinToString(",")).apply()
        scheduleAll(context, morningTime = normalized[0], lunchTime = normalized[1], eveningTime = normalized[2])
    }

    /** ⚠️ 2026-09-18 추가 - BootCompletedReceiver가 재부팅 직후 부르는 진입점. 마지막으로
     * 저장해둔 슬롯 값으로 다시 예약함(로그인만 되어 있으면 서버 재조회 없이 바로 가능). */
    fun rescheduleFromLastKnownSlots(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LAST_SCHEDULED_SLOTS, null) ?: return
        val parts = saved.split(",")
        if (parts.size != 3) return
        scheduleAll(context, morningTime = parts[0], lunchTime = parts[1], eveningTime = parts[2])
    }

    /** 알림 동의를 철회했을 때 - 예약된 걸 전부 취소. */
    fun cancelAll(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(missionPendingIntent(context, REQUEST_CODE_MORNING, MissionReminderReceiver.SLOT_MORNING, "08:00"))
        manager.cancel(missionPendingIntent(context, REQUEST_CODE_LUNCH, MissionReminderReceiver.SLOT_LUNCH, "12:00"))
        manager.cancel(missionPendingIntent(context, REQUEST_CODE_EVENING, MissionReminderReceiver.SLOT_EVENING, "18:00"))
        manager.cancel(restGiveUpPendingIntent(context))
        // ⚠️ 마지막 예약값 기록도 같이 지움 - 안 지우면 나중에 동의를 다시 켰을 때
        // 부팅 복구(rescheduleFromLastKnownSlots)가 취소된 값으로 되살릴 수 있음.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LAST_SCHEDULED_SLOTS).apply()
    }

    private fun scheduleMissionAlarm(context: Context, requestCode: Int, time: String, slot: String) {
        if (!canScheduleExactAlarms(context)) {
            android.util.Log.w("NotificationScheduler", "[$slot] 정확한 알람 권한이 없어 예약 스킵")
            return
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextOccurrenceMillis(time)
        manager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAt, missionPendingIntent(context, requestCode, slot, time),
        )
    }

    private fun scheduleRestGiveUpAlarm(context: Context) {
        if (!canScheduleExactAlarms(context)) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextOccurrenceMillis(REST_GIVEUP_TIME)
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, restGiveUpPendingIntent(context))
    }

    private fun missionPendingIntent(context: Context, requestCode: Int, slot: String, time: String): PendingIntent {
        val intent = Intent(context, MissionReminderReceiver::class.java).apply {
            putExtra(MissionReminderReceiver.EXTRA_SLOT, slot)
            putExtra(MissionReminderReceiver.EXTRA_TIME, time)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun restGiveUpPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RestGiveUpReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_REST_GIVEUP, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 리시버가 알림을 처리한 뒤 "내일 같은 시각"으로 다시 거는 용도.
     * slot=null이면 쉼·포기 리마인더(고정 시각). uniqueWorkName은 예전 WorkManager
     * 시절 이름을 그대로 재사용해서 requestCode 매핑에 씀. */
    fun rescheduleTomorrowExact(context: Context, uniqueWorkName: String, time: String, slot: String?) {
        if (slot != null) {
            scheduleMissionAlarm(context, requestCodeFor(uniqueWorkName), time, slot)
        } else {
            scheduleRestGiveUpAlarm(context)
        }
    }

    private fun requestCodeFor(uniqueWorkName: String): Int = when (uniqueWorkName) {
        WORK_MORNING -> REQUEST_CODE_MORNING
        WORK_LUNCH -> REQUEST_CODE_LUNCH
        else -> REQUEST_CODE_EVENING
    }

    private const val WORK_MORNING = "mission_reminder_morning"
    private const val WORK_LUNCH = "mission_reminder_lunch"
    private const val WORK_EVENING = "mission_reminder_evening"

    fun uniqueWorkNameFor(slot: String): String = when (slot) {
        MissionReminderReceiver.SLOT_MORNING -> WORK_MORNING
        MissionReminderReceiver.SLOT_LUNCH -> WORK_LUNCH
        else -> WORK_EVENING
    }

    val restGiveUpWorkName: String get() = "rest_giveup_reminder"

    /** 지금부터 "오늘 또는 내일의 HH:MM"까지 남은 시각(epoch millis). 이미 오늘 그 시각이
     * 지났으면 자동으로 내일 그 시각을 기준으로 계산함. */
    private fun nextOccurrenceMillis(time: String): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.now(zone)
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(zone)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }
}
