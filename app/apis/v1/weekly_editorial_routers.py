from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.weekly_editorial import WeeklyEditorialResponse
from app.models.users import User
from app.services.weekly_editorial_service import WeeklyEditorialService

weekly_editorial_router = APIRouter(tags=["weekly-editorial"])


@weekly_editorial_router.get(
    "/weekly-editorial", response_model=WeeklyEditorialResponse, status_code=status.HTTP_200_OK
)
async def get_weekly_editorial(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[WeeklyEditorialService, Depends(WeeklyEditorialService)],
) -> WeeklyEditorialResponse:
    """⚠️ 2026-09-15 신규 - 틈튼일보 "기록 기사"(records 모드). 데모 범위가 기록 기사로
    좁혀지면서 만든 첫 배선. approved_claims.json의 released=true 5개 claim만 씀 -
    모델 방향 주장 claim(당뇨·고혈압)은 전부 released=false라 아직 안 나감."""

    return await service.get_weekly_editorial(user)
