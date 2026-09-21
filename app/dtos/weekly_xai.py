"""주별 참고점수와 실제 실천 기록의 비교 계약."""

from datetime import date
from typing import Literal

from pydantic import BaseModel, Field


class WeeklyReferencePoint(BaseModel):
    week_start: date
    week_end: date
    observed_on: date | None = None
    status: Literal["recorded", "missing", "incompatible"] = "missing"
    diabetes: float | None = Field(default=None, ge=0, le=100)
    hypertension: float | None = Field(default=None, ge=0, le=100)
    comparison_key: str | None = None


class WeeklyPracticePoint(BaseModel):
    week_start: date
    recorded: bool
    completed_days: int = Field(ge=0, le=7)
    rest_days: int = Field(ge=0, le=7)
    elapsed_days: int = Field(ge=0, le=7)


class WeeklyXaiHistoryResponse(BaseModel):
    schema_version: Literal["tmtn-weekly-xai-v1"] = "tmtn-weekly-xai-v1"
    status: Literal["ready", "unavailable"]
    reason: str | None = None
    points: list[WeeklyReferencePoint] = Field(default_factory=list)
    practice: list[WeeklyPracticePoint] = Field(default_factory=list)
