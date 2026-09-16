package com.tmtn.app.ui.journal

import com.google.gson.Gson
import com.tmtn.app.network.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JournalDomainStoryTest {
    private fun snapshot() = Gson().fromJson(File("src/debug/assets/journal_review/personal-base.json").readText(), PersonalXaiSnapshot::class.java)
    private fun editorial() = Gson().fromJson(File("src/debug/assets/journal_review/knowledge.json").readText(), JournalEditorialResponse::class.java)

    @Test fun negligibleBarsAndNarrativeUseTheSameBoundary() {
        listOf(0.0, 5e-9, -5e-9, 1e-8, -1e-8).forEach {
            assertEquals("표시 기준에서 차이 없음", personalDirectionLabel(it))
        }
        assertEquals("높이는 쪽", personalDirectionLabel(2e-8))
        assertEquals("낮추는 쪽", personalDirectionLabel(-2e-8))
    }

    @Test fun domainsUseTheirOwnExplanationAndActualNextContributor() {
        val domains = snapshot().domains!!
        val diabetes = verifiedPersonalNarrative(domains[0])!!
        val pressure = verifiedPersonalNarrative(domains[1])!!
        assertTrue(diabetes.title!!.contains("당뇨"))
        assertTrue(pressure.title!!.contains("혈압"))
        assertNotEquals(diabetes.title, pressure.title)
        assertEquals(listOf("age_years"), diabetes.focus_keys)
        assertEquals(setOf("sex_code", "weight_kg"), diabetes.comparison_keys!!.toSet())
        assertEquals("different_next_tier", diabetes.comparison_kind)
        listOf(diabetes, pressure).forEach {
            assertEquals("challenge_encouragement", it.intro_kind)
            assertFalse(it.summary!!.contains("나이"))
            assertFalse(it.summary.contains("성별"))
            assertTrue(it.summary.contains("미션"))
            assertTrue(it.calculation_summary!!.contains("나이"))
            assertTrue(it.context_note!!.contains("바꿔야 할 목표"))
        }
    }

    @Test fun unknownVersionWrongDomainAndStaleLeaderAreHidden() {
        val domain = snapshot().domains!![0]
        val narrative = domain.narrative!!
        assertNull(verifiedPersonalNarrative(domain.copy(narrative = narrative.copy(domain = "hypertension"))))
        assertNull(verifiedPersonalNarrative(domain.copy(narrative = narrative.copy(version = "unknown"))))
        assertNull(verifiedPersonalNarrative(domain.copy(narrative = narrative.copy(version = "tmtn-shap-story-v1"))))
        assertNull(verifiedPersonalNarrative(domain.copy(narrative = narrative.copy(intro_kind = null))))
        assertNull(verifiedPersonalNarrative(domain.copy(narrative = narrative.copy(calculation_summary = null))))
        assertNull(verifiedPersonalNarrative(domain.copy(narrative = narrative.copy(focus_keys = listOf("weight_kg")))))
        assertNull(verifiedPersonalNarrative(domain.copy(narrative = null)))
    }

    @Test fun relatedReadingsRespectPreviewBoundaryAndSelectedSubject() {
        val data = editorial()
        assertTrue(journalRelatedArticles(data, "diabetes").isEmpty())
        assertTrue(journalRelatedArticles(data, "unknown", true).isEmpty())
        val diabetes = journalRelatedArticles(data, "diabetes", true)
        val pressure = journalRelatedArticles(data, "hypertension", true)
        assertEquals(2, diabetes.size)
        assertEquals(2, pressure.size)
        assertTrue(diabetes.none { it.id in pressure.map { p -> p.id } })
        assertEquals(10, journalArticles(data, "daily_column", true).size)
    }
}
