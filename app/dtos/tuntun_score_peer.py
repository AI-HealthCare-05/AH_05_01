"""틈튼지수 vNext(또래 백분위) 응답 DTO.

⚠️ 2026-09-09 추가 — LOCAL_REVIEW_CANDIDATE. 기존 /tuntun-score, /tuntun-score/v2와
완전히 별개 경로(/tuntun-score/peer/v2)이며, 기존 두 엔드포인트는 이 작업으로
전혀 건드리지 않는다(계속 Mock으로 남음). 팀 결정 전까지 이 라우트를 앱 홈/카드
화면에 연결하지 않는다.

응답 형태는 모델 브릿지(tuntun_peer_bridge/)가 이미 response.schema.json으로
엄격 검증한 JSON을 그대로 통과시킨다. 필드가 매우 많고(components[]·ageContext·
habitContext·inputUsage 등) 브릿지 쪽에서 검증 책임을 이미 지므로, 여기서
Pydantic으로 필드를 하나하나 다시 선언해 이중 유지보수하지 않는다 - dict를 그대로
받아 그대로 반환하되, 최소한 "이게 어떤 응답인지" 알 수 있도록 몇 개 핵심 필드만
명시한다.
"""

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class TuntunScorePeerV2Response(BaseModel):
    """브릿지 응답 pass-through. extra=allow로 나머지 필드(components, ageContext,
    habitContext, inputUsage 등)를 전부 보존한다."""

    model_config = ConfigDict(extra="allow", populate_by_name=True)

    schema_version: str = Field(alias="schemaVersion")
    is_mock: bool = Field(alias="isMock")
    release_status: str = Field(alias="releaseStatus")
    score_available: bool = Field(alias="scoreAvailable")
    peer_composite_score: float | None = Field(default=None, alias="peerCompositeScore")
    composite_display: dict[str, Any] = Field(alias="compositeDisplay")
    components: list[dict[str, Any]]
