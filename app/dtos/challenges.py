from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.dtos.base import BaseSerializerModel


class SkipChallengeRequest(BaseModel):
    reason: str | None = None
    occurred_at: datetime | None = None  # 오프라인 대비: 실제 건너뛴 시각 (HANDOFF.md §3.3)


class ChallengeProgressResponse(BaseSerializerModel):
    """진행 상태 조회 및 완료/건너뛰기 응답 공통 포맷."""

    id: UUID
    exec_type: str
    state: str
    target_duration_seconds: int | None = None
    accumulated_duration_seconds: int
    target_count: int | None = None
    accumulated_count: int
    version: int


class CompleteChallengeResponse(BaseSerializerModel):
    challenge: ChallengeProgressResponse
    points_awarded: int
    five_element: str
