package com.tmtn.app.network.model

/** Existing exercise-mission-records contract. It does not include measured minutes or model evidence. */
data class ExerciseMissionRecordItem(
    val service_date: String,
    val title: String,
    val five_element: String,
    val material_name: String,
    val reward_slot: Int,
    val completed_at: String?,
)

data class ExerciseMissionRecordsResponse(val records: List<ExerciseMissionRecordItem>)
