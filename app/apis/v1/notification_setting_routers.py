from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.notification_settings import (
    NotificationSettingResponse,
    NotificationSettingUpdateRequest,
    OnboardingScheduleRequest,
)
from app.models.users import User
from app.services.notification_setting_service import NotificationSettingService

notification_setting_router = APIRouter(prefix="/notification-settings", tags=["notification-settings"])


@notification_setting_router.post(
    "/schedule", response_model=NotificationSettingResponse, status_code=status.HTTP_200_OK
)
async def submit_onboarding_schedule(
    request: OnboardingScheduleRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[NotificationSettingService, Depends(NotificationSettingService)],
) -> NotificationSettingResponse:
    """A10: 기상/취침 시각 입력 -> 알림 슬롯 자동 생성 (아침 준비/점심 뒤/자기 전)."""

    return await service.submit_onboarding_schedule(user, request)


@notification_setting_router.get("", response_model=NotificationSettingResponse, status_code=status.HTTP_200_OK)
async def get_notification_settings(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[NotificationSettingService, Depends(NotificationSettingService)],
) -> NotificationSettingResponse:
    """A09/A10을 건너뛴 사용자는 기본값(enabled=True, slots=[])으로 자동 생성되어 반환됨."""

    return await service.get_current(user)


@notification_setting_router.patch("", response_model=NotificationSettingResponse, status_code=status.HTTP_200_OK)
async def update_notification_settings(
    request: NotificationSettingUpdateRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[NotificationSettingService, Depends(NotificationSettingService)],
) -> NotificationSettingResponse:
    """F02: 마이 > 생활시간·알림에서 개별 슬롯 수정/끄기 등에 사용."""

    return await service.update(user, request)
