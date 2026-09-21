package com.tmtn.app.ui.record

import com.tmtn.app.ui.common.failWithMessage
import com.tmtn.app.ui.common.userMessageOr
import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.DayDetailResponse
import com.tmtn.app.network.model.MemoUpdateRequest
import com.tmtn.app.network.model.MonthlyCalendarResponse
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
    var weeklyLoadFailed = mutableStateOf(false)
    var previousWeek = mutableStateOf<List<com.tmtn.app.network.model.CalendarDayItem>?>(null)

    // D03 (하루 상세 바텀시트)
    var selectedDate = mutableStateOf<String?>(null)
    var dayDetail = mutableStateOf<DayDetailResponse?>(null)
    var showDaySheet = mutableStateOf(false)
    var dayLoadFailed = mutableStateOf(false)
    var memoSaving = mutableStateOf(false)
    var memoError = mutableStateOf<String?>(null)
    private var dayRequest = 0

    // D05: 연속기록
    var streak = mutableStateOf<StreakResponse?>(null)

    suspend fun loadMonthly(y: Int = year.value, m: Int = month.value) {
        if (isLoading.value) return
        isLoading.value = true
        errorMessage.value = null
        monthlyLoadFailed.value = false
        runCatching {
            val response = ApiClient.recordApi.getMonthlyCalendar(y, m)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            year.value = y
            month.value = m
            monthlyCalendar.value = it
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("이번 달 기록을 가져오지 못했어요.")
            monthlyLoadFailed.value = true
        }
        isLoading.value = false
        loadStreak()
    }

    suspend fun loadWeekly() {
        if (isLoading.value) return
        isLoading.value = true
        errorMessage.value = null
        weeklyLoadFailed.value = false
        runCatching {
            val response = ApiClient.recordApi.getWeeklyReport()
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess {
            weeklyReport.value = it
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("이번 주 리포트를 가져오지 못했어요.")
            weeklyLoadFailed.value = true
        }
        isLoading.value = false
        if (!weeklyLoadFailed.value) loadPreviousWeek()
    }

    /** Uses the existing monthly calendar for two comparable seven-day periods. */
    private suspend fun loadPreviousWeek() {
        val report = weeklyReport.value ?: return
        previousWeek.value = null
        val start = runCatching { LocalDate.parse(report.start_date).minusDays(7) }.getOrNull() ?: return
        val dates = (0L..6L).map { start.plusDays(it) }
        try {
            val days = dates.map { java.time.YearMonth.from(it) }.distinct().flatMap { month ->
                val cached = monthlyCalendar.value?.takeIf { it.year == month.year && it.month == month.monthValue }
                (cached ?: ApiClient.recordApi.getMonthlyCalendar(month.year, month.monthValue).let {
                    if (!it.isSuccessful) return
                    it.body() ?: return
                }).days
            }.associateBy { it.date }
            val matched = dates.mapNotNull { days[it.toString()] }
            if (matched.size == 7 && matched.none { it.status == "BEFORE_SIGNUP" }) previousWeek.value = matched
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Exception) { /* Current week remains readable. */ }
    }

    suspend fun loadStreak() {
        runCatching {
            val response = ApiClient.cardHomeApi.getStreak()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { streak.value = it }
    }

    // D01/D02 -> D03: 날짜 하나 눌렀을 때
    suspend fun openDayDetail(date: String) {
        if (memoSaving.value) return
        val request = ++dayRequest
        selectedDate.value = date
        showDaySheet.value = true
        dayDetail.value = null
        dayLoadFailed.value = false
        memoError.value = null
        runCatching {
            val response = ApiClient.recordApi.getDayDetail(date)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { if (request == dayRequest && showDaySheet.value) dayDetail.value = it }
            .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (request == dayRequest) dayLoadFailed.value = true
            }
    }

    fun closeDaySheet() {
        if (memoSaving.value) return
        dayRequest++
        showDaySheet.value = false
    }

    // D03: 메모 저장/삭제 - 저장 뒤 하루 상세를 조용히 다시 불러와 화면 갱신
    suspend fun saveMemo(memo: String?): Boolean {
        val date = selectedDate.value ?: return false
        val known = dayDetail.value ?: return false
        if (memoSaving.value || known.date != date) return false
        memoSaving.value = true
        memoError.value = null
        return try {
            val response = ApiClient.cardHomeApi.updateDayMemo(date, MemoUpdateRequest(memo = memo))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            if (selectedDate.value == date) dayDetail.value = known.copy(memo = memo)
            true
        } catch (c: kotlinx.coroutines.CancellationException) { throw c }
        catch (_: Exception) { memoError.value = "메모를 저장하지 못했어요. 입력한 내용은 그대로예요."; false }
        finally { memoSaving.value = false }
    }
}
