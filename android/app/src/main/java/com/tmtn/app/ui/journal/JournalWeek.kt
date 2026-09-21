package com.tmtn.app.ui.journal

import com.tmtn.app.network.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The server's end date is the Korean service date (including the debug date offset).
 * Home and Records still use the original rolling-seven-day response.
 */
internal fun mondayEdition(source: WeeklyReportResponse): WeeklyReportResponse? {
    val today = runCatching { LocalDate.parse(source.end_date) }.getOrNull() ?: return null
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val statuses = source.days.associateBy { it.date }
    val days = (0L..6L).map { offset ->
        val date = monday.plusDays(offset)
        CalendarDayItem(date.toString(), if (date > today) "FUTURE" else statuses[date.toString()]?.status ?: "UNKNOWN")
    }
    return source.copy(start_date = monday.toString(), end_date = monday.plusDays(6).toString(),
        days = days, total_days = 7, completed_count = days.count { it.status == "COMPLETED" },
        best_time_slot = null, materials_this_week = emptyList())
}

/** Day-detail dates, rather than challenge updated_at, also work on simulated service days.
 * completed_at carries the supplied date only here: no completion time is fabricated.
 */
internal fun weekCard(day: DayDetailResponse): CardHistoryItem? =
    if (day.status != "COMPLETED" || day.mission_title.isNullOrBlank() ||
        runCatching { LocalDate.parse(day.date) }.isFailure) null
    else CardHistoryItem(day.mission_title, day.element.orEmpty(), day.material_name.orEmpty(), "", day.date)

internal fun weekMaterials(cards: List<CardHistoryItem>): List<WeeklyMaterialItem> = cards
    .filter { it.five_element.isNotBlank() && it.material_name.isNotBlank() }
    .groupBy { it.five_element to com.tmtn.app.ui.common.tmtnMaterialName(it.five_element, it.material_name) }
    .map { (material, items) -> WeeklyMaterialItem(material.first, material.second, items.size) }
