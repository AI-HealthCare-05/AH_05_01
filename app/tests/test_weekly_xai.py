"""주간 저장·누락·버전·계정/동의 경계와 내부 승인 범위를 확인한다."""

import ast
import json
from copy import deepcopy
from datetime import date, timedelta
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from aerich.utils import decompress_dict
from httpx import ASGITransport, AsyncClient

from app.core.config import Env
from app.dependencies.security import get_request_user
from app.dtos.personal_xai import PersonalXaiSnapshot
from app.main import app
from app.services.journal_editorial_service import eligibility_errors, load_catalog, select_editorial
from app.services.weekly_xai_service import WeeklyXaiService, comparison_key, history_points


@pytest.fixture(scope="session", autouse=True)
def initialize():
    yield


def snapshot(day="2026-09-20"):
    value = json.loads((Path(__file__).parent / "fixtures/personal_xai.synthetic.json").read_text(encoding="utf8"))
    value["reference_date"] = day
    if value.get("activity_comparison"):
        value["activity_comparison"]["reference_date"] = day
    return value


def row(day):
    observed = date.fromisoformat(day)
    return SimpleNamespace(week_start=observed - timedelta(days=observed.weekday()), snapshot=snapshot(day))


def test_eight_monday_weeks_with_missing_values_never_imputed():
    points = history_points([row("2026-09-13"), row("2026-09-20")], date(2026, 9, 20))
    assert len(points) == 8 and all(p.week_start.weekday() == 0 for p in points)
    assert points[-2].week_start == date(2026, 9, 7)
    assert points[-1].week_end == date(2026, 9, 20)
    assert all(p.diabetes is None and p.status == "missing" for p in points[:-2])
    assert points[-1].diabetes == snapshot()["domains"][0]["output_value"]
    assert points[-2].comparison_key == points[-1].comparison_key


def test_history_does_not_reuse_invalid_shap_or_assign_to_another_week():
    previous = row("2026-09-13")
    previous.snapshot["domains"][0]["background_sha256"] = "0" * 64
    current = row("2026-09-20")
    current.week_start = date(2026, 9, 7)
    for item in (previous, current):
        point = history_points([item], date(2026, 9, 20))[-2]
        assert point.status == "incompatible" and point.diabetes is None


def test_comparison_includes_release_and_reference_group_not_input_or_date():
    first = PersonalXaiSnapshot.model_validate(snapshot())
    changed_input = first.model_copy(update={"input_revision": "another-input", "reference_date": "2026-09-13"})
    assert comparison_key(first) == comparison_key(changed_input)
    changed_release = first.model_copy(update={"release_sha256": "another-release"})
    assert comparison_key(first) != comparison_key(changed_release)
    changed_group = deepcopy(first)
    changed_group.domains[0].reference_group = "65+:1"
    assert comparison_key(first) != comparison_key(changed_group)


@pytest.mark.asyncio
async def test_history_requires_consent_and_is_scoped_to_signed_in_user(monkeypatch):
    service = WeeklyXaiService()
    monkeypatch.setattr("app.services.weekly_xai_service.config.ENV", Env.DEV)
    monkeypatch.setattr("app.services.weekly_xai_service.service_today", lambda _: date(2026, 9, 20))
    service.consent_repo.get_latest_by_purpose = AsyncMock(return_value=None)
    service.history_repo.list_since = AsyncMock(return_value=[])
    result = await service.get_history(SimpleNamespace(id=72))
    assert result.reason == "consent_required" and not result.points
    service.history_repo.list_since.assert_not_called()
    service.consent_repo.get_latest_by_purpose.return_value = SimpleNamespace(status="AGREED")
    service.record_repo.get_card_sets_in_range = AsyncMock(return_value=[])
    service.record_repo.get_notes_in_range = AsyncMock(return_value={})
    result = await service.get_history(SimpleNamespace(id=72))
    service.history_repo.list_since.assert_awaited_once_with(72, date(2026, 7, 27), date(2026, 9, 14))
    assert len(result.points) == 8 and all(not p.recorded for p in result.practice)
    monkeypatch.setattr("app.services.weekly_xai_service.config.ENV", Env.PROD)
    assert (await service.get_history(SimpleNamespace(id=72))).reason == "release_review_required"


def test_all_forty_internal_approvals_are_bound_to_actual_copy_and_not_public():
    catalog, sources, approvals = load_catalog(internal_review=True)
    assert len(approvals) == len(catalog) == 40
    source_map = {source["id"]: source for source in sources}
    approval_map = {item["id"]: item for item in approvals}
    today = date(2026, 9, 20)
    for item in catalog:
        source, approval = source_map[item["source_id"]], approval_map[item["id"]]
        assert not eligibility_errors(item, source, approval, today=today, ages=(35, 35), internal_review=True)
        assert "internal_only" in eligibility_errors(item, source, approval, today=today, ages=(35, 35))
        changed = {**approval, "content_sha256": "0" * 64}
        assert "content_unapproved" in eligibility_errors(
            item, source, changed, today=today, ages=(35, 35), internal_review=True
        )
    internal = select_editorial(catalog, sources, approvals, today=today, ages=(35, 35), internal_review=True)
    assert [len(section.articles) for section in internal.sections] == [1, 1, 1]
    assert all(
        not section.articles
        for section in select_editorial(catalog, sources, approvals, today=today, ages=(35, 35)).sections
    )


@pytest.mark.asyncio
async def test_history_http_is_authenticated_and_never_cached():
    app.dependency_overrides[WeeklyXaiService] = lambda: SimpleNamespace(
        get_history=AsyncMock(return_value={"status": "ready"})
    )
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            assert (await client.get("/api/v1/tuntun-score/personal/history")).status_code == 401
            app.dependency_overrides[get_request_user] = lambda: SimpleNamespace(id=5)
            response = await client.get("/api/v1/tuntun-score/personal/history?user_id=99")
            assert response.status_code == 200 and response.headers["cache-control"] == "no-store"
    finally:
        app.dependency_overrides.pop(WeeklyXaiService, None)
        app.dependency_overrides.pop(get_request_user, None)


def test_migration_only_adds_weekly_table_and_user_relation():
    folder = Path(__file__).parents[1] / "core/db/migrations/models"

    def state(path):
        tree = ast.parse(path.read_text(encoding="utf8"))
        encoded = next(
            ast.literal_eval(n.value)
            for n in tree.body
            if isinstance(n, ast.Assign) and any(getattr(target, "id", None) == "MODELS_STATE" for target in n.targets)
        )
        return decompress_dict(encoded)

    before = state(next(folder.glob("23_*.py")))
    path = next(folder.glob("24_*_add_weekly_xai_snapshots.py"))
    after = state(path)
    key = next(key for key in after if key.endswith(".WeeklyXaiSnapshot"))
    assert after[key]["unique_together"] == [["user", "week_start"]]
    del after[key]
    for model in after.values():
        model["backward_fk_fields"] = [
            field for field in model["backward_fk_fields"] if field["name"] != "weekly_xai_snapshots"
        ]
    assert before == after
    upgrade_sql = path.read_text(encoding="utf8").split("async def downgrade")[0]
    assert "DROP " not in upgrade_sql and "ALTER " not in upgrade_sql
