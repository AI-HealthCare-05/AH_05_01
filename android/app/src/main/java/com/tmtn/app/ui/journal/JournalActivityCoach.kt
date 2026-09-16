package com.tmtn.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.theme.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** 설문에 맞춘 힌트와 실제 완료 기록에 따른 다음 안내를 구분합니다. */
internal data class JournalPracticeContext(
    val today: JournalLoad<JournalToday>,
    val collection: JournalLoad<List<CardHistoryItem>>,
    val exercises: JournalLoad<List<ExerciseMissionRecordItem>>? = null,
    val fallbackDate: LocalDate = LocalDate.now(JournalZone),
)

internal data class PracticeCompletion(val title: String, val date: LocalDate, val timestamp: String)
internal data class PracticeCopy(val title: String, val text: String, val reason: String, val action: String)

private fun strictCompletionDate(raw: String): LocalDate? =
    runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(JournalZone).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(raw) }.getOrNull()

internal fun practiceDate(context: JournalPracticeContext): LocalDate =
    runCatching { LocalDate.parse((context.today as? JournalLoad.Ready)?.value?.window?.service_date) }.getOrNull()
        ?: context.fallbackDate

internal fun recentPractice(context: JournalPracticeContext): List<PracticeCompletion> {
    val date = practiceDate(context)
    val cards = (context.collection as? JournalLoad.Ready)?.value.orEmpty().map { it.title to it.completed_at }
    val exercises = (context.exercises as? JournalLoad.Ready)?.value.orEmpty()
        .filter { it.reward_slot in 1..2 && it.completed_at != null }.map { it.title to it.completed_at!! }
    return (cards + exercises).mapNotNull { (title, stamp) ->
        val completed = strictCompletionDate(stamp)
        if (title.isBlank() || completed == null || completed < date.minusDays(6) || completed > date) null
        else PracticeCompletion(title, completed, stamp)
    }.distinctBy { it.title to it.timestamp }.sortedWith(compareByDescending<PracticeCompletion> { it.date }
        .thenByDescending { runCatching { OffsetDateTime.parse(it.timestamp).toInstant() }.getOrNull() }).take(3)
}

internal fun practiceCopy(context: JournalPracticeContext): PracticeCopy {
    val day = (context.today as? JournalLoad.Ready)?.value
    if (day == null) return PracticeCopy("내 생활에 맞는 실천 찾기", "오늘의 카드 상태는 홈에서 확인할 수 있어요.",
        "오늘의 카드 정보를 아직 확인하지 못해, 새 미션을 제안하지 않았어요.", "홈에서 카드 확인하기")
    if (!day.window.is_rest_day && day.challengeState == "COMPLETED") return completedPracticeCopy(day)
    if (!day.window.is_rest_day && !day.window.is_given_up && day.challengeState == null && day.window.draw_state == "AWAITING_SELECTION")
        return PracticeCopy("오늘의 카드부터 한 장", "오늘 하루에 들어갈 만한 카드 한 장을 골라볼까요? 틈새운동은 오늘의 카드를 완료한 뒤에 살펴볼 수 있어요.",
            "아직 오늘 카드를 고르기 전이라, 하루 카드 선택을 먼저 안내했어요.", "오늘의 카드로 가기")
    return when {
        day.window.is_rest_day -> PracticeCopy("오늘은 쉬어가는 리듬으로",
            "오늘은 쉬어가기로 했어요. 다음에 편하게 시작할 작은 실천을 떠올려봐요.",
            "오늘의 쉬어가기 설정을 보고, 추가 운동을 권하지 않았어요.", "홈으로 돌아가기")
        day.window.is_given_up || day.challengeState == "SKIPPED" -> PracticeCopy("오늘은 여기까지도 괜찮아요",
            "오늘 카드는 접어두었어요. 다음에는 내 하루에 더 편하게 들어갈 실천을 찾아봐요.",
            "오늘 카드를 접은 상태를 보고 안내했어요. 틈새운동은 하루 카드 완료 후에 열려요.", "홈으로 돌아가기")
        day.challengeState == "ACTIVE" -> PracticeCopy("진행 중인 한 장을 이어가요",
            "오늘 시작한 카드가 있어요. 할 수 있는 만큼 이어가고, 필요하면 잠시 쉬어가도 괜찮아요.",
            "하루 카드가 진행 중인 상태를 보고 안내했어요.", "진행 중인 카드 보기")
        day.challengeState == "PAUSED" -> PracticeCopy("잠깐 멈춰둔 한 장",
            "다시 할 수 있는 틈이 생기면 고른 카드를 확인해봐요. 오늘은 어렵다면 쉬어가도 괜찮아요.",
            "하루 카드가 일시정지 상태라, 기존 카드로 안내했어요.", "홈에서 이어서 보기")
        day.window.draw_state == "SELECTED" && day.challengeState in listOf(null, "READY") ->
            PracticeCopy("오늘 고른 실천은 언제가 편할까요?",
                "이미 고른 카드가 있어요. 오늘 하루 중 시작하기 편한 순간을 정해봐요.",
                "오늘 카드가 선택된 상태라, 새 카드를 다시 고르도록 안내하지 않았어요.", "오늘의 카드로 가기")
        else -> PracticeCopy("내 생활에 맞는 실천 찾기", "오늘의 카드 상태는 홈에서 확인할 수 있어요.",
            "오늘의 카드 상태를 확인한 뒤 안내할 수 있어요.", "홈에서 카드 확인하기")
    }
}

/** 서버 응답을 확인합니다. 활동 평균이나 기록 개수로 남은 횟수를 추정하지 않습니다. */
internal fun verifiedExtraAvailability(day: JournalToday): ExerciseMissionsTodayResponse? {
    if (day.window.is_rest_day || day.challengeState != "COMPLETED") return null
    val extra = (day.extras as? JournalLoad.Ready)?.value ?: return null
    return extra.takeIf { it.card_completed && it.limit == 2 && it.used in 0..2 && it.remaining == 2 - it.used }
}

internal fun selectableExtraOptions(day: JournalToday): List<ExerciseMissionOption> =
    verifiedExtraAvailability(day)?.takeIf { it.remaining > 0 }?.options.orEmpty().filter {
        !it.already_completed_today && it.five_element in listOf("WOOD", "FIRE") && it.title.isNotBlank()
    }.distinctBy { it.catalog_entry_id }

private fun completedPracticeCopy(day: JournalToday): PracticeCopy {
    val extra = verifiedExtraAvailability(day) ?: return PracticeCopy("오늘의 카드를 해냈어요",
        "오늘 시작하기 편했던 순간을 기억해볼까요? 틈새운동의 현재 상태는 홈에서 확인할 수 있어요.",
        "오늘 카드 완료는 확인했어요. 틈새운동의 남은 횟수는 아직 확인하지 못해 숫자를 안내하지 않았어요.", "홈에서 오늘 기록 보기")
    if (extra.remaining == 0) return PracticeCopy("오늘의 실천을 차곡차곡 남겼어요",
        "오늘의 카드와 틈새운동 2개를 완료했어요. 오늘은 해낸 기록을 돌아보며 마무리해요.",
        "서버에서 하루 카드 완료와 틈새운동 완료 2개를 확인했어요.", "홈에서 오늘 기록 보기")
    if (selectableExtraOptions(day).isEmpty()) return PracticeCopy("오늘의 카드를 해냈어요",
        "지금은 새로 고를 수 있는 틈새운동이 없어요. 해낸 실천을 돌아보며 쉬어가도 괜찮아요.",
        "서버의 현재 운동 목록에서 오늘 아직 완료하지 않은 선택지를 확인했어요.", "홈에서 오늘 기록 보기")
    return PracticeCopy(if (extra.used == 0) "오늘의 카드, 그다음은 내 선택" else "틈새운동도 한 번 해냈어요",
        "조금 더 움직이고 싶다면 틈새운동을 살펴봐요. 오늘은 최대 ${extra.remaining}개 더 완료할 수 있어요. 여기서 쉬어가도 괜찮아요.",
        "하루 카드 완료와 서버가 알려준 남은 횟수 ${extra.remaining}개를 확인했어요. 같은 운동은 오늘 다시 완료할 수 없어요.",
        "홈에서 틈새운동 확인하기")
}

internal fun verifiedActivityHint(card: PersonalActivityCard): PersonalActivityHint? {
    val hint = card.coaching_hint ?: return null
    val value = card.value ?: return null
    return hint.takeIf {
        value.isFinite() && value >= 0 && hint.version == "tmtn-activity-hint-v1" &&
            hint.basis == (if (value == 0.0) "survey_zero" else "survey_reported") &&
            listOf(hint.title, hint.text, hint.reason).none { it.isNullOrBlank() }
    }
}

@Composable
internal fun JournalActivityHint(card: PersonalActivityCard, context: JournalPracticeContext?) {
    val hint = verifiedActivityHint(card) ?: return
    val colors = LocalTmtnColors.current
    val day = (context?.today as? JournalLoad.Ready)?.value
    val resting = day?.window?.is_rest_day == true
    val completed = day?.challengeState == "COMPLETED"
    // 완료·휴식 안내는 아래의 상태 카드 한 곳에서 보여줍니다.
    if (resting || completed) return
    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(12.dp)).padding(14.dp)
        .testTag("activity-hint-${card.key}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(hint.title.orEmpty(), style = TmtnType.label, color = colors.onSurface)
        Text(hint.text.orEmpty(), style = TmtnType.body, color = colors.onSurface)

    }
}

@Composable
internal fun JournalPracticeCoach(context: JournalPracticeContext, onGo: (() -> Unit)?) {
    val colors = LocalTmtnColors.current
    val records = recentPractice(context)
    val copy = practiceCopy(context)
    var recordsOpen by remember(records) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("activity-practice-coach"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(copy.title, style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })
        if (records.isNotEmpty()) {
            val latest = records.first()
            Text("${latest.date.format(DateTimeFormatter.ofPattern("yyyy.M.d"))} · ‘${latest.title}’ 미션을 완료했어요.", style = TmtnType.body, color = colors.onSurface)
        }
        Text(copy.text, style = TmtnType.body, color = colors.onSurface)
        if (records.isNotEmpty()) TextButton(onClick = { recordsOpen = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("practice-open-records")) {
            Text("해낸 미션 돌아보기", style = TmtnType.label)
        }
        if (context.collection == JournalLoad.Failed || context.exercises == JournalLoad.Failed)
            Text("완료 기록을 모두 불러오지 못했어요. 기록란에서 다시 불러올 수 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        else if (context.collection == JournalLoad.Loading || context.exercises == JournalLoad.Loading)
            Text("해낸 미션을 불러오고 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        if (onGo != null) Button(onClick = onGo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("practice-go-home")) {
            Text(copy.action, style = TmtnType.label)
        }
    }
    if (recordsOpen) AlertDialog(onDismissRequest = { recordsOpen = false }, title = { Text("최근에 해낸 미션", style = TmtnType.title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            records.forEach { Text("${it.title}\n${it.date.format(DateTimeFormatter.ofPattern("yyyy.M.d"))} 완료", style = TmtnType.body) }
            Text("최근 7일의 완료 기록에서 가져왔어요. 다음에도 편하게 시작할 수 있는 순간을 떠올려봐요.", style = TmtnType.caption)
        } }, confirmButton = { TextButton(onClick = { recordsOpen = false }) { Text("닫기") } })
}
