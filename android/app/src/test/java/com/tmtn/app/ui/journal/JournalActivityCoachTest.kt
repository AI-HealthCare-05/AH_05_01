package com.tmtn.app.ui.journal

import com.tmtn.app.network.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class JournalActivityCoachTest {
    private fun option(done: Boolean = false, element: String = "WOOD") = ExerciseMissionOption(UUID(0, 1), "천천히 걷기", "", "CHECK", 10, "걸음", element, "나뭇가지", done)
    private fun day(state: String? = "COMPLETED", used: Int = 0, rest: Boolean = false): JournalToday = JournalToday(
        CardWindowResponse(if (state == null) "AWAITING_SELECTION" else "SELECTED", "2026-09-16", "set", emptyList(), null, null, state, rest), null,
        JournalLoad.Ready(ExerciseMissionsTodayResponse(true, used, 2, 2 - used, listOf(option()))))
    private fun context(day: JournalToday, records: List<CardHistoryItem> = emptyList()) =
        JournalPracticeContext(JournalLoad.Ready(day), JournalLoad.Ready(records), fallbackDate = LocalDate.of(2026, 9, 16))
    private fun history(title: String, stamp: String) = CardHistoryItem(title, "WOOD", "나뭇가지", "유산소", stamp)

    @Test fun oneCardComesBeforeOptionalExtras() {
        for (state in listOf(null, "READY", "ACTIVE", "PAUSED", "SKIPPED")) {
            val value = day(state)
            assertNull(verifiedExtraAvailability(value))
            assertTrue(selectableExtraOptions(value).isEmpty())
            assertFalse(practiceCopy(context(value)).action.contains("틈새운동"))
        }
        assertTrue(practiceCopy(context(day(null))).text.contains("카드 한 장"))
    }

    @Test fun extraAllowanceComesFromServerAndStopsAfterTwo() {
        for (used in 0..1) {
            val copy = practiceCopy(context(day(used = used)))
            assertTrue(copy.text.contains("${2 - used}개"))
            assertTrue(copy.text.contains("쉬어가도"))
            assertEquals("홈에서 틈새운동 확인하기", copy.action)
        }
        val finished = day(used = 2)
        assertTrue(selectableExtraOptions(finished).isEmpty())
        assertTrue(practiceCopy(context(finished)).text.contains("틈새운동 2개를 완료"))
        assertEquals("홈에서 오늘 기록 보기", practiceCopy(context(finished)).action)
    }

    @Test fun restingDayNeverOffersAdditionalExercise() {
        val value = day(rest = true)
        assertNull(verifiedExtraAvailability(value))
        assertEquals("홈으로 돌아가기", practiceCopy(context(value)).action)
        assertTrue(practiceCopy(context(value)).reason.contains("쉬어가기"))
    }

    @Test fun unknownOrContradictoryAllowanceNeverBecomesTwoRemaining() {
        val original = day()
        val valid = (original.extras as JournalLoad.Ready).value
        listOf(original.copy(extras = null), original.copy(extras = JournalLoad.Failed), original.copy(extras = JournalLoad.Loading),
            original.copy(extras = JournalLoad.Ready(valid.copy(card_completed = false))),
            original.copy(extras = JournalLoad.Ready(valid.copy(remaining = 1))),
            original.copy(extras = JournalLoad.Ready(valid.copy(limit = 3, remaining = 3)))).forEach {
            assertNull(verifiedExtraAvailability(it))
            assertFalse(practiceCopy(context(it)).text.contains("2개"))
            assertEquals("홈에서 오늘 기록 보기", practiceCopy(context(it)).action)
        }
    }

    @Test fun completedOrNonExerciseOptionsAreNotOfferedAgain() {
        val initial = day()
        val state = (initial.extras as JournalLoad.Ready).value.copy(options = listOf(option(true), option(element = "WATER")))
        val filtered = initial.copy(extras = JournalLoad.Ready(state))
        assertTrue(selectableExtraOptions(filtered).isEmpty())
        assertFalse(practiceCopy(context(filtered)).action.contains("틈새운동"))
    }

    @Test fun onlyRecentValidCompletionsCanBeCelebrated() {
        val records = listOf(history("최근", "2026-09-15T15:30:00Z"), history("미래", "2026-09-17"),
            history("오래된 기록", "2026-09-09"), history("잘못된 시각", "2026-09-16garbage"), history("", "2026-09-16"))
        assertEquals(listOf("최근"), recentPractice(context(day(), records)).map { it.title })
        assertEquals(LocalDate.of(2026, 9, 16), recentPractice(context(day(), records)).single().date)
    }

    @Test fun failedHistoryAndMissingDayDoNotInventCompletionsOrAvailability() {
        val context = JournalPracticeContext(JournalLoad.Failed, JournalLoad.Failed, fallbackDate = LocalDate.of(2026, 9, 16))
        assertTrue(recentPractice(context).isEmpty())
        assertEquals("홈에서 카드 확인하기", practiceCopy(context).action)
        assertFalse(practiceCopy(context).text.contains("완료"))
    }

    @Test fun hintCannotUseTheOtherSurveyState() {
        val hint = PersonalActivityHint("tmtn-activity-hint-v1", "survey_reported", "힌트", "이어가 봐요.", "설문 기준이에요.")
        val card = PersonalActivityCard(value = 0.0, coaching_hint = hint)
        assertNull(verifiedActivityHint(card))
        assertNotNull(verifiedActivityHint(card.copy(coaching_hint = hint.copy(basis = "survey_zero"))))
        assertNull(verifiedActivityHint(card.copy(value = Double.NaN)))
    }
}
