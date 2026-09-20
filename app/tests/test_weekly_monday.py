"""월~일 범위와 미래 날짜는 실제 기록 집계에서 구분되어야 한다."""

from datetime import date, datetime, timedelta
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from app.services import record_service
from app.services.record_service import RecordService


@pytest.fixture(scope="session", autouse=True)
def initialize():
    # 저장소 경계를 모의 처리하는 단위 테스트에는 회원 DB가 필요하지 않다.
    yield


@pytest.mark.parametrize("today", [date(2026, 9, 14), date(2026, 9, 19), date(2026, 9, 20), date(2027, 1, 1)])
async def test_report_is_monday_to_sunday_without_future_failures(monkeypatch, today):
    monkeypatch.setattr(record_service, "service_today", lambda _: today)
    monday = today - timedelta(days=today.weekday())
    user = SimpleNamespace(id=7, created_at=datetime(2026, 1, 1))
    service = RecordService()
    status_map = {
        monday + timedelta(days=i): ("REST" if i == 0 else "INCOMPLETE", None) for i in range(today.weekday() + 1)
    }
    service._build_status_map = AsyncMock(return_value=status_map)
    report = await service.get_weekly_report(user)
    assert report.start_date == monday
    assert report.end_date == monday + timedelta(days=6)
    assert len(report.days) == report.total_days == 7
    assert [d.date.weekday() for d in report.days] == list(range(7))
    assert all(d.status == "FUTURE" for d in report.days if d.date > today)
    assert report.completed_count == 0
    assert sum(d.status == "REST" for d in report.days) == 1
    service._build_status_map.assert_awaited_once_with(7, monday, today, signup_date=date(2026, 1, 1))
