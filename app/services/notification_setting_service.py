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
        F02 화면에서 나중에 개별 슬롯을 껐다 켰다 할 수 있음(별도 PATCH API).

        ⚠️ 2026-09-18 수정(UI/UX 핸드오프 P01~03) - 계산된 slots뿐 아니라 원본 입력값
        (wake_time/lunch_time/sleep_time)도 그대로 저장해서 나중에 재조회 가능하게 함.
        또한 "가입 POST와 수정 PATCH를 구분하고 enabled를 보존한다"는 계약에 맞춰,
        이미 레코드가 있으면(이 엔드포인트 재호출 - 예: 온보딩 재시도) enabled를
        무조건 True로 덮어쓰지 않고 기존 값을 유지함 - 최초 생성일 때만 True로 시작.
        """

        slots = [
            request.wake_time,
            _shift_time(request.lunch_time, hours=1),
            _shift_time(request.sleep_time, hours=-2),
        ]

        instance = await self.repo.get_or_create(user.id)
        is_first_submission = instance.wake_time is None
        updated = await self.repo.update(
            instance,
            slots=slots,
            wake_time=request.wake_time,
            lunch_time=request.lunch_time,
            sleep_time=request.sleep_time,
            enabled=True if is_first_submission else None,
        )
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
            # ⚠️ 2026-09-18 추가(UI/UX 핸드오프 P01~03) - "생활시간 바꾸기" 재수정 시
            # 원본 시각도 같이 최신화. 안 보내면(None) repo.update()가 그 필드는 안 건드림.
            wake_time=request.wake_time,
            lunch_time=request.lunch_time,
            sleep_time=request.sleep_time,
        )
        return NotificationSettingResponse.model_validate(updated)
