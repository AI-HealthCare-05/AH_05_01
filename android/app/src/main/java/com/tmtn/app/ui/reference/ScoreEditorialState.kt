package com.tmtn.app.ui.reference

import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.ScoreInputsResponse
import com.tmtn.app.network.model.WeeklyReportResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Read-only supporting material. Independent failures never replace a valid score. */
internal sealed interface EditorialLoad<out T> {
    data object Loading : EditorialLoad<Nothing>
    data class Ready<T>(val value: T) : EditorialLoad<T>
    data object Failed : EditorialLoad<Nothing>
}

internal class ScoreEditorialState {
    val inputs = mutableStateOf<EditorialLoad<ScoreInputsResponse>>(EditorialLoad.Loading)
    val weekly = mutableStateOf<EditorialLoad<WeeklyReportResponse>>(EditorialLoad.Loading)

    suspend fun load() = coroutineScope {
        launch { loadInputs() }
        launch { loadWeekly() }
    }

    suspend fun loadInputs() {
        inputs.value = EditorialLoad.Loading
        inputs.value = read {
            val response = ApiClient.tuntunScoreApi.getTuntunScoreInputs()
            check(response.isSuccessful)
            checkNotNull(response.body())
        }
    }

    suspend fun loadWeekly() {
        weekly.value = EditorialLoad.Loading
        weekly.value = read {
            val response = ApiClient.recordApi.getWeeklyReport()
            check(response.isSuccessful)
            checkNotNull(response.body())
        }
    }

    private suspend fun <T> read(block: suspend () -> T): EditorialLoad<T> = try {
        EditorialLoad.Ready(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        EditorialLoad.Failed
    }
}

internal data class EditorialFact(val label: String, val value: String)

internal fun scoreInputFacts(inputs: ScoreInputsResponse, area: String): List<EditorialFact> {
    fun number(value: Double?, unit: String) = value?.takeIf { it.isFinite() && it > 0 }
        ?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() + " " + unit } ?: "미입력"
    fun minutes(value: Int?) = value?.takeIf { it >= 0 }?.let { "${it}분 / 주" } ?: "미입력"
    val body = listOf(
        EditorialFact("생년월", inputs.birth_month_label?.takeIf { it.isNotBlank() } ?: "미입력"),
        EditorialFact("성별", inputs.sex_label?.takeIf { it.isNotBlank() } ?: "미입력"),
        EditorialFact("키", number(inputs.height_cm, "cm")),
        EditorialFact("몸무게", number(inputs.weight_kg, "kg")),
    )
    val exercise = listOf(
        EditorialFact("근력운동", inputs.strength_label?.takeIf { it.isNotBlank() } ?: "미입력"),
        EditorialFact("가벼운 유산소", minutes(inputs.cardio_low_min)),
        EditorialFact("적당한 유산소", minutes(inputs.cardio_moderate_min)),
        EditorialFact("강한 유산소", minutes(inputs.cardio_vigorous_min)),
    )
    // This is editorial grouping, never model contribution or feature-importance attribution.
    return when (area) { "physical" -> body; "lifestyle" -> exercise; else -> body + exercise }
}

internal data class RecordNews(val headline: String, val detail: String, val evening: Boolean, val empty: Boolean)

internal fun recordNews(report: WeeklyReportResponse): RecordNews? {
    // Missing or inconsistent server values must not become invented activity or a zero record.
    if (report.total_days <= 0 || report.completed_count !in 0..report.total_days) return null
    val count = report.completed_count
    if (count == 0) return RecordNews("이번 호엔 아직\n실천 기록이 없어요.", "첫 실천을 마치면 여기에 소식이 남아요.", false, true)
    val time = report.best_time_slot?.takeIf { it in setOf("아침", "오전", "점심", "오후", "저녁", "밤") }
    val headline = when {
        count >= 3 && time != null -> "내 실천은\n${time}에 모였네요."
        count == 1 -> "첫 실천이\n소식으로 남았어요."
        else -> "작은 실천이\n기록으로 모였어요."
    }
    return RecordNews(headline, "최근 ${report.total_days}일 중 ${count}일 실천했어요.",
        count >= 3 && time in setOf("저녁", "밤"), false)
}
