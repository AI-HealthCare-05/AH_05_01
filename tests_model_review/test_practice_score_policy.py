from datetime import UTC, date, datetime, timedelta
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

import pytest

from app.dtos.practice_score import PracticeScoreResponse
from app.services import practice_score_service as module
from app.services.practice_formula import trajectory

TODAY = date(2026, 9, 16)


def mission(sid):
    return dict(sessionId=sid, kind="mission", minutes=None)


def health():
    return [
        dict(
            componentKey=k,
            peerPercentile=70.0,
            available=True,
            direction="higher_is_healthier",
            valueType="health_direction_peer_percentile",
            modelVersion="test-model",
            referenceVersion="test-reference",
        )
        for k in module.HEALTH_KEYS
    ]


def service():
    s = module.PracticeScoreService()
    s._get_or_create_initial_snapshot = AsyncMock(
        return_value=SimpleNamespace(
            id="signup",
            input_revision="signup-input",
            formula_version="pending-selection",
            policy_status="pending",
            score=None,
        )
    )
    s.peer_service.fetch_bridge_result = AsyncMock(return_value=({"components": health()}, "input"))
    s._has_any_completion = AsyncMock(return_value=True)
    s.record_repo.get_notes_in_range = AsyncMock(return_value={})
    return s


def user(days=2):
    return SimpleNamespace(id="user", created_at=datetime(2026, 9, 16, tzinfo=UTC) - timedelta(days=days))


@pytest.mark.asyncio
async def test_additional_elapsed_seconds_are_never_scored():
    class Query:
        def prefetch_related(self, *args):
            return self

        def __await__(self):
            async def records():
                return [
                    SimpleNamespace(
                        id=name,
                        mission_snapshot={"domain": domain},
                        selection=SimpleNamespace(card_set=SimpleNamespace(service_date=TODAY)),
                    )
                    for name, domain in [("basic", "유산소"), ("meal", "식사수면생활리듬")]
                ]

            return records().__await__()

    s = service()
    with (
        patch.object(module.Challenge, "filter", return_value=Query()),
        patch.object(module.ExerciseMissionSession, "filter") as extra_query,
    ):
        rows = await s._daily_events(user(), TODAY, TODAY)
    assert rows[TODAY] == [mission("basic")]
    extra_query.assert_not_called()  # Neither zero nor positive timer duration is consumed.


@pytest.mark.asyncio
async def test_additional_only_day_preserves_previous_achievement():
    s = service()
    s._daily_events = AsyncMock(return_value={TODAY - timedelta(days=1): [mission("prior")]})
    with patch.object(module, "service_today", return_value=TODAY):
        r = await s.get_practice_score(user())
    expected = trajectory([dict(status="active", events=[mission("prior")])])[-1]
    assert r["daily_units"] == 0
    assert r["cumulative_units"] == 1
    assert r["practice_score"] == expected["practiceScore"] > 0
    assert r["confirmed_rest_run"] == 0
    assert r["ledger_state"] == "loaded"
    assert r["composite_score"] is None
    assert r["composite_blocked_reason"] == "INITIAL_FORMULA_POLICY_PENDING"
    PracticeScoreResponse(**r)


@pytest.mark.asyncio
@pytest.mark.parametrize("failure", ["exists", "records"])
@pytest.mark.parametrize("bridge_down", [False, True])
async def test_unavailable_never_becomes_zero(failure, bridge_down):
    s = service()
    s._daily_events = AsyncMock(side_effect=RuntimeError("database unavailable"))
    if failure == "exists":
        s._has_any_completion.side_effect = RuntimeError("database unavailable")
    if bridge_down:
        s.peer_service.fetch_bridge_result.side_effect = RuntimeError("bridge unavailable")
    with patch.object(module, "service_today", return_value=TODAY):
        r = await s.get_practice_score(user())
    assert r["ledger_state"] == "unavailable"
    assert r["practice_score"] is None
    assert r["daily_units"] is None and r["cumulative_units"] is None
    assert r["freshness"] == "unavailable"
    assert "PRACTICE_LEDGER_UNAVAILABLE" in r["composite_blocked_reason"]
    PracticeScoreResponse(**r)


@pytest.mark.asyncio
async def test_confirmed_empty_requires_new_user():
    s = service()
    s._has_any_completion.return_value = False
    s._daily_events = AsyncMock()
    with patch.object(module, "service_today", return_value=TODAY):
        new = await s.get_practice_score(user(days=0))
        old = await s.get_practice_score(user(days=8))
    assert new["ledger_state"] == "confirmed_empty_new"
    assert new["practice_score"] == 0
    assert old["ledger_state"] == "loaded"
    assert old["freshness"] == "needs_activity_update"
    assert old["confirmed_rest_run"] == 0
    s._daily_events.assert_not_called()


@pytest.mark.asyncio
async def test_achievement_before_730_days_not_dropped():
    s = service()
    first = TODAY - timedelta(days=800)
    s._daily_events = AsyncMock(return_value={first: [mission("old")]})
    with patch.object(module, "service_today", return_value=TODAY):
        r = await s.get_practice_score(user(days=800))
    assert s._daily_events.call_args.args[1] == first
    assert r["cumulative_units"] == 1 and r["practice_score"] > 0


@pytest.mark.asyncio
async def test_revision_distinguishes_same_total_on_different_days():
    s = service()
    s._daily_events = AsyncMock(return_value={TODAY: [mission("same")]})
    with patch.object(module, "service_today", return_value=TODAY):
        a = await s.get_practice_score(user())
        s._daily_events.return_value = {TODAY - timedelta(days=1): [mission("same")]}
        b = await s.get_practice_score(user())
    assert a["cumulative_units"] == b["cumulative_units"]
    assert a["ledger_revision"] != b["ledger_revision"]


def test_activity_precedes_rest_and_missing_is_unknown():
    start = TODAY - timedelta(days=2)
    notes = {start: SimpleNamespace(is_rest_day=True), TODAY: SimpleNamespace(is_rest_day=True)}
    days = module._practice_days(start, TODAY, {TODAY: [mission("done")]}, notes)
    assert [day["status"] for day in days] == ["confirmed_rest", "unknown", "active"]
    assert days[-1]["events"] == [mission("done")]
