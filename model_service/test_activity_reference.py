import json
from copy import deepcopy
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.dtos.personal_xai import ActivityComparison, PersonalXaiSnapshot
from model_service import activity_reference as reference
from model_service.build_activity_reference import AEROBIC, STRENGTH, features, survey_summary, weekly_minutes


def inputs():
    return {"age_years": 35, "sex_code": 1, AEROBIC: 120, STRENGTH: 4}


def test_pinned_survey_reference_has_valid_metric_specific_samples_and_uncertainty():
    package = reference.load_reference()
    assert package["adult_source_rows"] == 18691
    assert package["crosschecks"]["official_aerobic_compared"] == 16632
    assert package["crosschecks"]["official_aerobic_mismatch"] == 0
    for group in package["groups"].values():
        for metric in group["metrics"].values():
            assert metric["status"] == "ready"
            assert 30 <= metric["n"] <= group["n"]
            assert metric["ci95_normal"][0] <= metric["mean"] <= metric["ci95_normal"][1]
    group = package["groups"]["40-64:2"]["metrics"]
    assert group[AEROBIC]["n"] != group[STRENGTH]["n"]


def test_nonparticipation_is_zero_but_unknown_and_partial_activity_are_missing():
    columns = ("yes", "days", "hours", "minutes")
    assert weekly_minutes({"yes": "2", "days": "8", "hours": "88", "minutes": "88"}, columns) == 0
    assert weekly_minutes({"yes": "9"}, columns) is None
    assert weekly_minutes({"yes": "1", "days": "3", "hours": "1", "minutes": "15"}, columns) == 225
    assert weekly_minutes({"yes": "1", "days": "3", "hours": "1", "minutes": "99"}, columns) is None
    assert weekly_minutes({"yes": "1", "days": "1.5", "hours": "1", "minutes": "0"}, columns) is None
    for code, expected in [("1", 0), ("6", 5), ("8", None), ("9", None), ("", None)]:
        assert features({"BE5_1": code})[0][STRENGTH] == expected


def test_taylor_variance_keeps_psus_outside_domain_and_does_not_use_naive_individual_se():
    design = {"a": {1, 2}, "b": {1, 2}}
    rows = [
        {"group": "g", "weight": 1, "design": (s, p), AEROBIC: y}
        for s, p, y in [("a", 1, 1), ("a", 2, 2), ("b", 1, 3), ("b", 2, 4)]
    ]
    result = survey_summary(rows, design, "g", AEROBIC)
    assert result["mean"] == 2.5
    assert result["standard_error"] ** 2 == pytest.approx(0.125)
    assert result["status"] == "unstable_estimate"
    rows[0]["group"] = "outside"
    result = survey_summary(rows, design, "g", AEROBIC)
    assert result["mean"] == 3
    assert result["standard_error"] ** 2 == pytest.approx(2 / 9)


def test_user_strength_topcode_does_not_claim_exact_five_days_or_compare_six_as_six():
    for days in (5, 6, 7):
        result = reference.build_comparison(
            {**inputs(), STRENGTH: days}, input_revision="one", reference_date="2026-09-15"
        )
        card = result["cards"][1]
        assert card["value"] == 5 and card["topcoded"]
        assert "5일 이상" in card["text"]
        assert "평균보다" not in card["comparison_text"]
    result = reference.build_comparison({**inputs(), STRENGTH: None}, input_revision="one", reference_date="2026-09-15")
    assert len(result["cards"]) == 1


def test_activity_is_bound_to_same_personal_snapshot_and_group():
    path = Path(__file__).resolve().parents[1] / "app/tests/fixtures/personal_xai.synthetic.json"
    snapshot = json.loads(path.read_text(encoding="utf-8"))
    snapshot["activity_comparison"] = reference.build_comparison(
        inputs(), input_revision=snapshot["input_revision"], reference_date=snapshot["reference_date"]
    )
    PersonalXaiSnapshot.model_validate(snapshot)
    for field, value in [("input_revision", "other-user"), ("reference_date", "2000-01-01"), ("group_key", "65+:2")]:
        changed = deepcopy(snapshot)
        changed["activity_comparison"][field] = value
        with pytest.raises(ValidationError):
            PersonalXaiSnapshot.model_validate(changed)
    changed = deepcopy(snapshot)
    changed["activity_comparison"]["cards"][0]["delta"] += 1
    with pytest.raises(ValidationError):
        PersonalXaiSnapshot.model_validate(changed)


def test_reference_corruption_fails_closed(tmp_path, monkeypatch):
    path = tmp_path / "bad.json"
    path.write_bytes(reference.REFERENCE_PATH.read_bytes() + b" ")
    reference.load_reference.cache_clear()
    monkeypatch.setattr(reference, "REFERENCE_PATH", path)
    try:
        with pytest.raises(ValueError, match="MISMATCH"):
            reference.load_reference()
    finally:
        reference.load_reference.cache_clear()


@pytest.mark.parametrize("field,value", [("mean_display", "999"), ("value_display", "999"), ("unit", "일")])
def test_display_cannot_disagree_with_actual_statistic(field, value):
    activity = reference.build_comparison(inputs(), input_revision="one", reference_date="2026-09-15")
    activity["cards"][0][field] = value
    with pytest.raises(ValidationError):
        ActivityComparison.model_validate(activity)


@pytest.mark.parametrize("feature", [AEROBIC, STRENGTH])
@pytest.mark.parametrize("value", [0, 1, 4, 5])
def test_hint_is_survey_bound_and_not_a_peer_deficit_goal(feature, value):
    packet = reference.build_comparison({**inputs(), feature: value}, input_revision="one", reference_date="2026-09-16")
    card = next(c for c in packet["cards"] if c["key"] == feature)
    hint = card["coaching_hint"]
    assert hint["basis"] == ("survey_zero" if value == 0 else "survey_reported")
    assert not any(s in hint["text"] for s in ("부족", "목표", "완료했", "점수", "혈압", "혈당"))
    card["coaching_hint"]["text"] = "설문만 보고 오늘 미션 완료했다고 꾸민 문장"
    validated = ActivityComparison.model_validate(packet)
    actual = next(c for c in validated.cards if c.key == feature)
    assert "꾸민" not in actual.coaching_hint.text
    assert actual.value == value


def test_same_survey_input_gets_same_hint_across_different_peer_averages():
    first = reference.build_comparison(inputs(), input_revision="one", reference_date="2026-09-16")
    other = reference.build_comparison(
        {**inputs(), "age_years": 70, "sex_code": 2}, input_revision="two", reference_date="2026-09-16"
    )
    assert first["cards"][0]["mean"] != other["cards"][0]["mean"]
    assert [c["coaching_hint"] for c in first["cards"]] == [c["coaching_hint"] for c in other["cards"]]
