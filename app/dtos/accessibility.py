from datetime import datetime

from pydantic import BaseModel

from app.dtos.base import BaseSerializerModel


class AccessibilityUpdateRequest(BaseModel):
    """전부 선택 필드 — 보낸 것만 부분 수정됨(PATCH 시맨틱).
    예: {"senior_mode": true}만 보내면 나머지 필드는 그대로 유지됨."""

    large_controls: bool | None = None
    reduced_motion: bool | None = None
    preferred_text_scale_hint: str | None = None
    senior_mode: bool | None = None


class AccessibilityResponse(BaseSerializerModel):
    large_controls: bool
    reduced_motion: bool
    preferred_text_scale_hint: str | None = None
    senior_mode: bool
    updated_at: datetime
