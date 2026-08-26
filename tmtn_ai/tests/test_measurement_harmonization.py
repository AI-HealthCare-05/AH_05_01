"""Synthetic tests for deterministic measurement harmonization formulas."""

from __future__ import annotations

import pandas as pd
import pytest

from src.measurement_harmonization import (
    MeasurementHarmonizationError,
    convert_hba1c_to_2019_2021_scale,
    convert_microlife_bp_to_greenlight_scale,
    reproduce_official_final_bp,
)


def test_hba1c_conversion_is_identity_before_2022() -> None:
    raw = pd.Series([6.4, None])
    converted = convert_hba1c_to_2019_2021_scale(raw, year=2021)
    assert converted.iloc[0] == pytest.approx(6.4)
    assert pd.isna(converted.iloc[1])


def test_hba1c_conversion_uses_official_researcher_formula_from_2022() -> None:
    converted = convert_hba1c_to_2019_2021_scale(pd.Series([6.4]), year=2022)
    assert converted.iloc[0] == pytest.approx(1.034 * 6.4 - 0.0448)


def test_bp_conversion_is_identity_before_2021() -> None:
    result = convert_microlife_bp_to_greenlight_scale(
        pd.Series([130.0]), pd.Series([80.0]), pd.Series([50]), year=2020
    )
    assert result.loc[0, "sbp_greenlight_sensitivity"] == 130.0
    assert result.loc[0, "dbp_greenlight_sensitivity"] == 80.0


def test_bp_conversion_uses_raw_pulse_pressure_and_age() -> None:
    result = convert_microlife_bp_to_greenlight_scale(
        pd.Series([130.0]), pd.Series([80.0]), pd.Series([50]), year=2021
    )
    assert result.loc[0, "pulse_pressure_raw"] == 50.0
    assert result.loc[0, "sbp_greenlight_sensitivity"] == pytest.approx(131.653)
    assert result.loc[0, "dbp_greenlight_sensitivity"] == pytest.approx(82.19)


def test_bp_conversion_requires_all_formula_inputs() -> None:
    result = convert_microlife_bp_to_greenlight_scale(
        pd.Series([130.0]), pd.Series([None]), pd.Series([50]), year=2021
    )
    assert pd.isna(result.loc[0, "sbp_greenlight_sensitivity"])
    assert pd.isna(result.loc[0, "dbp_greenlight_sensitivity"])


def test_official_bp_reproduction_uses_rounds_two_and_three() -> None:
    frame = pd.DataFrame(
        {
            "HE_sbp2": [120.0],
            "HE_sbp3": [124.0],
            "HE_dbp2": [78.0],
            "HE_dbp3": [82.0],
        }
    )
    reproduced = reproduce_official_final_bp(frame)
    assert reproduced.loc[0, "HE_sbp_reproduced"] == 122.0
    assert reproduced.loc[0, "HE_dbp_reproduced"] == 80.0


def test_invalid_year_is_rejected() -> None:
    with pytest.raises(MeasurementHarmonizationError, match="year"):
        convert_hba1c_to_2019_2021_scale(pd.Series([6.4]), year=True)
