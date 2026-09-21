from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.accessibility import AccessibilityResponse, AccessibilityUpdateRequest
from app.models.users import User
from app.services.accessibility_service import AccessibilityService

accessibility_router = APIRouter(prefix="/accessibility", tags=["accessibility"])


@accessibility_router.get("", response_model=AccessibilityResponse, status_code=status.HTTP_200_OK)
async def get_accessibility(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[AccessibilityService, Depends(AccessibilityService)],
) -> AccessibilityResponse:
    """설정한 적 없으면 전부 기본값(False)으로 자동 생성해서 반환."""

    return await service.get_current(user)


@accessibility_router.patch("", response_model=AccessibilityResponse, status_code=status.HTTP_200_OK)
async def update_accessibility(
    request: AccessibilityUpdateRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[AccessibilityService, Depends(AccessibilityService)],
) -> AccessibilityResponse:
    """보낸 필드만 부분 수정. 시니어 모드만 켜고 싶으면 {"senior_mode": true}만 보내면 됨."""

    return await service.update(user, request)
