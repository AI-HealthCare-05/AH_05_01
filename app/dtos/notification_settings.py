import re
from datetime import datetime

from pydantic import BaseModel, field_validator

from app.dtos.base import BaseSerializerModel

TIME_PATTERN = re.compile(r"^([01]\d|2[0-3]):([0-5]\d)$")  # HH:MM, 24시간제


def _validate_time_string(value: str) -> str:
    if not TIME_PATTERN.match(value):
        raise ValueError("시간 형식은 HH:MM(24시간제)이어야 합니다. 예: 07:00, 23:30")
    return value


class OnboardingScheduleRequest(BaseModel):
    """A10: "몇 시에 자고 일어나?" 이 두 값만 받아서 서버가 알림 슬롯 3개로 변환.
    (아침 준비=기상시각, 점심 뒤=고정 13:00, 자기 전=취침시각-1시간)"""

    wake_time: str  # "07:00"
    sleep_time: str  # "23:30"

    @field_validator("wake_time", "sleep_time")
    @classmethod
    def _check_time_format(cls, v: str) -> str:
        return _validate_time_string(v)


class NotificationSettingUpdateRequest(BaseModel):
    """F02 화면(마이·생활시간 알림)에서 세부 조정할 때 사용. 전부 선택 필드 — 보낸 것만 반영."""

    enabled: bool | None = None
    slots: list[str] | None = None
    weekdays: list[str] | None = None
    quiet_hours: dict | None = None

    @field_validator("slots")
    @classmethod
    def _check_slots_format(cls, v: list[str] | None) -> list[str] | None:
        if v is not None:
            for slot in v:
                _validate_time_string(slot)
        return v


class NotificationSettingResponse(BaseSerializerModel):
    timezone: str
    slots: list[str]
    weekdays: list[str]
    quiet_hours: dict | None = None
    enabled: bool
    updated_at: datetime
