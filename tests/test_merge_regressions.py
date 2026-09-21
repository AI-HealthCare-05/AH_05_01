"""두 브랜치의 기능을 합칠 때 생겼던 라우트 중복과 입력 계약 회귀를 막는다."""

from collections import Counter
from datetime import date
from types import SimpleNamespace

from fastapi.routing import APIRoute

from app.main import app
from app.services.personal_xai_service import model_request


def test_api_method_and_path_are_registered_once():
    routes = Counter(
        (method, route.path) for route in app.routes if isinstance(route, APIRoute) for method in route.methods
    )
    assert all(count == 1 for count in routes.values())
    for method, path in (
        ("GET", "/api/v1/companion/first-repair"),
        ("POST", "/api/v1/companion/first-repair/gift"),
        ("POST", "/api/v1/companion/first-repair/complete"),
        ("GET", "/api/v1/tuntun-score/personal"),
        ("GET", "/api/v1/tuntun-score/personal/history"),
        ("GET", "/api/v1/tuntun-score/editorial"),
    ):
        assert routes[method, path] == 1


def test_legacy_strength_frequency_is_not_assumed_to_mean_days():
    user = SimpleNamespace(id=7, birth_year=1991, birth_month=1, gender="MALE", is_pregnant=None)
    health = SimpleNamespace(id="health-1", input_values={"height_cm": 170, "weight_kg": 64})
    habit = SimpleNamespace(
        id="habit-1",
        strength_weekly_count=3,
        strength_intensity="MODERATE",
        aerobic_low_minutes=30,
        aerobic_moderate_minutes=50,
        aerobic_high_minutes=0,
    )
    request = model_request(user, health, habit, date(2026, 9, 21))
    assert request["strengthFrequencyUnit"] is None
    habit.strength_frequency_unit = "days"
    confirmed = model_request(user, health, habit, date(2026, 9, 21))
    assert confirmed["strengthFrequencyUnit"] == "days"
    assert request["inputRevision"] != confirmed["inputRevision"]
