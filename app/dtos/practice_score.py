from pydantic import BaseModel


class PracticeScoreResponse(BaseModel):
    """⚠️ 2026-09-15 v2 - 강호님(모델) 조합 방향 반영해서 실제로 채워짐(v1은 항상
    null이었음). composite_score는 표시용 반올림값(display_score), composite_blocked_reason은
    여러 사유가 있을 수 있어 쉼표로 이어붙임(예: "INITIAL_FORMULA_POLICY_PENDING")."""

    as_of: str
    daily_units: float | None
    cumulative_units: float | None
    practice_score: float | None
    confirmed_rest_run: int
    unknown_run: int
    freshness: str
    policy_version: str
    lifestyle_score: float | None
    composite_score: float | None
    composite_blocked_reason: str | None
    ledger_state: str
    ledger_revision: str | None
    additional_time_policy: str
