"""Synthetic-only tests for the activity feature contract."""

from __future__ import annotations

import pandas as pd
import pytest

from src.activity_features import (
    ACTIVITY_BRANCHES,
    ActivityFeatureError,
    derive_app_leisure_aerobic_feature,
    derive_app_strength_feature,
    derive_knhanes_activity_features,
)


def _all_no_frame(rows: int) -> pd.DataFrame:
    payload: dict[str, list[float | int]] = {}
    for participation, days, hours, minutes in ACTIVITY_BRANCHES.values():
        payload[participation] = [2] * rows
        payload[days] = [8] * rows
        payload[hours] = [88] * rows
        payload[minutes] = [88] * rows
    payload["BE5_1"] = [1] * rows
    payload["pa_aerobic"] = [0] * rows
    return pd.DataFrame(payload)


def _set_branch(
    frame: pd.DataFrame,
    row: int,
    branch: str,
    *,
    participation: int,
    days: int,
    hours: int,
    minutes: int,
) -> None:
    columns = ACTIVITY_BRANCHES[branch]
    frame.loc[row, list(columns)] = [participation, days, hours, minutes]


def test_official_thresholds_and_scope_separation() -> None:
    frame = _all_no_frame(6)
    _set_branch(frame, 1, "work_moderate", participation=1, days=5, hours=1, minutes=0)
    _set_branch(frame, 2, "leisure_vigorous", participation=1, days=3, hours=0, minutes=25)
    _set_branch(frame, 3, "leisure_moderate", participation=1, days=5, hours=0, minutes=30)
    _set_branch(frame, 4, "work_vigorous", participation=1, days=1, hours=0, minutes=20)
    _set_branch(frame, 4, "transport_moderate", participation=1, days=1, hours=1, minutes=50)
    _set_branch(frame, 5, "leisure_moderate", participation=1, days=1, hours=2, minutes=29)
    frame["pa_aerobic"] = [0, 1, 1, 1, 1, 0]

    result = derive_knhanes_activity_features(frame)

    assert result["pa_aerobic_reproduced"].tolist() == [0, 1, 1, 1, 1, 0]
    assert result["leisure_aerobic_moderate_equivalent_min_week"].tolist() == [
        0.0,
        0.0,
        150.0,
        150.0,
        0.0,
        149.0,
    ]
    assert result["pa_aerobic_match"].tolist() == [True] * 6
    assert result.loc[1, "total_moderate_min_week"] == 300.0
    assert result.loc[4, "total_moderate_equivalent_min_week"] == 150.0


def test_official_reproduction_requires_all_five_evaluable_branches() -> None:
    frame = _all_no_frame(2)
    _set_branch(frame, 0, "work_vigorous", participation=1, days=9, hours=0, minutes=30)
    _set_branch(frame, 1, "leisure_moderate", participation=9, days=9, hours=99, minutes=99)
    frame["pa_aerobic"] = [1, None]

    result = derive_knhanes_activity_features(frame)

    assert pd.isna(result.loc[0, "pa_aerobic_reproduced"])
    assert pd.isna(result.loc[0, "pa_aerobic_match"])
    assert result.loc[0, "leisure_aerobic_moderate_equivalent_min_week"] == 0.0
    assert pd.isna(result.loc[1, "leisure_aerobic_moderate_equivalent_min_week"])
    assert pd.isna(result.loc[1, "pa_aerobic_reproduced"])


def test_participation_no_is_zero_but_special_participation_is_missing() -> None:
    frame = _all_no_frame(3)
    _set_branch(frame, 1, "leisure_vigorous", participation=8, days=8, hours=88, minutes=88)
    _set_branch(frame, 2, "leisure_vigorous", participation=9, days=9, hours=99, minutes=99)

    result = derive_knhanes_activity_features(frame)

    assert result.loc[0, "leisure_vigorous_min_week"] == 0.0
    assert pd.isna(result.loc[1, "leisure_vigorous_min_week"])
    assert pd.isna(result.loc[2, "leisure_vigorous_min_week"])


def test_mixed_leisure_intensity_is_combined_exactly() -> None:
    frame = _all_no_frame(1)
    _set_branch(frame, 0, "leisure_moderate", participation=1, days=2, hours=0, minutes=30)
    _set_branch(frame, 0, "leisure_vigorous", participation=1, days=1, hours=0, minutes=45)

    result = derive_knhanes_activity_features(frame)

    assert result.loc[0, "leisure_moderate_min_week"] == 60.0
    assert result.loc[0, "leisure_vigorous_min_week"] == 45.0
    assert result.loc[0, "leisure_aerobic_moderate_equivalent_min_week"] == 150.0
    assert result.loc[0, "leisure_meets_150_equivalent"] == 1


def test_strength_mapping_and_official_two_day_threshold() -> None:
    frame = _all_no_frame(9)
    frame["BE5_1"] = [1, 2, 3, 4, 5, 6, 8, 9, None]

    result = derive_knhanes_activity_features(frame)

    assert result["strength_days_week"].tolist()[:6] == [0.0, 1.0, 2.0, 3.0, 4.0, 5.0]
    assert all(pd.isna(value) for value in result["strength_days_week"].tolist()[6:])
    assert result["pa_muscle_reproduced"].tolist()[:6] == [0, 0, 1, 1, 1, 1]
    assert result["strength_top_coded"].tolist()[:6] == [False, False, False, False, False, True]


def test_app_leisure_feature_uses_typical_intensity_weight() -> None:
    assert derive_app_leisure_aerobic_feature(3, 40, "moderate") == 120.0
    assert derive_app_leisure_aerobic_feature(3, 40, "vigorous") == 240.0
    assert derive_app_leisure_aerobic_feature(0, None, None) == 0.0
    assert derive_app_leisure_aerobic_feature(0, 0, "moderate") == 0.0


@pytest.mark.parametrize(
    ("days", "minutes", "intensity"),
    [
        (True, 30, "moderate"),
        (8, 30, "moderate"),
        (2, None, "moderate"),
        (2, 0, "moderate"),
        (2, 30, None),
        (2, 30, "light"),
        (0, 30, None),
        (2, float("nan"), "moderate"),
    ],
)
def test_app_leisure_feature_rejects_inconsistent_inputs(
    days: object, minutes: object, intensity: object
) -> None:
    with pytest.raises(ActivityFeatureError):
        derive_app_leisure_aerobic_feature(days, minutes, intensity)  # type: ignore[arg-type]


def test_app_strength_normalization() -> None:
    assert derive_app_strength_feature(0) == {
        "strength_days_week": 0.0,
        "strength_top_coded": False,
    }
    assert derive_app_strength_feature(4)["strength_days_week"] == 4.0
    assert derive_app_strength_feature("5+") == {
        "strength_days_week": 5.0,
        "strength_top_coded": True,
    }
    with pytest.raises(ActivityFeatureError):
        derive_app_strength_feature(5)


def test_missing_columns_fail_without_mutating_input() -> None:
    frame = _all_no_frame(2)
    original = frame.copy(deep=True)
    derive_knhanes_activity_features(frame)
    pd.testing.assert_frame_equal(frame, original)

    with pytest.raises(ActivityFeatureError, match="missing"):
        derive_knhanes_activity_features(frame.drop(columns=["BE3_91"]))
