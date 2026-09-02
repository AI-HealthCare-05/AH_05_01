from datetime import datetime, timedelta

from app.dtos.notification_settings import (
    NotificationSettingResponse,
    NotificationSettingUpdateRequest,
    OnboardingScheduleRequest,
)
from app.models.users import User
from app.repositories.notification_setting_repository import NotificationSettingRepository

# ⚠️ 2026-08-27 Figma 핸드오프(HANDOFF.md §3.8)로 정정:
# "점심 뒤"는 고정 13:00이 아니라 "기상 + 6시간"으로 계산해야 함.
# 예전엔 LUNCH_SLOT="13:00" 고정값을 썼는데, 이러면 기상시각이 7시가 아닌 사용자는
# 전부 틀린 시각이 나가는 실제 버그였음.


def _shift_time(time_str: str, hours: int) -> str:
    """HH:MM 문자열을 hours만큼 이동. 자정을 넘나드는 경우도 자동 처리
    (예: 20:00 + 6시간 -> 다음날 02:00이 아니라 그냥 02:00으로 랩어라운드,
    24시간 시계 안에서만 도는 알림 슬롯이라 날짜 개념 없이 시각만 순환시키면 됨)."""

    dummy_date = datetime.strptime(time_str, "%H:%M")
    shifted = dummy_date + timedelta(hours=hours)
    return shifted.strftime("%H:%M")


class NotificationSettingService:
    def __init__(self):
        self.repo = NotificationSettingRepository()

    async def submit_onboarding_schedule(
        self, user: User, request: OnboardingScheduleRequest
    ) -> NotificationSettingResponse:
        """A10: 기상/취침 시각 -> 슬롯 3개로 변환해서 저장.
        HANDOFF.md §3.8 기준:
          - 아침 준비 = 기상 직후 (기상시각 그대로)
          - 점심 뒤   = 기상 + 6시간
          - 자기 전   = 취침 - 1시간
        F02 화면에서 나중에 개별 슬롯을 껐다 켰다 할 수 있음(별도 PATCH API)."""

        slots = [
            request.wake_time,
            _shift_time(request.wake_time, hours=6),
            _shift_time(request.sleep_time, hours=-1),
        ]

        instance = await self.repo.get_or_create(user.id)
        updated = await self.repo.update(instance, slots=slots, enabled=True)
        return NotificationSettingResponse.model_validate(updated)

    async def get_current(self, user: User) -> NotificationSettingResponse:
        instance = await self.repo.get_or_create(user.id)
        return NotificationSettingResponse.model_validate(instance)

    async def update(self, user: User, request: NotificationSettingUpdateRequest) -> NotificationSettingResponse:
        instance = await self.repo.get_or_create(user.id)
        updated = await self.repo.update(
            instance,
            enabled=request.enabled,
            slots=request.slots,
            weekdays=request.weekdays,
            quiet_hours=request.quiet_hours,
        )
        return NotificationSettingResponse.model_validate(updated)
