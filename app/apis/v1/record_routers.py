from datetime import date
from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.records import (
    DayDetailResponse,
    MemoUpdateRequest,
    MonthlyCalendarResponse,
    RestDayRequest,
    StreakResponse,
    WeeklyReportResponse,
)
from app.models.users import User
from app.services.record_service import RecordService

record_router = APIRouter(prefix="/records", tags=["records"])


@record_router.get("/calendar", response_model=MonthlyCalendarResponse, status_code=status.HTTP_200_OK)
async def get_monthly_calendar(
    year: int,
    month: int,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[RecordService, Depends(RecordService)],
) -> MonthlyCalendarResponse:
    """D01: 월 캘린더."""

    return await service.get_monthly_calendar(user, year, month)


@record_router.get("/weekly", response_model=WeeklyReportResponse, status_code=status.HTTP_200_OK)
async def get_weekly_report(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[RecordService, Depends(RecordService)],
) -> WeeklyReportResponse:
    """D02: 주간 리포트 (오늘 포함 최근 7일)."""

    return await service.get_weekly_report(user)


@record_router.get("/day/{target_date}", response_model=DayDetailResponse, status_code=status.HTTP_200_OK)
async def get_day_detail(
    target_date: date,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[RecordService, Depends(RecordService)],
) -> DayDetailResponse:
    """D03: 하루 상세 시트."""

    return await service.get_day_detail(user, target_date)


@record_router.patch("/day/{target_date}/memo", response_model=DayDetailResponse, status_code=status.HTTP_200_OK)
async def update_day_memo(
    target_date: date,
    request: MemoUpdateRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[RecordService, Depends(RecordService)],
) -> DayDetailResponse:
    """D03: "내 메모" 저장/수정."""

    return await service.update_memo(user, target_date, request)


@record_router.post("/rest-day", response_model=StreakResponse, status_code=status.HTTP_200_OK)
async def mark_rest_day(
    request: RestDayRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[RecordService, Depends(RecordService)],
) -> StreakResponse:
    """D05: "이번 주 쉼 표시하기". 한 주에 최대 2번까지만 가능."""

    return await service.mark_rest_day(user, request)


@record_router.get("/streak", response_model=StreakResponse, status_code=status.HTTP_200_OK)
async def get_streak(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[RecordService, Depends(RecordService)],
) -> StreakResponse:
    """D05: 연속 기록(쉼 제외 이어온 날) + 최장 기록."""

    return await service.get_streak(user)
