from types import SimpleNamespace

import pytest
from httpx import ASGITransport, AsyncClient

from app.dependencies.security import get_request_user
from app.main import app
from app.services.tuntun_score_service import TuntunScoreService


@pytest.fixture(scope="session", autouse=True)
def initialize():
    """These are pure service tests and deliberately do not initialize MySQL."""

    yield


class _Repo:
    def __init__(self, value):
        self.value = value

    async def get_latest(self, _user_id):
        return self.value


class _RecordRepo:
    def __init__(self, recorded_days=0):
        self.card_sets = [
            SimpleNamespace(selection=SimpleNamespace(challenge=SimpleNamespace(state="COMPLETED")))
            for _ in range(recorded_days)
        ]

    async def get_card_sets_in_range(self, _user_id, _start, _end):
        return self.card_sets


def _user(*, complete=True, older=False):
    return SimpleNamespace(
        id=1,
        birth_year=1950 if older else (1990 if complete else None),
        birth_month=1 if complete else None,
        gender="FEMALE" if complete else None,
    )


def _health(*, valid=True):
    values = {"height_cm": 165, "weight_kg": 60} if valid else {"height_cm": None, "weight_kg": 60}
    return SimpleNamespace(input_values=values)


def _habit(*, moderate=90, vigorous=30, strength=1):
    return SimpleNamespace(
        aerobic_moderate_minutes=moderate,
        aerobic_high_minutes=vigorous,
        strength_weekly_count=strength,
    )


def _service(*, health=None, habit=None, recorded_days=0):
    service = TuntunScoreService()
    service.health_repo = _Repo(health)
    service.exercise_repo = _Repo(habit)
    service.record_repo = _RecordRepo(recorded_days)
    return service


@pytest.mark.asyncio
async def test_v2_full_mock_has_four_components_and_approved_lifestyle_formula():
    result = await _service(health=_health(), habit=_habit(), recorded_days=3).get_score_v2(_user())

    assert result.physical_score == 74.0
    assert result.diabetes_score == 78.0
    assert result.hypertension_score == 76.0
    assert result.aerobic_score == 100.0  # (90 + 2*30) / 150
    assert result.strength_score == 50.0  # 1 / 2
    assert result.lifestyle_score == 75.0
    assert result.tuntun_index == 75.75
    assert result.available_component_count == 4
    assert result.is_partial_score is False
    assert result.recorded_days == 3


@pytest.mark.asyncio
async def test_v2_reweights_composite_over_only_available_lifestyle():
    result = await _service(habit=_habit()).get_score_v2(_user(complete=False))

    assert result.tuntun_index == 75.0
    assert result.available_components == ["lifestyle"]
    assert result.unavailable_components == ["physical", "diabetes", "hypertension"]
    assert result.available_component_count == 1
    assert result.is_partial_score is True
    assert result.score_available is True


@pytest.mark.asyncio
async def test_v2_reweights_composite_over_only_available_health_components():
    result = await _service(health=_health()).get_score_v2(_user())

    assert result.tuntun_index == 76.0
    assert result.available_component_count == 3
    assert result.unavailable_components == ["lifestyle"]
    assert result.is_partial_score is True


@pytest.mark.asyncio
async def test_v2_has_no_score_when_every_component_is_unavailable():
    result = await _service().get_score_v2(_user(complete=False))

    assert result.tuntun_index is None
    assert result.available_component_count == 0
    assert result.score_available is False
    assert result.is_partial_score is False
    assert all(component.score is None for component in result.component_scores)


@pytest.mark.asyncio
async def test_v2_invalid_health_input_does_not_create_mock_health_scores():
    result = await _service(health=_health(valid=False), habit=_habit()).get_score_v2(_user())

    assert result.physical_score is None
    assert result.diabetes_score is None
    assert result.hypertension_score is None
    assert result.available_components == ["lifestyle"]


@pytest.mark.asyncio
async def test_v2_invalid_lifestyle_subcomponent_is_excluded_not_clamped():
    result = await _service(health=_health(), habit=_habit(moderate=-1, vigorous=30, strength=8)).get_score_v2(_user())

    assert result.aerobic_score is None
    assert result.strength_score is None
    assert result.lifestyle_score is None
    assert result.lifestyle_available_subcomponent_count == 0


@pytest.mark.asyncio
async def test_v2_one_valid_lifestyle_subcomponent_is_used_directly():
    result = await _service(habit=_habit(moderate=-1, vigorous=30, strength=2)).get_score_v2(_user(complete=False))

    assert result.aerobic_score is None
    assert result.strength_score == 100.0
    assert result.lifestyle_score == 100.0
    assert result.lifestyle_available_subcomponent_count == 1


@pytest.mark.asyncio
async def test_v2_serializes_camel_case_without_internal_probabilities_or_ids():
    result = await _service(health=_health(), habit=_habit()).get_score_v2(_user())
    payload = result.model_dump(by_alias=True)

    assert payload["tuntunIndex"] == 75.75
    assert payload["componentScores"][0]["key"] == "physical"
    serialized_keys = str(payload).lower()
    assert "probability" not in serialized_keys
    assert "participant" not in serialized_keys
    assert "outer_fold" not in serialized_keys
    assert "oof_seed" not in serialized_keys


@pytest.mark.asyncio
async def test_v2_always_discloses_mock_and_non_diagnostic_notice():
    result = await _service(health=_health()).get_score_v2(_user())

    assert result.is_mock is True
    assert result.model_version == "mock-ui-integration-v0.1"
    assert "진단이나 치료를 대신하지 않습니다" in result.notice


@pytest.mark.asyncio
async def test_v2_shows_older_adult_notice_at_age_65_or_more():
    result = await _service(health=_health()).get_score_v2(_user(older=True))

    assert result.older_adult_notice is not None
    assert "65세 이상" in result.older_adult_notice


@pytest.mark.asyncio
async def test_v2_does_not_mix_pending_mission_data_into_lifestyle_score():
    zero_records = await _service(habit=_habit(), recorded_days=0).get_score_v2(_user(complete=False))
    seven_records = await _service(habit=_habit(), recorded_days=7).get_score_v2(_user(complete=False))

    assert zero_records.lifestyle_score == seven_records.lifestyle_score == 75.0
    assert seven_records.mission_integration_status == "pending_evidence"


@pytest.mark.asyncio
async def test_v2_component_order_and_top_level_scores_are_consistent():
    result = await _service(health=_health(), habit=_habit()).get_score_v2(_user())

    assert [component.key for component in result.component_scores] == [
        "physical",
        "diabetes",
        "hypertension",
        "lifestyle",
    ]
    assert [component.score for component in result.component_scores] == [
        result.physical_score,
        result.diabetes_score,
        result.hypertension_score,
        result.lifestyle_score,
    ]


def test_v2_route_and_camel_case_contract_are_in_openapi():
    schema = app.openapi()
    assert "/api/v1/tuntun-score/v2" in schema["paths"]
    properties = schema["components"]["schemas"]["TuntunScoreV2Response"]["properties"]
    assert "tuntunIndex" in properties
    assert "physicalScore" in properties
    assert "componentScores" in properties
    assert "isMock" in properties
    assert "abdominal_obesity_probability_internal" not in properties


@pytest.mark.asyncio
async def test_v2_zero_activity_is_measured_and_available():
    result = await _service(habit=_habit(moderate=0, vigorous=0, strength=0)).get_score_v2(_user(complete=False))

    assert result.aerobic_score == 0.0
    assert result.strength_score == 0.0
    assert result.lifestyle_score == 0.0
    assert result.score_available is True
    assert result.available_components == ["lifestyle"]


@pytest.mark.asyncio
async def test_v2_http_response_uses_camel_case_and_never_exposes_probabilities():
    service = _service(health=_health(), habit=_habit())

    async def fake_user():
        return _user()

    app.dependency_overrides[get_request_user] = fake_user
    app.dependency_overrides[TuntunScoreService] = lambda: service
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/tuntun-score/v2")
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    payload = response.json()
    assert payload["tuntunIndex"] == 75.75
    assert payload["isMock"] is True
    assert "physical_score" not in payload
    assert "probability" not in response.text.lower()
