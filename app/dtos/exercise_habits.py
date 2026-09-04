from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field, model_validator

from app.dtos.base import BaseSerializerModel


class ExerciseHabitsRequest(BaseModel):
    """A08 화면 그대로: 근력운동 주당 횟수+강도, 유산소(저/중/고강도) 주당 분."""

    strength_weekly_count: int = Field(ge=0, le=5, description="0=안 함, 5=주 5회 이상")
    strength_intensity: str | None = Field(None, description="LIGHT / MODERATE / HARD")
    aerobic_low_minutes: int = Field(0, ge=0, le=1000)
    aerobic_moderate_minutes: int = Field(0, ge=0, le=1000)
    aerobic_high_minutes: int = Field(0, ge=0, le=1000)

    @model_validator(mode="after")
    def _validate_intensity_required_if_exercising(self) -> "ExerciseHabitsRequest":
        if self.strength_weekly_count > 0 and self.strength_intensity is None:
            raise ValueError("근력운동을 한다면 강도(strength_intensity)를 선택해야 합니다.")
        if self.strength_weekly_count == 0:
            self.strength_intensity = None  # 안 함이면 강도 값 무시하고 항상 None으로 저장
        return self


class ExerciseHabitsResponse(BaseSerializerModel):
    id: UUID
    strength_weekly_count: int
    strength_intensity: str | None = None
    aerobic_low_minutes: int
    aerobic_moderate_minutes: int
    aerobic_high_minutes: int
    recorded_at: datetime
