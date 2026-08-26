"""Contract-driven KNHANES and app activity feature derivation.

This module contains deterministic preprocessing only. It does not read files, split
participants, fit preprocessing parameters, train models, or evaluate performance.
"""

from __future__ import annotations

import math
from numbers import Integral, Real
from typing import Any, Mapping

import pandas as pd


class ActivityFeatureError(ValueError):
    """Raised when activity inputs violate the frozen preprocessing contract."""


ACTIVITY_BRANCHES: Mapping[str, tuple[str, str, str, str]] = {
    "work_vigorous": ("BE3_71", "BE3_72", "BE3_73", "BE3_74"),
    "leisure_vigorous": ("BE3_75", "BE3_76", "BE3_77", "BE3_78"),
    "work_moderate": ("BE3_81", "BE3_82", "BE3_83", "BE3_84"),
    "leisure_moderate": ("BE3_85", "BE3_86", "BE3_87", "BE3_88"),
    "transport_moderate": ("BE3_91", "BE3_92", "BE3_93", "BE3_94"),
}

REQUIRED_KNHANES_COLUMNS = {
    column for columns in ACTIVITY_BRANCHES.values() for column in columns
} | {"BE5_1"}

STRENGTH_RAW_TO_DAYS = {1: 0.0, 2: 1.0, 3: 2.0, 4: 3.0, 5: 4.0, 6: 5.0}


def _numeric(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce")


def _whole_number_between(series: pd.Series, lower: int, upper: int) -> pd.Series:
    numeric = _numeric(series)
    return numeric.between(lower, upper, inclusive="both") & numeric.mod(1).eq(0)


def _weekly_minutes(
    frame: pd.DataFrame, columns: tuple[str, str, str, str]
) -> pd.Series:
    participation_column, days_column, hours_column, minutes_column = columns
    participation = _numeric(frame[participation_column])
    days = _numeric(frame[days_column])
    hours = _numeric(frame[hours_column])
    minutes = _numeric(frame[minutes_column])

    participation_no = participation.eq(2)
    participation_yes_valid = (
        participation.eq(1)
        & _whole_number_between(days, 1, 7)
        & _whole_number_between(hours, 0, 23)
        & _whole_number_between(minutes, 0, 59)
    )

    result = pd.Series(pd.NA, index=frame.index, dtype="Float64")
    result.loc[participation_no] = 0.0
    result.loc[participation_yes_valid] = (
        days.loc[participation_yes_valid]
        * (60.0 * hours.loc[participation_yes_valid] + minutes.loc[participation_yes_valid])
    )
    return result


def _nullable_binary(condition: pd.Series, eligible: pd.Series) -> pd.Series:
    result = pd.Series(pd.NA, index=condition.index, dtype="Int8")
    result.loc[eligible] = condition.loc[eligible].astype("int8")
    return result


def derive_knhanes_activity_features(
    frame: pd.DataFrame,
    *,
    official_pa_aerobic_column: str = "pa_aerobic",
) -> pd.DataFrame:
    """Derive official-reproduction and app-aligned activity features.

    The returned frame contains derived columns only and preserves the input index.
    ``pa_aerobic_reproduced`` uses all five official KNHANES activity branches,
    whereas ``leisure_aerobic_moderate_equivalent_min_week`` uses only leisure
    moderate and vigorous activity. These outputs are intentionally not aliases.
    """

    missing = sorted(REQUIRED_KNHANES_COLUMNS - set(frame.columns))
    if missing:
        raise ActivityFeatureError(f"Required KNHANES activity columns are missing: {missing}")

    output = pd.DataFrame(index=frame.index)
    weekly: dict[str, pd.Series] = {}
    for branch, columns in ACTIVITY_BRANCHES.items():
        value = _weekly_minutes(frame, columns)
        weekly[branch] = value
        output[f"{branch}_min_week"] = value

    output["leisure_activity_complete"] = (
        weekly["leisure_vigorous"].notna() & weekly["leisure_moderate"].notna()
    ).astype("boolean")
    output["total_activity_complete"] = pd.concat(weekly.values(), axis=1).notna().all(axis=1).astype(
        "boolean"
    )

    output["leisure_aerobic_moderate_equivalent_min_week"] = (
        weekly["leisure_moderate"] + 2.0 * weekly["leisure_vigorous"]
    ).astype("Float64")
    leisure_eligible = output["leisure_aerobic_moderate_equivalent_min_week"].notna()
    output["leisure_meets_150_equivalent"] = _nullable_binary(
        output["leisure_aerobic_moderate_equivalent_min_week"].ge(150),
        leisure_eligible,
    )

    total_vigorous = pd.concat(
        [weekly["work_vigorous"], weekly["leisure_vigorous"]], axis=1
    ).sum(axis=1, min_count=2)
    total_moderate = pd.concat(
        [
            weekly["work_moderate"],
            weekly["leisure_moderate"],
            weekly["transport_moderate"],
        ],
        axis=1,
    ).sum(axis=1, min_count=3)
    total_equivalent = total_moderate + 2.0 * total_vigorous
    output["total_vigorous_min_week"] = total_vigorous.astype("Float64")
    output["total_moderate_min_week"] = total_moderate.astype("Float64")
    output["total_moderate_equivalent_min_week"] = total_equivalent.astype("Float64")

    official_eligible = output["total_activity_complete"].fillna(False)
    official_condition = (
        total_moderate.ge(150) | total_vigorous.ge(75) | total_equivalent.ge(150)
    )
    output["pa_aerobic_reproduced"] = _nullable_binary(
        official_condition, official_eligible
    )

    if official_pa_aerobic_column in frame.columns:
        official = _numeric(frame[official_pa_aerobic_column])
        official_valid = official.isin([0, 1])
        output["pa_aerobic_official"] = _nullable_binary(official.eq(1), official_valid)
        comparable = official_valid & output["pa_aerobic_reproduced"].notna()
        match = output["pa_aerobic_reproduced"].eq(output["pa_aerobic_official"])
        match_output = pd.Series(pd.NA, index=frame.index, dtype="boolean")
        match_output.loc[comparable] = match.loc[comparable]
        output["pa_aerobic_match"] = match_output

    strength_raw = _numeric(frame["BE5_1"])
    output["strength_days_week"] = strength_raw.map(STRENGTH_RAW_TO_DAYS).astype("Float64")
    strength_valid = strength_raw.isin(STRENGTH_RAW_TO_DAYS)
    strength_top_coded = pd.Series(pd.NA, index=frame.index, dtype="boolean")
    strength_top_coded.loc[strength_valid] = strength_raw.loc[strength_valid].eq(6)
    output["strength_top_coded"] = strength_top_coded
    output["pa_muscle_reproduced"] = _nullable_binary(
        strength_raw.isin([3, 4, 5, 6]), strength_valid
    )
    return output


def derive_app_leisure_aerobic_feature(
    aerobic_days_week: int,
    aerobic_minutes_per_session: float | None,
    aerobic_typical_intensity: str | None,
) -> float:
    """Collapse app aerobic inputs to moderate-equivalent minutes per week."""

    if isinstance(aerobic_days_week, bool) or not isinstance(aerobic_days_week, Integral):
        raise ActivityFeatureError("aerobic_days_week must be an integer from 0 to 7.")
    days = int(aerobic_days_week)
    if not 0 <= days <= 7:
        raise ActivityFeatureError("aerobic_days_week must be between 0 and 7.")

    if aerobic_minutes_per_session is None:
        minutes: float | None = None
    elif isinstance(aerobic_minutes_per_session, bool) or not isinstance(
        aerobic_minutes_per_session, Real
    ):
        raise ActivityFeatureError("aerobic_minutes_per_session must be numeric.")
    else:
        minutes = float(aerobic_minutes_per_session)
        if not math.isfinite(minutes) or not 0 <= minutes <= 1440:
            raise ActivityFeatureError(
                "aerobic_minutes_per_session must be finite and between 0 and 1440."
            )

    intensity = (
        aerobic_typical_intensity.strip().lower()
        if isinstance(aerobic_typical_intensity, str)
        else aerobic_typical_intensity
    )
    if days == 0:
        if minutes not in (None, 0.0):
            raise ActivityFeatureError("Zero aerobic days requires zero or missing session minutes.")
        if intensity not in (None, "moderate", "vigorous"):
            raise ActivityFeatureError("Unsupported aerobic_typical_intensity.")
        return 0.0

    if minutes is None or minutes <= 0:
        raise ActivityFeatureError("Positive aerobic days requires positive session minutes.")
    if intensity not in {"moderate", "vigorous"}:
        raise ActivityFeatureError(
            "Positive aerobic days requires moderate or vigorous typical intensity."
        )
    intensity_weight = 1.0 if intensity == "moderate" else 2.0
    return float(days * minutes * intensity_weight)


def derive_app_strength_feature(strength_frequency: int | str) -> dict[str, Any]:
    """Normalize the app's 0/1/2/3/4/5+ strength-frequency answer."""

    if isinstance(strength_frequency, str):
        normalized = strength_frequency.strip().lower().replace(" ", "_")
        if normalized not in {"5+", "5_plus", "5_or_more"}:
            raise ActivityFeatureError("Unsupported strength_frequency category.")
        return {"strength_days_week": 5.0, "strength_top_coded": True}
    if isinstance(strength_frequency, bool) or not isinstance(strength_frequency, Integral):
        raise ActivityFeatureError("strength_frequency must be 0 to 4 or a 5+ category.")
    days = int(strength_frequency)
    if not 0 <= days <= 4:
        raise ActivityFeatureError("strength_frequency must be 0 to 4 or a 5+ category.")
    return {"strength_days_week": float(days), "strength_top_coded": False}
