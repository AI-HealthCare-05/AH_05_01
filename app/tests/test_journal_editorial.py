from copy import deepcopy
from datetime import date
from types import SimpleNamespace

import pytest
from httpx import ASGITransport, AsyncClient

from app.dependencies.security import get_request_user
from app.main import app
from app.services.journal_editorial_service import (
    age_bounds,
    content_hash,
    eligibility_errors,
    load_catalog,
    select_editorial,
)

TODAY = date(2026, 9, 16)


@pytest.fixture(scope="session", autouse=True)
def initialize():
    yield  # Pure catalog/API tests; do not initialize MySQL.


def approved(item, source):
    return {
        **{key: item[key] for key in ("id", "revision", "content_sha256", "source_id")},
        "source_edition": source["edition"],
        "status": "approved",
        "reviewer": "synthetic-reviewer",
        "approval_id": "TEST-ONLY",
        "rights_status": "cleared_for_app_summary",
        "rights_review_id": "TEST-ONLY",
        "expires_on": "2027-01-01",
    }


def test_pending_catalog_is_empty_in_app_and_visible_only_in_offline_preview():
    catalog, sources, approvals = load_catalog()
    assert len(catalog) == 40
    result = select_editorial(catalog, sources, approvals, today=TODAY, ages=(35, 35))
    assert all(section.status == "empty" and not section.articles for section in result.sections)
    preview = select_editorial(catalog, sources, approvals, today=TODAY, ages=(35, 35), preview=True)
    assert preview.preview and sum(len(section.articles) for section in preview.sections) == 40


@pytest.mark.parametrize(
    "field,value,expected",
    [
        ("text", "changed after review", "content_changed"),
        ("title", "", "content_changed"),
        ("source_id", "wrong-source", "source_unverified"),
    ],
)
def test_changed_copy_cannot_reuse_approval(field, value, expected):
    catalog, sources, _ = load_catalog()
    item, source = deepcopy(catalog[0]), sources[0]
    approval = approved(item, source)
    item[field] = value
    assert expected in eligibility_errors(item, source, approval, today=TODAY, ages=None)


@pytest.mark.parametrize(
    "url",
    [
        "http://health.kdca.go.kr/a",
        "https://health.kdca.go.kr.evil.test/",
        "javascript:alert(1)",
        "https://evil@health.kdca.go.kr/a",
        "https://health.kdca.go.kr:444/a",
    ],
)
def test_source_links_must_be_official_https(url):
    catalog, sources, _ = load_catalog()
    source = {**sources[0], "url": url}
    assert "source_url_invalid" in eligibility_errors(catalog[0], source, {}, today=TODAY, ages=None, preview=True)


@pytest.mark.parametrize(
    "ages,visible",
    [(None, False), ((18, 19), False), ((19, 19), True), ((64, 64), True), ((64, 65), False), ((65, 65), False)],
)
def test_age_restricted_guidance_never_uses_an_assumed_birthday(ages, visible):
    result = select_editorial(*load_catalog(), today=TODAY, ages=ages, preview=True)
    ids = {article.id for section in result.sections for article in section.articles}
    assert "movement.equivalence" not in ids
    assert ("movement.replace_sitting" in ids) is visible


def test_approval_and_source_expiry_fail_closed():
    catalog, sources, _ = load_catalog()
    item, source = catalog[0], sources[0]
    approval = approved(item, source)
    assert not eligibility_errors(item, source, approval, today=TODAY, ages=None)
    assert "approval_expired" in eligibility_errors(
        item, source, {**approval, "expires_on": "2026-09-14"}, today=TODAY, ages=None
    )
    assert "source_review_due" in eligibility_errors(
        item, {**source, "recheck_by": "2026-09-14"}, approval, today=TODAY, ages=None
    )
    assert "source_use_unconfirmed" in eligibility_errors(
        item, source, {**approval, "source_edition": "other edition"}, today=TODAY, ages=None
    )


def test_real_approval_selects_one_fixed_article_per_section():
    catalog, sources, _ = load_catalog()
    source_map = {source["id"]: source for source in sources}
    approvals = [approved(item, source_map[item["source_id"]]) for item in catalog]
    first = select_editorial(catalog, sources, approvals, today=TODAY, ages=(35, 35))
    second = select_editorial(list(reversed(catalog)), sources, approvals, today=TODAY, ages=(35, 35))
    assert first == second
    assert all(len(section.articles) == 1 for section in first.sections)
    assert not first.preview


def test_missing_source_duplicate_ids_and_hashes():
    catalog, sources, approvals = load_catalog()
    assert all(content_hash(item) == item["content_sha256"] for item in catalog)
    assert all(
        not section.articles for section in select_editorial(catalog, [], approvals, today=TODAY, ages=None).sections
    )
    with pytest.raises(ValueError, match="Duplicate"):
        select_editorial(catalog + [catalog[0]], sources, approvals, today=TODAY, ages=None)
    assert age_bounds(SimpleNamespace(birth_year=2007, birth_month=9), TODAY) == (18, 19)
    assert age_bounds(SimpleNamespace(birth_year=1961, birth_month=10), TODAY) == (64, 64)


@pytest.mark.asyncio
async def test_editorial_route_requires_login_and_cannot_enable_drafts(monkeypatch):
    monkeypatch.setattr("app.services.journal_editorial_service.service_today", lambda _: TODAY)
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        assert (await client.get("/api/v1/tuntun-score/editorial")).status_code == 401
        app.dependency_overrides[get_request_user] = lambda: SimpleNamespace(id=1, birth_year=1991, birth_month=1)
        try:
            response = await client.get("/api/v1/tuntun-score/editorial?preview=true")
            assert response.status_code == 200
            assert response.json()["preview"] is False
            assert all(not section["articles"] for section in response.json()["sections"])
        finally:
            app.dependency_overrides.pop(get_request_user, None)
