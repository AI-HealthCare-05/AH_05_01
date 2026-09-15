package com.tmtn.app.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.DayDetailResponse
import com.tmtn.app.ui.cardhome.MaterialIcon
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Figma E02/E03: the full date page and memo editor keep the main navigation visible. */
@Composable
fun DayDetailSheet(state: RecordState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val detail = state.dayDetail.value
    val date = state.selectedDate.value
    var editing by rememberSaveable(date) { mutableStateOf(false) }
    var memoText by rememberSaveable(date, detail?.memo) { mutableStateOf(detail?.memo.orEmpty()) }
    val back: () -> Unit = { if (!state.memoSaving.value) { if (editing) editing = false else state.closeDaySheet() } }
    BackHandler { back() }
    val dateLabel = runCatching { LocalDate.parse(date) }.getOrNull()?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: "하루"
    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        TmtnTopBar(if (editing) "오늘의 메모" else "$dateLabel 기록", back)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when {
                state.dayLoadFailed.value -> {
                    Text("하루 기록을 불러오지 못했어요.", style = TmtnType.title, color = colors.onSurface)
                    TmtnTonalButton("다시 불러오기", { date?.let { scope.launch { state.openDayDetail(it) } } })
                }
                detail == null -> Text("이날의 기록을 펼치고 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                editing -> {
                    Text("숫자에 담기지 않은\n오늘도 남겨요.", style = TmtnType.headline, color = colors.onSurface)
                    Text(dateLabel, style = TmtnType.caption, color = colors.onSurfaceVariant)
                    TmtnTextField(memoText, { memoText = it.take(100) }, "한 줄 메모", supportingText = "${memoText.length} / 100자", enabled = !state.memoSaving.value, singleLine = false)
                    Text("실천하며 느낀 점을 적어 보세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("틈튼이", style = TmtnType.label, color = colors.onSurface)
                        Text("멋진 문장이 아니어도 돼. 오늘의 너를 기억할 한 줄이면 충분해.", style = TmtnType.body, color = colors.onSurface)
                    }
                    OnboardingErrorMessage(state.memoError.value)
                    TmtnPrimaryButton(if (state.memoSaving.value) "저장 중…" else "저장", {
                        scope.launch { if (state.saveMemo(memoText.trim().ifBlank { null })) editing = false }
                    }, enabled = !state.memoSaving.value && memoText.trim() != detail.memo.orEmpty())
                    TmtnTonalButton("취소", { editing = false; memoText = detail.memo.orEmpty() }, enabled = !state.memoSaving.value)
                }
                else -> {
                    Text(when (detail.status) {
                        "COMPLETED" -> "오늘의 작은 실천을\n기억해 둘게요."
                        "REST" -> "쉬어 간 하루도\n함께 남겨뒀어요."
                        "BEFORE_SIGNUP" -> "틈튼을 만나기\n전의 날이에요."
                        else -> "잠시 비워 둔 하루."
                    }, style = TmtnType.headline, color = colors.onSurface)
                    Text(statusLabel(detail.status), style = TmtnType.caption, color = colors.onSurfaceVariant)
                    detail.mission_title?.let { title ->
                        Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(title, style = TmtnType.title, color = colors.onSurface)
                            completionCaption(detail).takeIf { it.isNotBlank() }?.let { Text(it, style = TmtnType.body, color = colors.onSurfaceVariant) }
                        }
                    }
                    if (detail.status == "COMPLETED" && detail.element != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MaterialIcon(detail.element, 64.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("모은 재료", style = TmtnType.label, color = colors.onSurfaceVariant)
                            Text("${detail.material_name.orEmpty()} 1개", style = TmtnType.title, color = colors.onSurface)
                        }
                    }
                    if (!detail.memo.isNullOrBlank()) {
                        Text("오늘의 메모", style = TmtnType.label, color = colors.onSurfaceVariant)
                        Text(detail.memo, style = TmtnType.body, color = colors.onSurface)
                    }
                    if (detail.status != "BEFORE_SIGNUP" && runCatching { LocalDate.parse(detail.date).isAfter(LocalDate.now()) }.getOrDefault(true).not()) {
                        TmtnTonalButton(if (detail.memo.isNullOrBlank()) "메모 남기기" else "메모 수정하기", { editing = true })
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "COMPLETED" -> "실천 완료"
    "REST" -> "쉼"
    "INCOMPLETE" -> "미완료"
    "BEFORE_SIGNUP" -> "가입 전"
    else -> "기록 없음"
}

private fun completionCaption(detail: DayDetailResponse): String = buildList {
    if (detail.status == "COMPLETED") detail.completed_at?.let { recordCompletionTime(it)?.let { time -> add("$time 완료") } }
    detail.duration_seconds?.takeIf { it > 0 }?.let { add("${it / 60}분 ${it % 60}초") }
    detail.count_achieved?.takeIf { it > 0 }?.let { add("${it}회") }
}.joinToString(" · ")
