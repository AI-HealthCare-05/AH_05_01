"""Synthetic-only tests for row-preserving canonical ETL."""

from __future__ import annotations

import pandas as pd
import pytest

from src.activity_features import ACTIVITY_BRANCHES
from src.canonical_etl import (
    CanonicalETLError,
    assert_model_input_columns,
    build_canonical_frame,
)


def _frame(rows: int = 1) -> pd.DataFrame:
    payload: dict[str, list[object]] = {
        "ID": [f"P{i}" for i in range(rows)],
        "age": [50] * rows,
        "sex": [2] * rows,
        "wt_itvex": [1.5] * rows,
        "kstrata": [101] * rows,
        "psu": [1] * rows,
        "HE_prg": [0] * rows,
        "HE_ht": [170.0] * rows,
        "HE_wt": [70.0] * rows,
        "HE_wc": [90.0] * rows,
        "HE_fst": [8.0] * rows,
        "HE_glu": [100.0] * rows,
        "HE_HbA1c": [6.4] * rows,
        "HE_DM_HbA1c": [2] * rows,
        "HE_sbp": [130.0] * rows,
        "HE_dbp": [80.0] * rows,
        "HE_sbp2": [130.0] * rows,
        "HE_sbp3": [130.0] * rows,
        "HE_dbp2": [80.0] * rows,
        "HE_dbp3": [80.0] * rows,
        "HE_HP": [1] * rows,
        "BE5_1": [1] * rows,
        "pa_aerobic": [0] * rows,
    }
    for participation, days, hours, minutes in ACTIVITY_BRANCHES.values():
        payload[participation] = [2] * rows
        payload[days] = [8] * rows
        payload[hours] = [88] * rows
        payload[minutes] = [88] * rows
    return pd.DataFrame(payload)


def test_etl_preserves_rows_and_builds_complete_zero_activity_feature() -> None:
    canonical = build_canonical_frame(_frame(2), year=2019)
    assert len(canonical) == 2
    assert canonical["participant_id"].tolist() == ["P0", "P1"]
    assert canonical["p0_general_adult_eligible"].all()
    assert canonical["leisure_aerobic_moderate_equivalent_min_week"].eq(0).all()
    assert canonical["strength_days_week"].eq(0).all()
    assert canonical["feature_f2_leisure_complete"].all()
    assert canonical["diabetes_measurement_label_raw"].eq(0).all()
    assert canonical["hypertension_measurement_label_raw"].eq(0).all()


def test_pregnancy_and_child_rows_are_preserved_but_targets_are_missing() -> None:
    frame = _frame(2)
    frame.loc[0, "HE_prg"] = 1
    frame.loc[1, "age"] = 10
    canonical = build_canonical_frame(frame, year=2019)
    assert not canonical["p0_general_adult_eligible"].any()
    assert canonical["diabetes_measurement_label_raw"].isna().all()
    assert canonical["hypertension_measurement_label_raw"].isna().all()
    assert canonical["waist_target_eligible"].eq(False).all()


def test_candidate_guard_preserves_value_and_flags_without_clipping() -> None:
    frame = _frame()
    frame.loc[0, "HE_ht"] = 99.0
    frame.loc[0, "HE_glu"] = 700.0
    canonical = build_canonical_frame(frame, year=2019)
    assert canonical.loc[0, "height_cm"] == pytest.approx(99.0)
    assert canonical.loc[0, "height_quality_reason"] == "below_guard"
    assert canonical.loc[0, "fasting_glucose_mg_dl"] == pytest.approx(700.0)
    assert canonical.loc[0, "fasting_glucose_quality_reason"] == "above_guard"
    assert canonical.loc[0, "feature_f0_complete"]
    assert canonical.loc[0, "diabetes_measurement_label_raw"] == 1
    assert canonical.loc[0, "anthropometry_quality_issue"]
    assert canonical.loc[0, "diabetes_measurement_quality_issue"]


def test_nonfinite_numeric_is_still_missing_and_ineligible() -> None:
    frame = _frame()
    frame.loc[0, "HE_glu"] = float("inf")
    canonical = build_canonical_frame(frame, year=2019)
    assert pd.isna(canonical.loc[0, "fasting_glucose_mg_dl"])
    assert canonical.loc[0, "fasting_glucose_quality_reason"] == "nonfinite"
    assert pd.isna(canonical.loc[0, "diabetes_measurement_label_raw"])


def test_structural_activity_skip_is_not_mistaken_for_unknown() -> None:
    frame = _frame(2)
    frame.loc[0, "BE3_75"] = 8
    frame.loc[1, "BE3_75"] = 9
    canonical = build_canonical_frame(frame, year=2019)
    assert pd.isna(canonical.loc[0, "leisure_aerobic_moderate_equivalent_min_week"])
    assert pd.isna(canonical.loc[1, "leisure_aerobic_moderate_equivalent_min_week"])
    assert canonical["activity_quality_issue"].all()


def test_2022_hba1c_sensitivity_is_separate_and_can_reclassify() -> None:
    canonical = build_canonical_frame(_frame(), year=2022)
    assert canonical.loc[0, "hba1c_raw_pct"] == pytest.approx(6.4)
    assert canonical.loc[0, "hba1c_sensitivity_pct"] == pytest.approx(6.5728)
    assert canonical.loc[0, "diabetes_measurement_label_raw"] == 0
    assert canonical.loc[0, "diabetes_measurement_label_sensitivity"] == 1


def test_invalid_categories_fail_closed_without_deleting_row() -> None:
    frame = _frame()
    frame.loc[0, "sex"] = 7
    frame.loc[0, "HE_prg"] = 9
    canonical = build_canonical_frame(frame, year=2019)
    assert len(canonical) == 1
    assert pd.isna(canonical.loc[0, "sex_code"])
    assert canonical.loc[0, "sex_quality_reason"] == "unexpected_code"
    assert canonical.loc[0, "pregnancy_quality_reason"] == "unexpected_code"
    assert not canonical.loc[0, "p0_general_adult_eligible"]


def test_sex_pregnancy_inconsistency_is_excluded_and_flagged() -> None:
    frame = _frame()
    frame.loc[0, "sex"] = 1
    frame.loc[0, "HE_prg"] = 0
    canonical = build_canonical_frame(frame, year=2019)
    assert canonical.loc[0, "sex_pregnancy_consistency_issue"]
    assert canonical.loc[0, "eligibility_quality_issue"]
    assert not canonical.loc[0, "p0_general_adult_eligible"]


def test_model_input_allowlist_blocks_leakage() -> None:
    assert_model_input_columns({"age_years", "height_cm"})
    with pytest.raises(CanonicalETLError, match="Prohibited"):
        assert_model_input_columns({"age_years", "hba1c_raw_pct"})
    with pytest.raises(CanonicalETLError, match="Unapproved"):
        assert_model_input_columns({"age_years", "mystery_feature"})


@pytest.mark.parametrize("year", [2018, 2023, 2024])
def test_disallowed_years_are_rejected(year: int) -> None:
    with pytest.raises(CanonicalETLError, match="not allowed"):
        build_canonical_frame(_frame(), year=year)
