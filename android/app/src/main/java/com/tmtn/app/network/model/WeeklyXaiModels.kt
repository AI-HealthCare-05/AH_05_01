package com.tmtn.app.network.model

data class WeeklyXaiHistoryResponse(
    val schema_version: String? = null,
    val status: String? = null,
    val reason: String? = null,
    val points: List<WeeklyReferencePoint>? = null,
    val practice: List<WeeklyPracticePoint>? = null,
)

data class WeeklyReferencePoint(
    val week_start: String? = null,
    val week_end: String? = null,
    val observed_on: String? = null,
    val status: String? = null,
    val diabetes: Double? = null,
    val hypertension: Double? = null,
    val comparison_key: String? = null,
)

data class WeeklyPracticePoint(
    val week_start: String? = null,
    val recorded: Boolean? = null,
    val completed_days: Int? = null,
    val rest_days: Int? = null,
    val elapsed_days: Int? = null,
)
