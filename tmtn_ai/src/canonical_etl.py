"""Deterministic row-preserving canonical ETL for KNHANES P0 data.

This module performs contract transformations only. It does not read or write files,
split data, fit preprocessing parameters, impute values, train models, or evaluate
performance.
"""

from __future__ import annotations

from numbers import Integral
from typing import Any

import numpy as np
import pandas as pd

from .activity_features import REQUIRED_KNHANES_COLUMNS, derive_knhanes_activity_features
from .measurement_harmonization import (
    convert_hba1c_to_2019_2021_scale,
    convert_microlife_bp_to_greenlight_scale,
)


CANONICAL_CONTRACT_VERSION = "v0.1"
ALLOWED_YEARS = {2019, 2020, 2021, 2022}
MODEL_INPUT_ALLOWLIST = {
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
    "pa_aerobic_official",
}
PROHIBITED_MODEL_INPUTS = {
    "participant_id",
    "source_year",
    "examination_weight",
    "strata",
    "psu",
    "waist_cm",
    "fasting_hours",
    "fasting_glucose_mg_dl",
    "hba1c_raw_pct",
    "hba1c_sensitivity_pct",
    "sbp_raw_mmhg",
    "dbp_raw_mmhg",
    "sbp_sensitivity_mmhg",
    "dbp_sensitivity_mmhg",
    "diabetes_measurement_label_raw",
    "diabetes_measurement_label_sensitivity",
    "hypertension_measurement_label_raw",
    "hypertension_measurement_label_sensitivity",
    "official_diabetes_comparator",
    "official_hypertension_comparator",
}

REQUIRED_CANONICAL_INPUT_COLUMNS = (
    {
        "ID",
        "age",
        "sex",
        "wt_itvex",
        "kstrata",
        "psu",
        "HE_prg",
        "HE_ht",
        "HE_wt",
        "HE_wc",
        "HE_fst",
        "HE_glu",
        "HE_HbA1c",
        "HE_DM_HbA1c",
        "HE_sbp",
        "HE_dbp",
        "HE_sbp2",
        "HE_sbp3",
        "HE_dbp2",
        "HE_dbp3",
        "HE_HP",
        "pa_aerobic",
    }
    | set(REQUIRED_KNHANES_COLUMNS)
)


class CanonicalETLError(ValueError):
    """Raised when canonical ETL input violates the frozen code contract."""


def _validate_year(year: int) -> int:
    if isinstance(year, bool) or not isinstance(year, Integral):
        raise CanonicalETLError("year must be an integer.")
    normalized = int(year)
    if normalized not in ALLOWED_YEARS:
        raise CanonicalETLError(f"Year {normalized} is not allowed for canonical ETL v0.1.")
    return normalized


def _clean_numeric(
    series: pd.Series,
    *,
    minimum: float | None = None,
    maximum: float | None = None,
    minimum_exclusive: float | None = None,
    integer: bool = False,
    outside_guard_action: str = "missing",
) -> tuple[pd.Series, pd.Series]:
    if outside_guard_action not in {"missing", "flag_only"}:
        raise CanonicalETLError(
            "outside_guard_action must be either 'missing' or 'flag_only'."
        )
    numeric = pd.to_numeric(series, errors="coerce")
    values_array = numeric.to_numpy(dtype="float64", na_value=np.nan)
    finite = pd.Series(np.isfinite(values_array), index=series.index)
    reason = pd.Series("ok", index=series.index, dtype="string")
    source_missing = series.isna()
    conversion_failure = series.notna() & numeric.isna()
    nonfinite = numeric.notna() & ~finite
    reason.loc[source_missing] = "source_missing"
    reason.loc[conversion_failure] = "numeric_conversion_failure"
    reason.loc[nonfinite] = "nonfinite"

    valid = finite.copy()
    if minimum is not None:
        below = finite & numeric.lt(minimum)
        reason.loc[below] = "below_guard"
        if outside_guard_action == "missing":
            valid &= ~below
    if maximum is not None:
        above = finite & numeric.gt(maximum)
        reason.loc[above] = "above_guard"
        if outside_guard_action == "missing":
            valid &= ~above
    if minimum_exclusive is not None:
        below_or_equal = finite & numeric.le(minimum_exclusive)
        reason.loc[below_or_equal] = "not_above_minimum"
        valid &= ~below_or_equal
    if integer:
        non_integer = finite & numeric.mod(1).ne(0)
        reason.loc[non_integer] = "non_integer"
        valid &= ~non_integer

    cleaned = pd.Series(pd.NA, index=series.index, dtype="Float64")
    cleaned.loc[valid] = numeric.loc[valid].astype(float)
    return cleaned, reason


def _clean_category(
    series: pd.Series, allowed: set[int]
) -> tuple[pd.Series, pd.Series]:
    numeric = pd.to_numeric(series, errors="coerce")
    reason = pd.Series("ok", index=series.index, dtype="string")
    reason.loc[series.isna()] = "source_missing"
    reason.loc[series.notna() & numeric.isna()] = "numeric_conversion_failure"
    integer = numeric.notna() & numeric.mod(1).eq(0)
    valid = integer & numeric.isin(allowed)
    reason.loc[numeric.notna() & ~valid] = "unexpected_code"
    cleaned = pd.Series(pd.NA, index=series.index, dtype="Int16")
    cleaned.loc[valid] = numeric.loc[valid].astype("int16")
    return cleaned, reason


def _nullable_binary(condition: pd.Series, eligible: pd.Series) -> pd.Series:
    result = pd.Series(pd.NA, index=condition.index, dtype="Int8")
    result.loc[eligible] = condition.loc[eligible].astype("int8")
    return result


def _issue(reason: pd.Series) -> pd.Series:
    return reason.ne("ok").astype("boolean")


def build_canonical_frame(frame: pd.DataFrame, *, year: int) -> pd.DataFrame:
    """Build a row-preserving canonical frame from one KNHANES source year."""

    normalized_year = _validate_year(year)
    missing = sorted(REQUIRED_CANONICAL_INPUT_COLUMNS - set(frame.columns))
    if missing:
        raise CanonicalETLError(f"Required canonical ETL columns are missing: {missing}")

    output = pd.DataFrame(index=frame.index)
    output["participant_id"] = frame["ID"].copy()
    output["source_year"] = pd.Series(normalized_year, index=frame.index, dtype="Int16")
    output["canonical_contract_version"] = CANONICAL_CONTRACT_VERSION
    output["hba1c_harmonization_version"] = (
        "knhanes_2022_plus_sensitivity_v1" if normalized_year >= 2022 else "identity_pre_2022"
    )
    output["bp_harmonization_version"] = (
        "knhanes_adult_microlife_to_greenlight_v1"
        if normalized_year >= 2021
        else "identity_pre_2021"
    )

    examination_weight, weight_design_reason = _clean_numeric(
        frame["wt_itvex"], minimum_exclusive=0
    )
    output["examination_weight"] = examination_weight
    output["strata"] = frame["kstrata"].copy()
    output["psu"] = frame["psu"].copy()

    age_float, age_reason = _clean_numeric(frame["age"], minimum=0, maximum=80, integer=True)
    age = pd.Series(pd.NA, index=frame.index, dtype="Int16")
    age.loc[age_float.notna()] = age_float.loc[age_float.notna()].astype("int16")
    sex, sex_reason = _clean_category(frame["sex"], {1, 2})
    pregnancy, pregnancy_reason = _clean_category(frame["HE_prg"], {0, 1, 8})
    output["age_years"] = age
    output["age_80_top_coded"] = age.eq(80).astype("boolean")
    output["sex_code"] = sex
    output["adult_eligible"] = age.ge(19).fillna(False).astype("boolean")
    valid_sex_pregnancy = (
        (sex.eq(1) & pregnancy.eq(8))
        | (sex.eq(2) & pregnancy.eq(0))
        | (sex.eq(2) & pregnancy.eq(1))
    )
    output["sex_pregnancy_consistency_issue"] = (
        sex.notna() & pregnancy.notna() & ~valid_sex_pregnancy
    ).fillna(False).astype("boolean")
    output["pregnancy_eligible"] = (
        (sex.eq(1) & pregnancy.eq(8)) | (sex.eq(2) & pregnancy.eq(0))
    ).fillna(False).astype("boolean")
    output["p0_general_adult_eligible"] = (
        output["adult_eligible"] & output["pregnancy_eligible"]
    ).fillna(False).astype("boolean")

    height, height_reason = _clean_numeric(
        frame["HE_ht"], minimum=100, maximum=220, outside_guard_action="flag_only"
    )
    body_weight, body_weight_reason = _clean_numeric(
        frame["HE_wt"], minimum=25, maximum=250, outside_guard_action="flag_only"
    )
    waist, waist_reason = _clean_numeric(
        frame["HE_wc"], minimum=40, maximum=200, outside_guard_action="flag_only"
    )
    output["height_cm"] = height
    output["weight_kg"] = body_weight
    output["waist_cm"] = waist
    output["bmi_kg_m2"] = (
        body_weight / ((height / 100.0) ** 2)
    ).astype("Float64")

    activity = derive_knhanes_activity_features(frame)
    for column in (
        "pa_aerobic_official",
        "pa_aerobic_reproduced",
        "pa_aerobic_match",
        "leisure_aerobic_moderate_equivalent_min_week",
        "strength_days_week",
        "strength_top_coded",
        "total_activity_complete",
        "leisure_activity_complete",
    ):
        output[column] = activity[column]

    fasting, fasting_reason = _clean_numeric(
        frame["HE_fst"], minimum=0, maximum=48, outside_guard_action="flag_only"
    )
    glucose, glucose_reason = _clean_numeric(
        frame["HE_glu"], minimum=30, maximum=600, outside_guard_action="flag_only"
    )
    hba1c, hba1c_reason = _clean_numeric(
        frame["HE_HbA1c"], minimum=3, maximum=20, outside_guard_action="flag_only"
    )
    hba1c_sensitivity = convert_hba1c_to_2019_2021_scale(hba1c, year=normalized_year)
    output["fasting_hours"] = fasting
    output["fasting_glucose_mg_dl"] = glucose
    output["hba1c_raw_pct"] = hba1c
    output["hba1c_sensitivity_pct"] = hba1c_sensitivity

    sbp, sbp_reason = _clean_numeric(
        frame["HE_sbp"], minimum=60, maximum=260, outside_guard_action="flag_only"
    )
    dbp, dbp_reason = _clean_numeric(
        frame["HE_dbp"], minimum=30, maximum=160, outside_guard_action="flag_only"
    )
    bp_sensitivity = convert_microlife_bp_to_greenlight_scale(
        sbp, dbp, age_float, year=normalized_year
    )
    output["sbp_raw_mmhg"] = sbp
    output["dbp_raw_mmhg"] = dbp
    output["sbp_sensitivity_mmhg"] = bp_sensitivity["sbp_greenlight_sensitivity"]
    output["dbp_sensitivity_mmhg"] = bp_sensitivity["dbp_greenlight_sensitivity"]

    p0 = output["p0_general_adult_eligible"].fillna(False)
    output["waist_target_eligible"] = (p0 & waist.notna()).astype("boolean")
    diabetes_eligible = (
        p0
        & fasting.ge(8)
        & glucose.notna()
        & hba1c.notna()
        & hba1c_sensitivity.notna()
    )
    output["diabetes_target_eligible"] = diabetes_eligible.astype("boolean")
    output["diabetes_measurement_label_raw"] = _nullable_binary(
        glucose.ge(126) | hba1c.ge(6.5), diabetes_eligible
    )
    output["diabetes_measurement_label_sensitivity"] = _nullable_binary(
        glucose.ge(126) | hba1c_sensitivity.ge(6.5), diabetes_eligible
    )

    hypertension_eligible = (
        p0
        & sbp.notna()
        & dbp.notna()
        & output["sbp_sensitivity_mmhg"].notna()
        & output["dbp_sensitivity_mmhg"].notna()
    )
    output["hypertension_target_eligible"] = hypertension_eligible.astype("boolean")
    output["hypertension_measurement_label_raw"] = _nullable_binary(
        sbp.ge(140) | dbp.ge(90), hypertension_eligible
    )
    output["hypertension_measurement_label_sensitivity"] = _nullable_binary(
        output["sbp_sensitivity_mmhg"].ge(140)
        | output["dbp_sensitivity_mmhg"].ge(90),
        hypertension_eligible,
    )

    official_diabetes, _ = _clean_category(frame["HE_DM_HbA1c"], {1, 2, 3})
    hp_allowed = {1, 2, 3} if normalized_year <= 2021 else {1, 2, 3, 4}
    official_hp, _ = _clean_category(frame["HE_HP"], hp_allowed)
    hp_positive_code = 3 if normalized_year <= 2021 else 4
    output["official_diabetes_comparator"] = _nullable_binary(
        official_diabetes.eq(3), official_diabetes.notna()
    )
    output["official_hypertension_comparator"] = _nullable_binary(
        official_hp.eq(hp_positive_code), official_hp.notna()
    )

    f0_complete = p0 & age.notna() & sex.notna() & height.notna() & body_weight.notna()
    output["feature_f0_complete"] = f0_complete.astype("boolean")
    output["feature_f1_complete"] = (
        f0_complete
        & output["pa_aerobic_official"].notna()
        & output["strength_days_week"].notna()
    ).astype("boolean")
    output["feature_f2_leisure_complete"] = (
        f0_complete
        & output["leisure_aerobic_moderate_equivalent_min_week"].notna()
        & output["strength_days_week"].notna()
    ).astype("boolean")

    output["age_quality_reason"] = age_reason
    output["sex_quality_reason"] = sex_reason
    output["pregnancy_quality_reason"] = pregnancy_reason
    output["height_quality_reason"] = height_reason
    output["weight_quality_reason"] = body_weight_reason
    output["waist_quality_reason"] = waist_reason
    output["fasting_hours_quality_reason"] = fasting_reason
    output["fasting_glucose_quality_reason"] = glucose_reason
    output["hba1c_quality_reason"] = hba1c_reason
    output["sbp_quality_reason"] = sbp_reason
    output["dbp_quality_reason"] = dbp_reason
    output["survey_weight_quality_reason"] = weight_design_reason
    output["anthropometry_quality_issue"] = (
        _issue(height_reason) | _issue(body_weight_reason) | _issue(waist_reason)
    ).astype("boolean")
    output["diabetes_measurement_quality_issue"] = (
        p0
        & (_issue(fasting_reason) | _issue(glucose_reason) | _issue(hba1c_reason))
    ).astype("boolean")
    output["blood_pressure_quality_issue"] = (
        p0 & (_issue(sbp_reason) | _issue(dbp_reason))
    ).astype("boolean")
    output["activity_quality_issue"] = (
        p0
        & (
            ~output["leisure_activity_complete"].fillna(False)
            | output["strength_days_week"].isna()
            | output["pa_aerobic_match"].eq(False).fillna(False)
        )
    ).astype("boolean")
    output["eligibility_quality_issue"] = (
        _issue(age_reason)
        | _issue(sex_reason)
        | _issue(pregnancy_reason)
        | output["sex_pregnancy_consistency_issue"]
    ).astype("boolean")

    return output


def assert_model_input_columns(columns: set[str]) -> None:
    """Fail closed if a downstream pipeline requests unapproved model inputs."""

    prohibited = columns & PROHIBITED_MODEL_INPUTS
    unknown = columns - MODEL_INPUT_ALLOWLIST
    if prohibited:
        raise CanonicalETLError(f"Prohibited model inputs requested: {sorted(prohibited)}")
    if unknown:
        raise CanonicalETLError(f"Unapproved model inputs requested: {sorted(unknown)}")
