package kr.tmtn.app.ui

import kr.tmtn.app.domain.model.DailyRecord
import kr.tmtn.app.domain.model.DamMaterial
import kr.tmtn.app.domain.model.DamStage
import kr.tmtn.app.domain.model.DayStatus

/**
 * 기록·댐 화면들이 그대로 그리기만 하면 되도록 미리 계산해 둔 값들.
 *
 * 화면은 여기 있는 값을 **읽기만** 한다. 날짜를 세거나 재료를 묶는 계산을 화면에서 하지 않는다.
 * 화면마다 따로 세면 같은 주를 두고 숫자가 달라지기 때문이다.
 *
 * 만드는 곳은 [TodayViewModel] 이다 — `today.weekReport()` 처럼 부르면 된다.
 */

/** 주간 스트립(D02)·달력의 한 칸 */
data class WeekDayCell(
    val dateKey: String,        // 2026-08-24
    val dayOfMonth: Int,        // 24
    val weekdayShort: String,   // 월
    val status: DayStatus,      // DONE · REST · MISSED · FUTURE
    val isToday: Boolean,
)

/** 재료 하나와 그 개수 */
data class MaterialCount(
    val material: DamMaterial,
    val count: Int,
) {
    /** "나뭇가지 1개" */
    val label: String get() = "${material.displayName} ${count}개"
}

/**
 * D02 · 주간 리포트 한 장에 필요한 것 전부.
 *
 * `weekOffset = 0` 이 이번 주, `-1` 이 지난주다.
 */
data class WeekReport(
    val weekOffset: Int,
    val weekStartKey: String,
    val rangeLabel: String,         // "2026. 8. 24. ~ 8. 30."
    val days: List<WeekDayCell>,    // 항상 7개 (월~일)
    val doneCount: Int,
    val restCount: Int,
    val missedCount: Int,
    /** 아직 오지 않은 날. **오늘은 세지 않는다** (오늘은 아직 할 수 있으므로) */
    val remainingCount: Int,
    /** 이번 주에 모은 재료. 0개인 재료는 빠져 있다 */
    val materials: List<MaterialCount>,
    val materialsThisWeek: Int,
    /** 지금까지(전체) 모은 재료 수 */
    val collectedTotal: Int,
    val dam: DamStage,
    /** "한 번 쉬고 두 번 해냈어.\n아직 사흘 남았으니 천천히 가." */
    val beaverLine: String,
    val isThisWeek: Boolean,
) {
    /** "이번 주 2일 실천" · 지난주면 "2일 실천" */
    val headline: String get() = if (isThisWeek) "이번 주 ${doneCount}일 실천" else "${doneCount}일 실천"

    /** 기록이 하나도 없는 주인가. 빈 화면 문구를 띄울지 판단한다 */
    val isEmpty: Boolean get() = doneCount == 0 && restCount == 0
}

/** G05 · 재료별 기록 한 줄 */
data class MaterialLog(
    val title: String,          // "점심 먹고 8분 걷기"
    val whenLabel: String,      // "2026. 8. 27. 오후 1:12"
    val record: DailyRecord,
)

/** G05 · 재료 하나의 상세 */
data class MaterialDetail(
    val material: DamMaterial,
    val count: Int,
    /** 최근 것부터. 기본 5개까지만 */
    val logs: List<MaterialLog>,
    /** 잘라내기 전 전체 개수 (`logs.size` 보다 클 수 있다) */
    val totalLogs: Int,
)

/** G06 · 카드첩의 카드 한 장 */
data class CollectedCard(
    val record: DailyRecord,
    val material: DamMaterial?,
    val dateLabel: String,      // "2026. 8. 27."
)
