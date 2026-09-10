package com.tmtn.app.network

import com.tmtn.app.network.model.DayDetailResponse
import com.tmtn.app.network.model.MonthlyCalendarResponse
import com.tmtn.app.network.model.WeeklyReportResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RecordApi {

    @GET("records/calendar")
    suspend fun getMonthlyCalendar(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): Response<MonthlyCalendarResponse>

    @GET("records/weekly")
    suspend fun getWeeklyReport(): Response<WeeklyReportResponse>

    @GET("records/day/{date}")
    suspend fun getDayDetail(@Path("date") date: String): Response<DayDetailResponse>
}
