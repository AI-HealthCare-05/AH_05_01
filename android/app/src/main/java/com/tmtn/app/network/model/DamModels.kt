package com.tmtn.app.network.model

// ===== G05/G06 공유 항목 =====
data class CardHistoryItem(
    val title: String,
    val five_element: String,
    val material_name: String,
    val domain_label: String,
    val completed_at: String
)

// ===== G05: 재료별 기록 상세 =====
data class MaterialHistoryResponse(
    val element: String,
    val material_name: String,
    val domain_label: String,
    val count: Int,
    val recent_history: List<CardHistoryItem>
)

// ===== G06: 카드첩 =====
data class CardCollectionResponse(
    val total_count: Int,
    val cards: List<CardHistoryItem>
)

// ===== G07: 단계 상승 축하 =====
data class StageUpPendingResponse(
    val previous_stage: Int,
    val new_stage: Int,
    val new_stage_label: String,
    val materials_gained_this_stage: Int,
    val days_practiced_this_stage: Int,
    val days_rested_this_stage: Int,
    val top_material_name: String?
)
