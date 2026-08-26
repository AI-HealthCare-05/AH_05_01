"""Deterministic KNHANES measurement harmonization candidates.

The functions in this module do not read files, mutate raw columns, fit parameters,
train models, or select a harmonization policy from observed performance.
"""

from __future__ import annotations

from numbers import Integral

import pandas as pd


class MeasurementHarmonizationError(ValueError):
    """Raised when a requested transformation violates the frozen contract."""


HBA1C_CONVERSION_START_YEAR = 2022
BP_CONVERSION_START_YEAR = 2021


def _validate_year(year: int) -> int:
    if isinstance(year, bool) or not isinstance(year, Integral):
        raise MeasurementHarmonizationError("year must be an integer.")
    normalized = int(year)
    if normalized < 1900 or normalized > 2100:
        raise MeasurementHarmonizationError("year is outside the supported calendar range.")
    return normalized


def _numeric(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce").astype("Float64")


def convert_hba1c_to_2019_2021_scale(raw_hba1c: pd.Series, *, year: int) -> pd.Series:
    """Return the official researcher conversion candidate without clipping.

    Values before 2022 are returned unchanged. For 2022 onward the KNHANES
    researcher formula ``1.034 * x - 0.0448`` is applied.
    """

    normalized_year = _validate_year(year)
    raw = _numeric(raw_hba1c)
    if normalized_year < HBA1C_CONVERSION_START_YEAR:
        return raw.copy()
    return (1.034 * raw - 0.0448).astype("Float64")


def convert_microlife_bp_to_greenlight_scale(
    raw_sbp: pd.Series,
    raw_dbp: pd.Series,
    age_years: pd.Series,
    *,
    year: int,
) -> pd.DataFrame:
    """Return adult Microlife-to-Greenlight sensitivity values.

    Values before 2021 are unchanged. Pulse pressure is computed from the raw
    official SBP and DBP. Converted values are never rounded or clipped here.
    """

    normalized_year = _validate_year(year)
    sbp = _numeric(raw_sbp)
    dbp = _numeric(raw_dbp)
    age = _numeric(age_years)
    result = pd.DataFrame(index=sbp.index)
    result["pulse_pressure_raw"] = (sbp - dbp).astype("Float64")
    if normalized_year < BP_CONVERSION_START_YEAR:
        result["sbp_greenlight_sensitivity"] = sbp.copy()
        result["dbp_greenlight_sensitivity"] = dbp.copy()
        return result

    complete = sbp.notna() & dbp.notna() & age.notna()
    converted_sbp = pd.Series(pd.NA, index=sbp.index, dtype="Float64")
    converted_dbp = pd.Series(pd.NA, index=sbp.index, dtype="Float64")
    pulse_pressure = result["pulse_pressure_raw"]
    converted_sbp.loc[complete] = (
        10.773
        + 0.771 * sbp.loc[complete]
        + 0.039 * age.loc[complete]
        + 0.374 * pulse_pressure.loc[complete]
    )
    converted_dbp.loc[complete] = (
        13.480
        + 0.952 * dbp.loc[complete]
        - 0.051 * age.loc[complete]
        - 0.098 * pulse_pressure.loc[complete]
    )
    result["sbp_greenlight_sensitivity"] = converted_sbp
    result["dbp_greenlight_sensitivity"] = converted_dbp
    return result


def reproduce_official_final_bp(frame: pd.DataFrame) -> pd.DataFrame:
    """Reproduce KNHANES final BP as the mean of rounds 2 and 3."""

    required = {"HE_sbp2", "HE_sbp3", "HE_dbp2", "HE_dbp3"}
    missing = sorted(required - set(frame.columns))
    if missing:
        raise MeasurementHarmonizationError(
            f"Required blood-pressure round columns are missing: {missing}"
        )
    result = pd.DataFrame(index=frame.index)
    result["HE_sbp_reproduced"] = (
        (_numeric(frame["HE_sbp2"]) + _numeric(frame["HE_sbp3"])) / 2.0
    ).astype("Float64")
    result["HE_dbp_reproduced"] = (
        (_numeric(frame["HE_dbp2"]) + _numeric(frame["HE_dbp3"])) / 2.0
    ).astype("Float64")
    return result
