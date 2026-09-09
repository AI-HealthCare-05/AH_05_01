package com.tmtn.app.network.model

/** ⚠️ 2026-09-09 추가 — 튼튼지수 vNext(또래 백분위) 응답. 기존 TuntunScoreV2Response(Mock,
 * tuntun-score/v2)와 완전히 별개 계약 - 필드명·구조가 다르므로 하나로 합치지 않는다.
 * 서버 응답(수십 개 필드) 중 화면에 실제로 쓰는 것만 매핑한다 - 브릿지 원본 계약 문서
 * (전달 메모)의 "먼저 적용할 화면 연결" 표를 그대로 따름:
 *   - 홈 종합: compositeDisplay.text
 *   - 영역 카드: components[].rankDisplay.text (componentKey로 매핑, 배열 인덱스 아님)
 *   - 상세 참고점수: components[].absoluteReferenceScore
 *   - 부분/미산출: scoreAvailable, availableComponentCount, components[].available
 * 이전 호환용 components[].display(상위 % 문구)는 새 화면에 연결하지 않는다(문서 지침).
 */
data class TuntunScorePeerV2Response(
    val isMock: Boolean,
    val releaseStatus: String,
    val scoreAvailable: Boolean,
    val peerCompositeScore: Double?,
    val compositeDisplay: PeerCompositeDisplay,
    val components: List<PeerComponent>,
    val availableComponentCount: Int,
    val isPartialScore: Boolean,
    val referenceCaution: String,
    val notice: String,
    val modelVersion: String,
    val formulaVersion: String,
)

data class PeerCompositeDisplay(
    val score: Double?,
    val text: String,
    val unit: String,
    val bandLabel: String?, // ⚠️ 항상 null - 기존 "관심/보통/양호" 구간을 여기 붙이지 않는다(문서 지침).
)

data class PeerComponent(
    val componentKey: String, // "physical" | "diabetes" | "hypertension" | "lifestyle"
    val label: String,
    val available: Boolean,
    val absoluteReferenceScore: Double?,
    val rankDisplay: PeerRankDisplay,
    val unavailableReason: String?,
)

data class PeerRankDisplay(
    val rankApprox: Int?,
    val text: String, // 예: "또래 100명 중 약 21등"
    val tieNotice: String?,
)
