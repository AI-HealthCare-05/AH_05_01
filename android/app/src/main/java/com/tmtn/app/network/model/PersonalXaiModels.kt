package com.tmtn.app.network.model

data class PersonalXaiResponse(
    val status: String? = null,
    val reason: String? = null,
    val snapshot: PersonalXaiSnapshot? = null,
    val retry_after_seconds: Int? = null,
    val activity_comparison: PersonalActivityComparison? = null,
)

data class PersonalXaiSnapshot(
    val schema_version: String? = null,
    val snapshot_id: String? = null,
    val input_revision: String? = null,
    val computed_at: String? = null,
    val reference_date: String? = null,
    val release_sha256: String? = null,
    val model_version: String? = null,
    val reference_version: String? = null,
    val formula_version: String? = null,
    val input_mapping_version: String? = null,
    val display_policy_version: String? = null,
    val age_notice: String? = null,
    val release_stage: String? = null,
    val is_mock: Boolean? = null,
    val status: String? = null,
    val composite_score: Double? = null,
    val domains: List<PersonalXaiDomain>? = null,
    val activity_comparison: PersonalActivityComparison? = null,
)

data class PersonalActivityComparison(
    val status: String? = null,
    val reference_version: String? = null,
    val source_sha256: String? = null,
    val input_revision: String? = null,
    val reference_date: String? = null,
    val group_key: String? = null,
    val group_label: String? = null,
    val source_kind: String? = null,
    val years: List<Int>? = null,
    val source_label: String? = null,
    val source_url: String? = null,
    val scope_note: String? = null,
    val method_note: String? = null,
    val cards: List<PersonalActivityCard>? = null,
    val survey_recorded_at: String? = null,
)

data class PersonalActivityCard(
    val key: String? = null,
    val label: String? = null,
    val unit: String? = null,
    val n: Int? = null,
    val mean: Double? = null,
    val value: Double? = null,
    val mean_display: String? = null,
    val value_display: String? = null,
    val delta: Double? = null,
    val title: String? = null,
    val text: String? = null,
    val comparison_text: String? = null,
    val unit_note: String? = null,
    val topcoded: Boolean? = null,
    val coaching_hint: PersonalActivityHint? = null,
)

data class PersonalActivityHint(
    val version: String? = null,
    val basis: String? = null,
    val title: String? = null,
    val text: String? = null,
    val reason: String? = null,
)

data class PersonalXaiDomain(
    val domain: String? = null,
    val rank: Int? = null,
    val rank_range: List<Int>? = null,
    val reference_group: String? = null,
    val reference_n: Int? = null,
    val output_target: String? = null,
    val unit: String? = null,
    val base_value: Double? = null,
    val output_value: Double? = null,
    val feature_order: List<String>? = null,
    val contributions: List<PersonalShapContribution>? = null,
    val background_sha256: String? = null,
    val background_rows: Int? = null,
    val explainer: String? = null,
    val masker: String? = null,
    val link: String? = null,
    val shap_version: String? = null,
    val additivity_error: Double? = null,
    val narrative: PersonalShapNarrative? = null,
)

data class PersonalShapNarrative(
    val version: String? = null,
    val domain: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val intro_kind: String? = null,
    val calculation_title: String? = null,
    val calculation_summary: String? = null,
    val context_note: String? = null,
    val focus_keys: List<String>? = null,
    val activity_text: String? = null,
    val activity_keys: List<String>? = null,
    val comparison_text: String? = null,
    val comparison_kind: String? = null,
    val comparison_keys: List<String>? = null,
    val scope_note: String? = null,
)

data class PersonalShapContribution(val key: String? = null, val label: String? = null, val value: Double? = null)
