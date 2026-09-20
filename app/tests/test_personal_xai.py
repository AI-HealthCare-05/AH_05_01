import asyncio
import json
from copy import deepcopy
from datetime import UTC, date, datetime
from pathlib import Path
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.dtos.exercise_habits import ExerciseHabitsRequest
from app.dtos.personal_xai import PersonalXaiResponse, PersonalXaiSnapshot
from app.services.personal_xai_service import PersonalJobs, PersonalXaiService, immediate_activity, model_request


@pytest.fixture(scope="session", autouse=True)
def initialize():
    yield


def snapshot():
    return json.loads((Path(__file__).parent / "fixtures/personal_xai.synthetic.json").read_text(encoding="utf-8"))


def user(user_id=1):
    return SimpleNamespace(id=user_id, birth_year=1991, birth_month=1, gender="MALE", is_pregnant=None)


def habit(unit="days"):
    return SimpleNamespace(
        id="habit-one",
        strength_weekly_count=4,
        strength_frequency_unit=unit,
        strength_intensity="MODERATE",
        aerobic_low_minutes=60,
        aerobic_moderate_minutes=120,
        aerobic_high_minutes=0,
    )


def health():
    return SimpleNamespace(id="health-one", input_values={"height_cm": 170, "weight_kg": 64})


def test_real_calculation_fixture_validates_and_preserves_all_contributions():
    result = PersonalXaiSnapshot.model_validate(snapshot())
    assert len(result.domains) == 2
    assert all(len(domain.contributions) == 6 for domain in result.domains)


@pytest.mark.parametrize(
    "mutation",
    ["target", "unit", "sum", "missing_feature", "order", "background", "release", "nan", "mock", "domain", "rank"],
)
def test_mismatched_or_fabricated_explanations_are_rejected(mutation):  # noqa: C901 - independent corruptions of one real fixture
    value = snapshot()
    domain = value["domains"][0]
    if mutation == "target":
        domain["output_target"] = "peer_rank"
    if mutation == "unit":
        domain["unit"] = "probability"
    if mutation == "sum":
        domain["contributions"][0]["value"] += 1
    if mutation == "missing_feature":
        domain["contributions"].pop()
    if mutation == "order":
        domain["contributions"].reverse()
    if mutation == "background":
        domain["background_sha256"] = "0" * 64
    if mutation == "release":
        value["release_sha256"] = "0" * 64
    if mutation == "nan":
        domain["contributions"][0]["value"] = float("nan")
    if mutation == "mock":
        value["is_mock"] = True
    if mutation == "domain":
        domain["domain"] = "physical"
    if mutation == "rank":
        domain["rank"] = 101
    with pytest.raises(ValidationError):
        PersonalXaiSnapshot.model_validate(value)


def test_input_binding_changes_with_user_saved_snapshot_and_input():
    args = (user(), health(), habit(), date(2026, 9, 15))
    first = model_request(*args)
    assert first == model_request(*args)
    assert first["inputRevision"] != model_request(user(2), *args[1:])["inputRevision"]
    changed = deepcopy(args[1])
    changed.input_values["weight_kg"] = 99
    changed.id = "health-new"
    assert first["inputRevision"] != model_request(args[0], changed, *args[2:])["inputRevision"]
    assert model_request(user(), health(), habit(None), args[-1])["strengthFrequencyUnit"] is None


def test_legacy_exercise_unit_stays_unknown_and_new_days_are_explicit():
    fields = {"strength_weekly_count": 4, "strength_intensity": "MODERATE"}
    assert ExerciseHabitsRequest(**fields).strength_frequency_unit is None
    assert ExerciseHabitsRequest(**fields, strength_frequency_unit="days").strength_frequency_unit == "days"
    with pytest.raises(ValidationError):
        ExerciseHabitsRequest(**fields, strength_frequency_unit="minutes")


@pytest.mark.asyncio
async def test_jobs_are_deduplicated_and_never_shared_across_users_or_revisions():
    jobs = PersonalJobs()
    calls = []

    async def calculate(request):
        calls.append(request["inputRevision"])
        return PersonalXaiResponse(status="unavailable", reason="test_only")

    jobs.calculate = calculate
    request = {"inputRevision": "one"}
    assert jobs.poll(1, request).status == "pending"
    assert jobs.poll(1, request).status == "pending"
    jobs.poll(2, request)
    jobs.poll(1, {"inputRevision": "two"})
    await asyncio.sleep(0)
    assert calls == ["one", "one", "two"]
    jobs.forget(1)
    assert list(jobs.jobs) == [(2, "one")]


class Repo:
    def __init__(self, result):
        self.result = result

    async def get_latest(self, _):
        return self.result

    async def get_latest_by_purpose(self, _, purpose):
        return self.result


@pytest.mark.asyncio
async def test_old_units_and_withdrawn_consent_cannot_start_personal_jobs(monkeypatch):
    monkeypatch.setattr("app.services.personal_xai_service.config.ENV", "local")
    monkeypatch.setattr("app.services.personal_xai_service.config.TUNTUN_XAI_URL", "http://127.0.0.1:8776")
    monkeypatch.setattr("app.services.personal_xai_service.config.TUNTUN_XAI_TOKEN", "test-only-token")
    service = PersonalXaiService()
    service.health_repo, service.habit_repo = Repo(health()), Repo(habit(None))
    service.consent_repo = Repo(SimpleNamespace(status="AGREED"))
    assert (await service.get_personal(user())).reason == "strength_days_unconfirmed"
    service.consent_repo = Repo(SimpleNamespace(status="WITHDRAWN"))
    assert (await service.get_personal(user())).reason == "consent_required"


def test_immediate_comparison_preserves_survey_date_and_refuses_ambiguous_age_band():
    person, survey, today = user(), habit(), date(2026, 9, 15)
    survey.recorded_at = datetime(2026, 8, 1, tzinfo=UTC)
    request = model_request(person, health(), survey, today)
    comparison = immediate_activity(person, survey, request, today)
    assert comparison.input_revision == request["inputRevision"]
    assert comparison.survey_recorded_at == survey.recorded_at
    assert comparison.cards[0].mean == pytest.approx(155.33183148636854)
    person.birth_year, person.birth_month = 1986, 9  # Could still be 39 or already 40.
    assert immediate_activity(person, survey, model_request(person, health(), survey, today), today) is None


@pytest.mark.asyncio
async def test_activity_arrives_before_shap_and_is_removed_when_consent_is_withdrawn(monkeypatch):
    monkeypatch.setattr("app.services.personal_xai_service.config.ENV", "local")
    monkeypatch.setattr("app.services.personal_xai_service.config.TUNTUN_XAI_URL", "http://127.0.0.1:8776")
    monkeypatch.setattr("app.services.personal_xai_service.config.TUNTUN_XAI_TOKEN", "test-only-token")
    monkeypatch.setattr(
        "app.services.personal_xai_service.personal_jobs.poll",
        lambda *args: PersonalXaiResponse(status="pending", retry_after_seconds=3),
    )
    service = PersonalXaiService()
    service.health_repo, service.habit_repo = Repo(health()), Repo(habit())
    service.consent_repo = Repo(SimpleNamespace(status="AGREED"))
    response = await service.get_personal(user())
    assert response.status == "pending" and response.snapshot is None
    assert response.activity_comparison.group_key == "19-39:1"
    service.consent_repo = Repo(SimpleNamespace(status="WITHDRAWN"))
    denied = await service.get_personal(user())
    assert denied.reason == "consent_required" and denied.activity_comparison is None
