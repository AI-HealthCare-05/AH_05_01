package com.tmtn.app.network.model

/** 첫 선물은 완료한 운동 카드와 별개이며 서버가 중복 지급을 막는다. */
data class FirstRepairResponse(
    val status: String,
    val gift_element: String,
    val gift_count: Int,
    val companion: CompanionResponse,
)
