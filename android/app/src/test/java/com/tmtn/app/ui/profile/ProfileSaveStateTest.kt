package com.tmtn.app.ui.profile

import com.tmtn.app.network.ProfileApi
import com.tmtn.app.network.model.NotificationSettingResponse
import com.tmtn.app.network.model.NotificationSettingUpdateRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class ProfileSaveStateTest {
    private val setting = NotificationSettingResponse("Asia/Seoul", listOf("07:00", "13:00", "21:00"), emptyList(), null, false, "2026-09-14T12:00:00Z")

    @Test fun editingSchedulePreservesOffSwitchAndUsesExistingSlotMapping() = runBlocking {
        var sent: NotificationSettingUpdateRequest? = null
        val state = ProfileState { api { name, args ->
            check(name == "updateNotificationSettings")
            sent = args!![0] as NotificationSettingUpdateRequest
            Response.success(setting.copy(slots = sent!!.slots!!))
        } }.apply { notificationSetting.value = setting }
        state.saveWakeSleep("08:30", "12:00", "01:00")
        assertEquals(listOf("08:30", "13:00", "23:00"), sent?.slots)
        assertNull(sent?.enabled)
        assertEquals(false, state.notificationSetting.value?.enabled)
    }

    @Test fun failedSaveDoesNotReplaceTheStoredSettingOrCloseTheEditor() = runBlocking {
        val state = ProfileState { api { _, _ -> Response.error<Any>(503, "".toResponseBody()) } }.apply {
            notificationSetting.value = setting
            screen.value = ProfileScreenKey.NOTIFICATION_TIME
        }
        state.updateSlotTime(1, "14:00")
        assertEquals(setting, state.notificationSetting.value)
        assertEquals(ProfileScreenKey.NOTIFICATION_TIME, state.screen.value)
        assertNotNull(state.errorMessage.value)
        assertFalse(state.isLoading.value)
    }

    @Test fun cancellationPropagatesAndAlwaysReleasesTheSavingState() = runBlocking {
        val state = ProfileState { api { _, _ -> throw CancellationException("test cancellation") } }.apply { notificationSetting.value = setting }
        var cancelled = false
        try { state.toggleNotificationsEnabled(true) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertFalse(state.isLoading.value)
        assertEquals(setting, state.notificationSetting.value)
    }

    @Test fun failedFileWriteNeverReportsExportSuccess() = runBlocking {
        val state = ProfileState { api { name, _ -> check(name == "exportMyData"); Response.success("date,status\n".toResponseBody()) } }
        var saved = false
        state.exportMyData { _, _ ->
            error("저장 공간을 확인해 주세요.")
            @Suppress("UNREACHABLE_CODE")
            saved = true
        }
        assertFalse(saved)
        assertNotNull(state.errorMessage.value)
        assertFalse(state.isLoading.value)
    }

    private fun api(block: (String, Array<out Any?>?) -> Any): ProfileApi =
        java.lang.reflect.Proxy.newProxyInstance(ProfileApi::class.java.classLoader, arrayOf(ProfileApi::class.java)) { _, method, args -> block(method.name, args) } as ProfileApi
}
