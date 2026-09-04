from datetime import date

from pydantic import BaseModel

DayStatus = str  # "COMPLETED" / "INCOMPLETE" / "REST"


class CalendarDayItem(BaseModel):
    date: date
    status: DayStatus


class MonthlyCalendarResponse(BaseModel):
    """D01: 월 캘린더."""

    year: int
    month: int
    days: list[CalendarDayItem]
    completed_count: int
    rest_count: int


class WeeklyMaterialItem(BaseModel):
    element: str
    material_name: str
    count: int


class WeeklyReportResponse(BaseModel):
    """D02: 주간 리포트."""

    start_date: date
    end_date: date
    days: list[CalendarDayItem]  # 최근 7일
    completed_count: int
    total_days: int  # 항상 7
    best_time_slot: str | None  # "아침" / "점심 뒤" / "저녁" / "밤" 중 가장 완료가 많았던 시간대, 데이터 없으면 None
    materials_this_week: list[WeeklyMaterialItem]


class DayDetailResponse(BaseModel):
    """D03: 하루 상세 시트."""

    date: date
    status: DayStatus
    mission_title: str | None = None
    exec_type: str | None = None
    completed_at: str | None = None  # ISO datetime string, 완료 안 했으면 None
    duration_seconds: int | None = None  # 타이머/센서형이면 걸린 시간
    count_achieved: int | None = None  # 카운터형이면 달성한 수치
    element: str | None = None
    material_name: str | None = None
    memo: str | None = None


class MemoUpdateRequest(BaseModel):
    memo: str | None  # None을 보내면 메모 삭제


class RestDayRequest(BaseModel):
    service_date: date


class StreakResponse(BaseModel):
    """D05: 연속 기록."""

    current_streak: int  # 쉼 제외하고 이어온 날. 오늘부터 거슬러 올라가며 계산
    longest_streak: int
    rest_days_used_this_week: int  # 이번 주(월~일)에 이미 쓴 쉼 횟수
    rest_days_remaining_this_week: int  # 한 주 최대 2번 중 남은 횟수
