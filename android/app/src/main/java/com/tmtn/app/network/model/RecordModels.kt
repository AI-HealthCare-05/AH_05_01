package com.tmtn.app.network.model

// ===== D01: 월 캘린더 =====
data class CalendarDayItem(
    val date: String,       // "YYYY-MM-DD"
    val status: String       // "COMPLETED" / "INCOMPLETE" / "REST"
)

data class MonthlyCalendarResponse(
    val year: Int,
    val month: Int,
    val days: List<CalendarDayItem>,
    val completed_count: Int,
    val rest_count: Int
)

// ===== D02: 주간 리포트 =====
data class WeeklyMaterialItem(
    val element: String,
    val material_name: String,
    val count: Int
)

data class WeeklyReportResponse(
    val start_date: String,
    val end_date: String,
    val days: List<CalendarDayItem>,
    val completed_count: Int,
    val total_days: Int,
    val best_time_slot: String?,
    val materials_this_week: List<WeeklyMaterialItem>
)

// ===== D03: 하루 상세 =====
data class DayDetailResponse(
    val date: String,
    val status: String,
    val mission_title: String?,
    val exec_type: String?,
    val completed_at: String?,
    val duration_seconds: Int?,
    val count_achieved: Int?,
    val element: String?,
    val material_name: String?,
    val memo: String?
)
