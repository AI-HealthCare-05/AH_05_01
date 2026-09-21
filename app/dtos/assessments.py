from datetime import datetime
from uuid import UUID

from app.dtos.base import BaseSerializerModel


class AssessmentResultResponse(BaseSerializerModel):
    """기능명세서 §9.2.1 확인 필드: score·band·factors·model_version·calibration_version·copy_version."""

    id: UUID
    job_id: UUID
    score: float
    band: str
    factors: list | None = None
    model_version: str
    calibration_version: str | None = None
    copy_version: str | None = None
    access_status: str
    created_at: datetime


class AssessmentJobResponse(BaseSerializerModel):
    id: UUID
    status: str
    created_at: datetime
    result: AssessmentResultResponse | None = None
