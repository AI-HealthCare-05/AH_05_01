package com.tmtn.app.ui.record

import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.DayDetailResponse
import com.tmtn.app.network.model.MemoUpdateRequest
import com.tmtn.app.network.model.MonthlyCalendarResponse
import com.tmtn.app.network.model.RestDayRequest
import com.tmtn.app.network.model.StreakResponse
import com.tmtn.app.network.model.WeeklyReportResponse
import com.tmtn.app.ui.onboarding.parseErrorMessage
import java.time.LocalDate

enum class RecordTab { MONTHLY, WEEKLY }

/** D01~D08 "기록" 탭 전체 상태. Navigation Compose 없이 다른 흐름들과 동일한 패턴. */
class RecordState {
    var tab = mutableStateOf(RecordTab.MONTHLY)
    var isLoading = mutableStateOf(false)
    var errorMessage = mutableStateOf<String?>(null)

    // D01
    var year = mutableStateOf(LocalDate.now().year)
    var month = mutableStateOf(LocalDate.now().monthValue)
    var monthlyCalendar = mutableStateOf<MonthlyCalendarResponse?>(null)
    var monthlyLoadFailed = mutableStateOf(false) // D07: 일부 실패해도 화면은 보여줌

    // D02
    var weeklyReport = mutableStateOf<WeeklyReportResponse?>(null)

    // D03 (하루 상세 바텀시트)
    var selectedDate = mutableStateOf<String?>(null)
    var dayDetail = mutableStateOf<DayDetailResponse?>(null)
    var showDaySheet = mutableStateOf(false)

    // D05/D06: 연속기록 · 쉼
    var streak = mutableStateOf<StreakResponse?>(null)
    var showRestSheet = mutableStateOf(false)

    suspend fun loadMonthly(y: Int = year.value, m: Int = month.value) {
        isLoading.value = true
        errorMessage.value = null
        monthlyLoadFailed.value = false
        runCatching {
            val response = ApiClient.recordApi.getMonthlyCalendar(y, m)
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            year.value = y
            month.value = m
            monthlyCalendar.value = it
        }.onFailure { e ->
            errorMessage.value = e.message ?: "이번 달 기록을 가져오지 못했어요."
            monthlyLoadFailed.value = true
        }
        isLoading.value = false
        loadStreak()
    }

    suspend fun loadWeekly() {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.recordApi.getWeeklyReport()
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            weeklyReport.value = it
        }.onFailure { e ->
            errorMessage.value = e.message ?: "이번 주 리포트를 가져오지 못했어요."
        }
        isLoading.value = false
    }

    suspend fun loadStreak() {
        runCatching {
            val response = ApiClient.cardHomeApi.getStreak()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { streak.value = it }
    }

    // D01/D02 -> D03: 날짜 하나 눌렀을 때
    suspend fun openDayDetail(date: String) {
        selectedDate.value = date
        showDaySheet.value = true
        dayDetail.value = null
        runCatching {
            val response = ApiClient.recordApi.getDayDetail(date)
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { dayDetail.value = it }
            .onFailure { e -> errorMessage.value = e.message ?: "하루 기록을 가져오지 못했어요." }
    }

    fun closeDaySheet() {
        showDaySheet.value = false
    }

    // D03 -> D06: "쉼으로 표시" 바텀시트 열기
    suspend fun openRestSheetFor(date: String) {
        selectedDate.value = date
        showRestSheet.value = true
        loadStreak()
    }

    fun closeRestSheet() {
        showRestSheet.value = false
    }

    // D06: "쉼으로 표시하기" 확정 - 실제 기록 API 재사용 (B16/B17과 동일 엔드포인트)
    suspend fun confirmRestDay() {
        val date = selectedDate.value ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.markRestDay(RestDayRequest(service_date = date))
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            streak.value = it
            showRestSheet.value = false
            loadMonthly()
        }.onFailure { e ->
            errorMessage.value = e.message ?: "쉼 표시에 실패했어요."
        }
        isLoading.value = false
    }

    // D03: 메모 저장/삭제 - 저장 뒤 하루 상세를 조용히 다시 불러와 화면 갱신
    suspend fun saveMemo(memo: String?) {
        val date = selectedDate.value ?: return
        runCatching {
            ApiClient.cardHomeApi.updateDayMemo(date, MemoUpdateRequest(memo = memo))
        }
        runCatching {
            val response = ApiClient.recordApi.getDayDetail(date)
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { dayDetail.value = it }
    }
}
