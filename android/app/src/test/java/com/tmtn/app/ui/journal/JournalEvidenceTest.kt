package com.tmtn.app.ui.journal

import com.google.gson.Gson
import com.tmtn.app.network.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JournalEvidenceTest {
    private fun personal(): PersonalXaiSnapshot = Gson().fromJson(File("src/debug/assets/journal_review/personal-base.json").readText(), PersonalXaiSnapshot::class.java)
    private fun valid(snapshot: PersonalXaiSnapshot) = verifiedPersonalSnapshot(JournalLoad.Ready(PersonalXaiResponse("ready", snapshot = snapshot)))

    @Test fun activityComparisonRequiresSameInputAndVerifiedSurveyUnits() {
        val snapshot = personal()
        val activity = verifiedActivityComparison(snapshot)!!
        assertEquals("19-39:1", activity.group_key)
        assertEquals(2087, activity.cards!!.first().n)
        assertEquals("155", activity.cards.first().mean_display)
        assertNull(verifiedActivityComparison(snapshot.copy(activity_comparison = activity.copy(input_revision = "another-user"))))
        assertNull(verifiedActivityComparison(snapshot.copy(activity_comparison = activity.copy(group_key = "65+:2"))))
        assertNull(verifiedActivityComparison(snapshot.copy(activity_comparison = activity.copy(source_sha256 = "changed"))))
        val altered = activity.cards.first().copy(unit = "일")
        assertNull(verifiedActivityComparison(snapshot.copy(activity_comparison = activity.copy(cards = listOf(altered)))))
        assertNotNull(valid(snapshot.copy(activity_comparison = null)))
        val strength = activity.cards[1]
        assertEquals("주 4일", activityAmount(strength, true))
        assertEquals("주 5일 이상", activityAmount(strength.copy(topcoded = true, value = 5.0, value_display = "5+"), true))
    }

    @Test fun personalSnapshotRequiresAllContributionsAndCorrectUnits() {
        val snapshot = personal()
        assertNotNull(valid(snapshot))
        assertNull(valid(snapshot.copy(is_mock = true)))
        assertNull(valid(snapshot.copy(release_sha256 = "wrong")))
        val domain = snapshot.domains!![0]
        val second = snapshot.domains[1]
        assertNull(valid(snapshot.copy(domains = listOf(domain.copy(unit = "rank"), second))))
        assertNull(valid(snapshot.copy(domains = listOf(domain.copy(contributions = domain.contributions!!.drop(1)), second))))
        assertNull(valid(snapshot.copy(domains = listOf(domain.copy(output_value = 10.0), second))))
    }

    @Test fun productionKnowledgeRejectsDraftsAndUnsafeUrls() {
        val response = Gson().fromJson(File("src/debug/assets/journal_review/knowledge.json").readText(), JournalEditorialResponse::class.java)
        assertTrue(journalArticles(response, null).isEmpty())
        assertEquals(40, journalArticles(response, null, allowPreview = true).size)
        val movement = response.sections!!.first { it.section == "movement_column" }
        val retired = movement.articles!!.first().copy(id = "movement.equivalence")
        val oldResponse = response.copy(sections = listOf(movement.copy(articles = listOf(retired))))
        assertTrue(journalArticles(oldResponse, null, allowPreview = true).isEmpty())
        listOf("http://health.kdca.go.kr", "https://health.kdca.go.kr.evil.test", "https://x@health.kdca.go.kr", "javascript:alert(1)").forEach {
            assertFalse(journalSourceUrlAllowed(it))
        }
        assertTrue(journalSourceUrlAllowed("https://health.kdca.go.kr/healthinfo/"))
    }
}
