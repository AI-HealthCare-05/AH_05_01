package com.tmtn.app.network.model

/** Nullable at the network boundary: malformed/older responses must fail closed. */
data class JournalEditorialResponse(
    val schema_version: String? = null,
    val service_date: String? = null,
    val preview: Boolean? = null,
    val sections: List<JournalReadingSection>? = null,
    val related_readings: Map<String, List<JournalKnowledgeArticle>>? = null,
)

data class JournalReadingSection(
    val section: String? = null,
    val status: String? = null,
    val articles: List<JournalKnowledgeArticle>? = null,
)

data class JournalKnowledgeArticle(
    val id: String? = null,
    val section: String? = null,
    val title: String? = null,
    val text: String? = null,
    val revision: String? = null,
    val content_sha256: String? = null,
    val numeric_slot: Boolean? = null,
    val source: JournalKnowledgeSource? = null,
    val topic: String? = null,
    val allowed_claim_scope: String? = null,
    val audience_min_age: Int? = null,
    val audience_max_age: Int? = null,
)

data class JournalKnowledgeSource(
    val id: String? = null,
    val publisher: String? = null,
    val title: String? = null,
    val url: String? = null,
    val edition: String? = null,
    val locator: String? = null,
    val checked_on: String? = null,
)
