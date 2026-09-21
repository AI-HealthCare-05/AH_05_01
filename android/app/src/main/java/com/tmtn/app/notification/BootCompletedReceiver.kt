package com.tmtn.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tmtn.app.network.TokenHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ⚠️ 2026-09-18 신규 - AlarmManager로 예약한 알람은 기기가 재부팅되면 전부 사라진다
 * (WorkManager와 달리 시스템이 자동으로 복구해주지 않음). 예전 방식(주석: "재부팅 후에도
 * 앱을 한 번 열면 복구됨")은 사용자가 재부팅 후 앱을 안 열면 그때까지 리마인더가 전혀
 * 안 걸리는 문제가 있었음 - 부팅 완료 시점에 직접 재예약해서 이 틈을 없앰. 로그인
 * 안 돼 있으면(토큰 없음) 아무것도 안 함 - 다음 로그인/앱 실행 때 정상 경로로 예약됨.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TokenHolder.init(appContext)
                if (TokenHolder.accessToken == null) return@launch
                NotificationScheduler.rescheduleFromLastKnownSlots(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
