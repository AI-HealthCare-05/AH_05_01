package com.tmtn.app.ui.journal

import com.tmtn.app.network.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JournalEditorialTest {
    private fun report(count: Int = 2) = WeeklyReportResponse("2026-09-08", "2026-09-14",
        listOf(CalendarDayItem("2026-09-09", "REST")), count, 7, null, emptyList())
    private fun card(date: String, title: String = "가볍게 걷기") = CardHistoryItem(title, "WOOD", "나뭇가지", "유산소", date)

    @Test fun completionUsesKoreanDateAcrossUtcMidnight() {
        assertEquals(LocalDate.of(2026, 9, 14), completionDate("2026-09-13T16:30:00Z"))
        assertNull(completionDate("not-a-date"))
    }

    @Test fun onlyTheIssuePeriodBecomesAnArticle() {
        val cards = listOf(card("2026-09-07"), card("2026-09-08"), card("2026-09-14"), card("2026-09-15"), card("invalid"))
        assertEquals(listOf("2026-09-14", "2026-09-08"), issueCards(cards, report()).map { it.completed_at })
        assertTrue(issueCards(cards, null).isEmpty())
    }

    @Test fun completedDaysAreNotConfusedWithNumberOfCards() {
        val story = weeklyLead(report(2), listOf(card("2026-09-10")))
        assertTrue(story.title.startsWith("2일"))
        assertTrue(story.body.contains("가볍게 걷기"))
        assertFalse(story.body.contains("좋아졌"))
        assertFalse(story.body.contains("혈당"))
    }

    @Test fun extraExerciseUsesServiceDayAndDoesNotDuplicateRewardSlots() {
        val item = ExerciseMissionRecordItem("2026-09-14", "벽 짚고 밀기", "FIRE", "받침돌", 1, "2026-09-14T16:00:00Z")
        val records = issueExerciseRecords(listOf(item, item, item.copy(reward_slot = 2, title = "의자에서 일어서기"),
            item.copy(service_date = "2026-09-15"), item.copy(completed_at = null), item.copy(reward_slot = 0)), report())
        assertEquals(2, records.size)
        assertTrue(records.all { it.service_date == "2026-09-14" })
        assertTrue(weeklyLead(report(2), emptyList()).title.startsWith("2일"))
    }

    @Test fun incompleteOrInvalidExerciseHistoryCannotBecomeAnArticle() {
        val item = ExerciseMissionRecordItem("bad-date", "벽 짚고 밀기", "FIRE", "받침돌", 1, "2026-09-14T03:00:00Z")
        assertTrue(issueExerciseRecords(listOf(item, item.copy(service_date = "2026-09-14", completed_at = "invalid"),
            item.copy(service_date = "2026-09-14", title = "")), report()).isEmpty())
        assertTrue(issueExerciseRecords(listOf(item), null).isEmpty())
    }

    @Test fun missingHistoryCannotLookLikeFailureOrZeroHealth() {
        assertEquals("작은 실천이\n소식이 되는 곳.", weeklyLead(null, emptyList()).title)
        assertTrue(weeklyLead(report(0), emptyList()).body.contains("1일 쉬어갔어요"))
    }

    @Test fun pausedAndActiveCardsDoNotAskUsersToPickOrStartAgain() {
        val window = CardWindowResponse("SELECTED", "2026-09-14", "set", emptyList(), "option", "card", "PAUSED")
        val paused = dailyEditorial(JournalLoad.Ready(JournalToday(window, null)))
        assertEquals("홈에서 이어서 보기", paused.action)
        assertTrue(paused.title.contains("멈춰둔"))
        val active = dailyEditorial(JournalLoad.Ready(JournalToday(window.copy(challenge_state = "ACTIVE"), null)))
        assertEquals("진행 중인 카드 보기", active.action)
        assertFalse(active.sentence.contains("시작할 때"))
    }

    @Test fun closedDaysNeverInviteStartingTheSameCard() {
        val window = CardWindowResponse("SELECTED", "2026-09-14", "set", emptyList(), "option", "card", "COMPLETED")
        val closed = listOf(window, window.copy(challenge_state = "SKIPPED"), window.copy(challenge_state = "READY", is_rest_day = true),
            window.copy(challenge_state = null, challenge_id = null, is_given_up = true))
        closed.forEach {
            val copy = dailyEditorial(JournalLoad.Ready(JournalToday(it, null)))
            assertNotEquals("오늘의 카드로 가기", copy.action)
            assertFalse(copy.articleTitle.contains("언제 해보면"))
            assertFalse(copy.articleBody.contains("좋아졌"))
        }
        assertEquals("오늘의 카드로 가기", dailyEditorial(JournalLoad.Failed).action)
    }
}
