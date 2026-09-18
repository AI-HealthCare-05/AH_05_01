package com.tmtn.app.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.Response

sealed interface JournalLoad<out T> {
    data object Loading : JournalLoad<Nothing>
    data object Failed : JournalLoad<Nothing>
    data class Ready<T>(val value: T) : JournalLoad<T>
}

data class JournalToday(val window: CardWindowResponse, val card: CardRevealResponse?)

internal val JournalToday.challengeState: String?
    get() = card?.state ?: window.challenge_state

/** Uses the existing read endpoints only. Health results never enter local preferences. */
class JournalState {
    var requestedEdition by mutableStateOf<Int?>(null)
    var weekly by mutableStateOf<JournalLoad<WeeklyReportResponse>>(JournalLoad.Loading)
        private set
    var collection by mutableStateOf<JournalLoad<List<CardHistoryItem>>>(JournalLoad.Loading)
        private set
    var today by mutableStateOf<JournalLoad<JournalToday>>(JournalLoad.Loading)
        private set
    var exercises by mutableStateOf<JournalLoad<List<ExerciseMissionRecordItem>>?>(null)
        private set
    // ⚠️ 2026-09-18 추가(UI/UX 핸드오프 E03 "초기 습관의 반영 안내") - 가입 설문
    // 초기 습관이 지수 계산에 반영됐는지 보여주기 위함. 계산 성공 여부(composite_score)와
    // 종합 산식 버전(policy_version)을 그대로 노출 - 이 화면에서 산식·배점을 새로
    // 정하지 않는다.
    var practiceScore by mutableStateOf<JournalLoad<PracticeScoreResponse>>(JournalLoad.Loading)
        private set
    var refreshing by mutableStateOf(false)
        private set

    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        try {
            coroutineScope {
                launch {
                    weekly = read { ApiClient.recordApi.getWeeklyReport() }
                    val report = (weekly as? JournalLoad.Ready)?.value
                    exercises = if (report == null) null else {
                        exercises = JournalLoad.Loading
                        readExerciseRecords(report)
                    }
                }
                launch {
                    collection = when (val result = read { ApiClient.cardHomeApi.getCardCollection() }) {
                        is JournalLoad.Ready -> JournalLoad.Ready(result.value.cards)
                        else -> JournalLoad.Failed
                    }
                }
                launch {
                    practiceScore = read { ApiClient.practiceScoreApi.getPracticeScore() }
                }
                launch {
                    today = try {
                        val response = ApiClient.cardHomeApi.getTodayCards()
                        val window = response.body().takeIf { response.isSuccessful }
                        if (window == null) JournalLoad.Failed else {
                            val card = window.challenge_id?.let { id ->
                                val reveal = ApiClient.cardHomeApi.revealChallenge(id)
                                reveal.body().takeIf { reveal.isSuccessful }
                            }
                            JournalLoad.Ready(JournalToday(window, card))
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { JournalLoad.Failed }
                }
            }
        } finally { refreshing = false }
    }

    private suspend fun readExerciseRecords(report: WeeklyReportResponse): JournalLoad<List<ExerciseMissionRecordItem>>? = try {
        val response = ApiClient.recordApi.getExerciseMissionRecords(report.start_date, report.end_date)
        // Older server builds do not expose this optional read endpoint.
        if (response.code() == 404 || response.code() == 501) null
        else response.body()?.takeIf { response.isSuccessful }?.let {
            JournalLoad.Ready(issueExerciseRecords(it.records, report))
        } ?: JournalLoad.Failed
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { JournalLoad.Failed }
}

private suspend fun <T> read(request: suspend () -> Response<T>): JournalLoad<T> = try {
    val response = request()
    val body = response.body()
    if (response.isSuccessful && body != null) JournalLoad.Ready(body) else JournalLoad.Failed
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { JournalLoad.Failed }

internal val JournalZone: ZoneId = ZoneId.of("Asia/Seoul")

/** A completion is attributed to its Korean service date, not the phone's current timezone. */
internal fun completionDate(raw: String): LocalDate? =
    runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(JournalZone).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()

internal fun issueCards(cards: List<CardHistoryItem>, report: WeeklyReportResponse?): List<CardHistoryItem> {
    val start = report?.start_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
    val end = runCatching { LocalDate.parse(report.end_date) }.getOrNull() ?: return emptyList()
    return cards.filter { card -> completionDate(card.completed_at)?.let { it >= start && it <= end } == true }
        .sortedByDescending { it.completed_at }
}

/** Reward slots are unique per service day. They never increase the card completion-day total. */
internal fun issueExerciseRecords(records: List<ExerciseMissionRecordItem>, report: WeeklyReportResponse?): List<ExerciseMissionRecordItem> {
    val start = report?.start_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
    val end = runCatching { LocalDate.parse(report.end_date) }.getOrNull() ?: return emptyList()
    return records.filter { item ->
        val date = runCatching { LocalDate.parse(item.service_date) }.getOrNull()
        date != null && date >= start && date <= end && item.reward_slot > 0 && item.title.isNotBlank() &&
            item.completed_at?.let { completionDate(it) } != null
    }.sortedByDescending { it.completed_at }.distinctBy { it.service_date to it.reward_slot }
}

data class JournalLead(val title: String, val body: String)

internal data class DailyEditorial(
    val title: String,
    val sentence: String,
    val articleTitle: String,
    val articleBody: String,
    val action: String,
)

/** Copy follows the recorded day state, including rest and give-up; it never promises a health effect. */
internal fun dailyEditorial(today: JournalLoad<JournalToday>): DailyEditorial {
    val day = (today as? JournalLoad.Ready)?.value
    return when {
        day?.window?.is_rest_day == true -> DailyEditorial(
            "오늘은 잠깐,\n쉬어가는 날.", "오늘은 쉬어도 괜찮아.\n모아둔 재료는 여기 있을게.",
            "쉬는 날에는,\n다음 한 장을 남겨둬요.",
            "오늘의 실천을 내일 두 배로 채우지 않아도 돼요. 다시 시작할 때는 그날 할 수 있는 카드 한 장이면 됩니다.", "홈으로 돌아가기")
        day?.challengeState == "COMPLETED" -> DailyEditorial(
            "오늘의 한 장,\n내 기록에 남았어요.", "해낸 한 장을 펼쳐보니,\n오늘 쓸 재료가 생겼네!",
            "오늘 잘 맞았던 순간을\n기억해둘까요?",
            "시작하기 편했던 시간이나 장소가 있었나요? 다음에도 해보고 싶은 방법을 하루 기록에 짧게 남겨보세요.", "홈에서 오늘 기록 보기")
        day?.window?.is_given_up == true || day?.challengeState == "SKIPPED" -> DailyEditorial(
            "다음 한 장은,\n다시 고르면 돼요.", "오늘은 여기까지.\n다음 카드에서 다시 만나자.",
            "맞지 않았던 카드도\n나를 알아가는 기록.",
            "시간이 부족했는지, 동작이 어려웠는지 잠깐 돌아보세요. 다음에는 내 하루에 더 편하게 들어갈 실천을 골라봐요.", "홈으로 돌아가기")
        day?.challengeState == "PAUSED" -> DailyEditorial(
            "잠깐 멈춰둔 한 장,\n다시 펼쳐도 괜찮아요.", "잠깐 쉬는 사이에도,\n네 카드는 여기 있어.",
            "다시 할 수 있는\n틈이 생겼나요?",
            "멈춰둔 미션은 홈에서 이어갈 수 있어요. 오늘은 어렵다면 쉬어가기를 선택해도 괜찮아요.", "홈에서 이어서 보기")
        day?.challengeState == "ACTIVE" -> DailyEditorial(
            "오늘의 한 장을\n채워가는 중이에요.", "오늘 고른 작은 행동,\n한 걸음씩 해보자.",
            "시작한 실천을\n마저 이어가볼까요?",
            "홈으로 돌아가면 진행 중인 미션을 확인할 수 있어요. 오늘 고른 실천을 할 수 있는 만큼 이어가보세요.", "진행 중인 카드 보기")
        day?.card != null || day?.window?.draw_state == "SELECTED" -> DailyEditorial(
            "내가 고른 한 장이\n오늘을 기다려요.", "카드는 골랐으니,\n시작할 때만 정해볼까?",
            "오늘 고른 실천은,\n언제 해보면 좋을까요?",
            "일을 마친 뒤, 식사를 마친 뒤, 잠들기 전. 오늘의 카드가 들어갈 자리를 생각해보세요. 늘 하던 일 뒤에 붙여두면 시작할 때를 기억하기 쉬워요.", "오늘의 카드로 가기")
        else -> DailyEditorial(
            "오늘의 틈에,\n작은 한 장.", "오늘은 어떤 한 장이\n네 하루에 잘 맞을까?",
            "시작할 때를\n한 번 정해봐요.",
            "카드를 고르기 전에 오늘 쓸 수 있는 시간을 떠올려보세요. 잠깐의 산책도, 잠자리 준비도 한 장의 실천이 될 수 있어요.", "오늘의 카드로 가기")
    }
}

/** Headlines depend on verified completion records; never on inferred medical benefit. */
internal fun weeklyLead(report: WeeklyReportResponse?, cards: List<CardHistoryItem>): JournalLead {
    if (report == null) return JournalLead("작은 실천이\n소식이 되는 곳.", "해낸 카드도, 잠깐 쉬어간 하루도. 여기서 한 주의 이야기를 함께 펼쳐봐요.")
    val count = report.completed_count.takeIf { it in 0..report.total_days } ?: 0
    val rest = report.days.count { it.status == "REST" }
    return when {
        count > 0 -> JournalLead("${count}일의 실천,\n이번 주에 남았어요.",
            cards.firstOrNull()?.let { "‘${it.title}’도 해냈어요. 바쁜 하루 사이에 직접 해낸 카드들을 모아봤습니다. 다음에도 꺼내고 싶은 실천이 있나요?" }
                ?: "하루에 한 장씩, 해낸 날들이 나란히 남았어요. 이번 주에는 어떤 실천이 가장 편했나요?")
        rest > 0 -> JournalLead("쉬어간 자리에도,\n다음 이야기가 있어요.", "이번 주에는 ${rest}일 쉬어갔어요. 이미 모은 재료는 그대로 있으니, 다시 할 수 있는 날 한 장을 꺼내봐요.")
        else -> JournalLead("첫 소식은,\n작은 한 장부터.", "아직 이번 주에 해낸 카드가 없어요. 오늘 내 하루에 들어갈 만한 카드 한 장을 골라볼까요?")
    }
}
