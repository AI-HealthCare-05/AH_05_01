package com.tmtn.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * ⚠️ 2026-09-08 추가 - 미션 미완료(오전/점심/저녁 3회) · 쉼·포기(오후 7시 고정) 리마인더
 * 로컬 알림. 서버가 실제 FCM 발송 인프라를 아직 안 갖고 있어서(토큰 저장 모델만 있고
 * 실제 전송 로직·스케줄러는 없음), 기기에서 직접 스케줄링하는 로컬 알림으로 구현함 -
 * 서버 인프라가 나중에 생기면 이 부분만 FCM 수신으로 바꾸면 됨.
 *
 * WorkManager의 PeriodicWorkRequest는 최소 주기가 15분이라 "정확히 오전 8시"처럼 특정
 * 시각을 못 맞춤. 대신 "다음 실행 시각까지 남은 시간"을 계산해서 OneTimeWorkRequest로
 * 예약하고, Worker 자신이 실행 끝날 때 "내일 같은 시각"으로 스스로 재예약하는 방식(self-
 * rescheduling)을 씀 - 정확한 시각 제어 + 기기 재부팅 후에도 앱을 한 번 열면 복구됨.
 *
 * ⚠️ 2026-09-08 개정: 처음엔 오전/점심/저녁을 "정해진 3개 후보 중 택1"(시 단위만)로
 * 만들었다가, "자고 일어나는 시각 기반 자동 계산(기상+2시간/점심 직접입력/취침-2시간)"으로
 * 요구사항이 바뀌면서 임의의 "분" 값도 나올 수 있게 됐음(예: 07:23) - 시(hour)만 쓰던
 * API를 전부 "HH:MM" 문자열 기반으로 바꿔서 분까지 정확히 반영함.
 */
object NotificationScheduler {

    const val CHANNEL_ID = "mission_reminder_channel"
    private const val WORK_MORNING = "mission_reminder_morning"
    private const val WORK_LUNCH = "mission_reminder_lunch"
    private const val WORK_EVENING = "mission_reminder_evening"
    private const val WORK_REST_GIVEUP = "rest_giveup_reminder"

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

    /** 3개 리마인더(오전/점심/저녁) + 쉼·포기 알림(오후 7시 고정)을 전부 등록.
     * times는 [오전, 점심, 저녁] 순서 3개, 각 "HH:MM" 문자열. */
    fun scheduleAll(context: Context, morningTime: String, lunchTime: String, eveningTime: String) {
        ensureChannel(context)
        scheduleOne(context, WORK_MORNING, morningTime, MissionReminderWorker.SLOT_MORNING)
        scheduleOne(context, WORK_LUNCH, lunchTime, MissionReminderWorker.SLOT_LUNCH)
        scheduleOne(context, WORK_EVENING, eveningTime, MissionReminderWorker.SLOT_EVENING)
        scheduleRestGiveUp(context)
    }

    /** slots = [오전, 점심, 저녁] "HH:MM" 리스트(서버 NotificationSetting.slots 그대로).
     * 순서가 안 맞거나 값이 없으면 기본값(08:00/12:00/18:00)으로 채움. */
    fun scheduleAllExact(context: Context, slots: List<String>) {
        scheduleAll(
            context,
            morningTime = slots.getOrNull(0) ?: "08:00",
            lunchTime = slots.getOrNull(1) ?: "12:00",
            eveningTime = slots.getOrNull(2) ?: "18:00",
        )
    }

    /** 알림 동의를 철회했을 때 - 예약된 걸 전부 취소. */
    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_MORNING)
        workManager.cancelUniqueWork(WORK_LUNCH)
        workManager.cancelUniqueWork(WORK_EVENING)
        workManager.cancelUniqueWork(WORK_REST_GIVEUP)
    }

    private fun scheduleOne(context: Context, uniqueWorkName: String, time: String, slot: String) {
        val delay = millisUntilNextOccurrence(time)
        val request = OneTimeWorkRequestBuilder<MissionReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(MissionReminderWorker.KEY_SLOT, slot)
                    .putString(MissionReminderWorker.KEY_TIME, time)
                    .build()
            )
            .build()
        // REPLACE: 시간대 설정을 바꿨을 때, 예전 시각으로 예약돼 있던 걸 새 시각으로 덮어씀.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleRestGiveUp(context: Context) {
        val delay = millisUntilNextOccurrence(REST_GIVEUP_TIME)
        val request = OneTimeWorkRequestBuilder<RestGiveUpReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_REST_GIVEUP, ExistingWorkPolicy.KEEP, request)
    }

    /** Worker가 자기 자신을 "내일 같은 시각"으로 재예약할 때 씀. slot=null이면 쉼·포기
     * 리마인더(고정 시각, RestGiveUpReminderWorker)를 재예약. */
    fun rescheduleTomorrow(context: Context, uniqueWorkName: String, time: String, slot: String?) {
        val delay = millisUntilNextOccurrence(time)
        val request = if (slot != null) {
            OneTimeWorkRequestBuilder<MissionReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(MissionReminderWorker.KEY_SLOT, slot)
                        .putString(MissionReminderWorker.KEY_TIME, time)
                        .build()
                )
                .build()
        } else {
            OneTimeWorkRequestBuilder<RestGiveUpReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
        }
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, request)
    }

    fun uniqueWorkNameFor(slot: String): String = when (slot) {
        MissionReminderWorker.SLOT_MORNING -> WORK_MORNING
        MissionReminderWorker.SLOT_LUNCH -> WORK_LUNCH
        else -> WORK_EVENING
    }

    val restGiveUpWorkName: String get() = WORK_REST_GIVEUP

    /** 지금부터 "오늘 또는 내일의 HH:MM"까지 남은 밀리초. 이미 오늘 그 시각이
     * 지났으면 자동으로 내일 그 시각을 기준으로 계산함. */
    private fun millisUntilNextOccurrence(time: String): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.now(zone)
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(zone)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis()
    }
}
