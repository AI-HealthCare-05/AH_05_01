from datetime import datetime, timedelta

from app.dtos.notification_settings import (
    NotificationSettingResponse,
    NotificationSettingUpdateRequest,
    OnboardingScheduleRequest,
)
from app.models.users import User
from app.repositories.notification_setting_repository import NotificationSettingRepository

# ⚠️ 2026-09-08 반영: "점심"은 자동 계산(기상+N시간) 대신 사용자가 직접 입력하는 값으로
# 바뀜(늦게 일어나는 사람은 자동 계산이 실제 점심시간과 안 맞았음) - 아래 _shift_time()은
# 이제 오전(기상+2시간)·저녁(취침-2시간)에만 쓰임.


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
        """A10: 기상/점심/취침 시각 -> 슬롯 3개로 변환해서 저장.
        2026-09-08 재개정:
          - 아침 준비 = 기상 시각 그대로(일어난 직후)
          - 점심 뒤   = 입력한 점심 시각 + 1시간
          - 자기 전   = 취침 - 2시간
        F02 화면에서 나중에 개별 슬롯을 껐다 켰다 할 수 있음(별도 PATCH API)."""

        slots = [
            request.wake_time,
            _shift_time(request.lunch_time, hours=1),
            _shift_time(request.sleep_time, hours=-2),
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
