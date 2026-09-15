from pydantic import BaseModel


class EditorialClaim(BaseModel):
    """approved_claims.json의 released=true claim 하나 - text는 플레이스홀더까지
    치환된 최종 문장(GPT가 편집·의역하면 안 되는 원문 그대로)."""

    id: str
    text: str


class WeeklyEditorialResponse(BaseModel):
    """⚠️ 2026-09-15 신규 - 문홍주 팀장님 SHAP/XAI 검토 회신 반영. 데모 범위가
    "기록 기사"로 좁혀져서, mode는 이번 범위에선 항상 "records"(모델 방향 주장 claim은
    전부 released=false라 아직 못 씀). approved_model_claims가 비어 있으면 폴백
    (fallback_text + fallback_reason)으로 내려감 - approved_claims.json의 fallback
    정책 그대로.

    ⚠️ 절대 여기 안 넣을 것(app_payload_boundary): SHAP contributions 배열, base_value,
    개인정보, 내부 절대 점수(100점 만점 값). 이 DTO엔 애초에 그런 필드가 없음 - 구조
    자체로 실수 방지.
    """

    mode: str  # 이번 범위에선 항상 "records"
    approved_model_claims: list[EditorialClaim]
    fallback_reason: str | None
    fallback_text: str | None
    linked_record_ids: list[str]
