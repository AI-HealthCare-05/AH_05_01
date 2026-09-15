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
    """A10: "몇 시에 자고, 점심은 언제, 일어나?" 이 세 값만 받아서 서버가 알림 슬롯 3개로 변환.
    (오전=기상시각+2시간, 점심=입력한 점심시각 그대로, 저녁=취침시각-2시간)

    ⚠️ 2026-09-08 반영: 원래 lunch는 "기상+6시간"으로 자동 계산했는데, 사용자가 실제로
    점심 먹는 시각과 안 맞을 수 있어서(예: 늦게 일어나는 사람) 직접 입력받는 걸로 바꿈.
    오전 슬롯도 "기상 직후"가 아니라 "기상+2시간"으로(일어나자마자보다는 준비를 좀
    마친 뒤가 알림 받기에 더 자연스러움), 저녁도 "취침-1시간"에서 "취침-2시간"으로."""

    wake_time: str  # "07:00"
    lunch_time: str  # "12:00"
    sleep_time: str  # "23:30"

    @field_validator("wake_time", "lunch_time", "sleep_time")
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
