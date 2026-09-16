package com.tmtn.app.ui.journal

import com.google.gson.Gson
import com.tmtn.app.network.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JournalIssueIntegrationTest {
    private fun editorial() = Gson().fromJson(File("src/debug/assets/journal_review/knowledge.json").readText(), JournalEditorialResponse::class.java)
    private fun snapshot() = Gson().fromJson(File("src/debug/assets/journal_review/personal-base.json").readText(), PersonalXaiSnapshot::class.java)

    @Test fun previewAndLegacyPoolsRespectIssueCountsAndTopics() {
        val source = editorial().copy(service_date = "2026-09-16")
        assertEquals(1, journalIssueArticles(source, false, true).size)
        val weekly = journalIssueArticles(source, true, true)
        assertEquals(2, weekly.size)
        assertEquals(2, weekly.map { it.topic }.distinct().size)
        assertEquals(weekly, journalIssueArticles(source.copy(service_date = "2026-09-20"), true, true))
        assertTrue(journalIssueArticles(source, true).isEmpty())
        assertTrue(journalIssueArticles(source.copy(weekly_articles = weekly, weekly_issue_start = "2026-09-13"), true, true).isEmpty())
    }

    @Test fun shapOnlyAttachesToExactlyBoundDomainResults() {
        val snapshot = snapshot()
        val domain = snapshot.domains!!.first()
        val peer = TuntunScorePeerV2Response(false, "candidate", true, null,
            PeerCompositeDisplay(null, "", "점", null), listOf(PeerComponent("diabetes", "당뇨", true,
                domain.output_value, null, PeerRankDisplay(domain.rank, "", null), null)), 1, true, "", "",
            snapshot.model_version!!, "peer-v2", snapshot.input_revision, snapshot.reference_date)
        assertEquals(domain, boundPersonalDomain(peer, snapshot, "diabetes"))
        assertNull(boundPersonalDomain(peer.copy(explanationInputRevision = "other-input"), snapshot, "diabetes"))
        assertNull(boundPersonalDomain(peer.copy(modelVersion = "other-model"), snapshot, "diabetes"))
        assertNull(boundPersonalDomain(peer.copy(explanationReferenceDate = "2026-09-15"), snapshot, "diabetes"))
        assertNull(boundPersonalDomain(peer.copy(isMock = true), snapshot, "diabetes"))
        assertNull(boundPersonalDomain(peer.copy(components = listOf(peer.components[0].copy(absoluteReferenceScore = 1.0))), snapshot, "diabetes"))
        assertNull(boundPersonalDomain(peer.copy(explanationInputRevision = null), snapshot, "diabetes"))
        assertNull(boundPersonalDomain(peer, snapshot, "physical"))
    }
}
