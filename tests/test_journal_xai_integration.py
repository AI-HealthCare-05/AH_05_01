"""외부 DB·AI 호출 없이 발행 선택과 개인 결과 연결 계약을 확인합니다."""

from copy import deepcopy
from datetime import date, timedelta
from types import SimpleNamespace

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.apis.v1.tuntun_score_routers import tuntun_score_router
from app.dependencies.security import get_request_user
from app.dtos.personal_xai import PersonalXaiResponse
from app.services.journal_editorial_service import JournalEditorialService, load_catalog, select_editorial
from app.services.personal_xai_service import PersonalXaiService, immediate_activity, model_request


def catalog_approved_for_test():
    catalog, sources, _ = load_catalog()
    # 실제 출처 확인일 이전을 승인한 것으로 보지 않도록, 테스트용 확인일만 고정합니다.
    sources = [{**source, "checked_on": "2026-09-01"} for source in sources]
    source_map = {s["id"]: s for s in sources}
    approvals = [
        dict(
            **{key: a[key] for key in ("id", "revision", "content_sha256", "source_id")},
            source_edition=source_map[a["source_id"]]["edition"],
            status="approved",
            reviewer="test-only",
            approval_id="TEST-ONLY",
            rights_status="cleared_for_app_summary",
            rights_review_id="TEST-ONLY",
            expires_on="2027-01-01",
        )
        for a in catalog
    ]
    return catalog, sources, approvals


def test_one_daily_two_distinct_weekly_and_stable_monday_issue():
    records = catalog_approved_for_test()
    issues = [select_editorial(*records, today=date(2026, 9, 14) + timedelta(days=i), ages=(35, 35)) for i in range(7)]
    for issue in issues:
        assert len(issue.daily_articles) == 1
        assert len(issue.weekly_articles) == 2
        assert len({a.topic for a in issue.weekly_articles}) == 2
        assert issue.weekly_issue_start == date(2026, 9, 14)
        assert issue.weekly_articles == issues[0].weekly_articles
    assert len({issue.daily_articles[0].id for issue in issues}) > 1


def test_no_draft_leaks_through_new_issue_fields():
    result = select_editorial(*load_catalog(), today=date(2026, 9, 16), ages=(35, 35))
    assert result.daily_articles == result.weekly_articles == []
    assert not result.preview


def test_changed_text_loses_approval_in_all_placements():
    records = catalog_approved_for_test()
    first = select_editorial(*records, today=date(2026, 9, 16), ages=(35, 35))
    revoked = first.weekly_articles[0].id
    for item in records[0]:
        if item["id"] == revoked:
            item["text"] += " changed"
    second = select_editorial(*records, today=date(2026, 9, 16), ages=(35, 35))
    assert revoked not in {a.id for a in second.daily_articles + second.weekly_articles}


@pytest.mark.parametrize(
    "day, monday", [("2026-09-13", "2026-09-07"), ("2026-09-14", "2026-09-14"), ("2027-01-01", "2026-12-28")]
)
def test_monday_boundary_even_when_no_copy_is_eligible(day, monday):
    issue = select_editorial([], [], [], today=date.fromisoformat(day), ages=None)
    assert issue.weekly_issue_start.isoformat() == monday


def inputs():
    return (
        SimpleNamespace(id=1, birth_year=1991, birth_month=1, gender="MALE", is_pregnant=None),
        SimpleNamespace(id="health-1", input_values={"height_cm": 170, "weight_kg": 64}),
        SimpleNamespace(
            id="habit-1",
            strength_weekly_count=4,
            strength_intensity="MODERATE",
            aerobic_low_minutes=60,
            aerobic_moderate_minutes=120,
            aerobic_high_minutes=0,
        ),
    )


def test_binding_changes_with_user_or_input_and_preserves_confirmed_days_contract():
    user, health, habit = inputs()
    request = model_request(user, health, habit, date(2026, 9, 16))
    assert request["strengthFrequencyUnit"] == "days"
    assert request == model_request(user, health, habit, date(2026, 9, 16))
    changed_user = deepcopy(user)
    changed_user.id = 2
    assert request["inputRevision"] != model_request(changed_user, health, habit, date(2026, 9, 16))["inputRevision"]
    habit.id = "habit-2"
    assert request["inputRevision"] != model_request(user, health, habit, date(2026, 9, 16))["inputRevision"]


def test_real_survey_aggregates_remain_separate_from_mission_history():
    user, health, habit = inputs()
    request = model_request(user, health, habit, date(2026, 9, 16))
    result = immediate_activity(user, habit, request, date(2026, 9, 16))
    assert result is not None
    assert result.input_revision == request["inputRevision"]
    assert result.cards[0].value == 120
    assert result.cards[0].mean_display == "155"
    assert result.cards[1].value == 4
    assert result.cards[1].mean_display == "1.5"


@pytest.mark.asyncio
async def test_new_endpoints_require_authentication():
    api = FastAPI()
    api.include_router(tuntun_score_router, prefix="/api/v1")
    async with AsyncClient(transport=ASGITransport(app=api), base_url="http://test") as client:
        for endpoint in ("personal", "editorial"):
            response = await client.get("/api/v1/tuntun-score/" + endpoint)
            assert response.status_code in (401, 403)


@pytest.mark.asyncio
async def test_pending_endpoint_is_private_and_editorial_cannot_enable_preview():
    class PersonalStub:
        async def get_personal(self, user):
            return PersonalXaiResponse(status="pending", retry_after_seconds=3)

    class EditorialStub:
        async def get_editorial(self, user):
            return select_editorial(*load_catalog(), today=date(2026, 9, 16), ages=(35, 35))

    api = FastAPI()
    api.include_router(tuntun_score_router, prefix="/api/v1")
    api.dependency_overrides[get_request_user] = lambda: inputs()[0]
    api.dependency_overrides[PersonalXaiService] = lambda: PersonalStub()
    api.dependency_overrides[JournalEditorialService] = lambda: EditorialStub()
    async with AsyncClient(transport=ASGITransport(app=api), base_url="http://test") as client:
        response = await client.get("/api/v1/tuntun-score/personal")
        assert response.status_code == 200
        assert response.headers["cache-control"] == "no-store"
        assert response.json()["status"] == "pending"
        editorial = await client.get("/api/v1/tuntun-score/editorial?preview=true")
        assert editorial.status_code == 200
        assert not editorial.json()["preview"]
        assert editorial.json()["daily_articles"] == editorial.json()["weekly_articles"] == []
