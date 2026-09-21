from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.dtos.base import BaseSerializerModel


class HealthInputCreateRequest(BaseModel):
    """기능명세서 §2 확인 필드 그대로. input_values/units는 자유 key-value라
    프론트에서 어떤 키를 보내는지는 팀 협의로 확정 필요 (예: height_cm, weight_kg, waist_cm)."""

    input_values: dict
    units: dict
    source: str = "MANUAL"  # MANUAL / ESTIMATED / HEALTH_CONNECT
    measured_at: datetime | None = None  # 생략 시 서버 현재시각 사용


class HealthInputResponse(BaseSerializerModel):
    id: UUID
    measured_at: datetime
    input_values: dict
    units: dict
    source: str
    created_at: datetime
