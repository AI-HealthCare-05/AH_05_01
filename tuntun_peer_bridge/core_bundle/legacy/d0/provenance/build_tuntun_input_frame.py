"""Build the MVP-baseline tuntun-index input frame (union + three-way views).

Combines the existing frozen 2019-2021 nested-OOF outputs of three independently
developed models -- waist W2 (regression), diabetes D0 and hypertension D0
(classification) -- into two read-only analysis frames, keyed by
[source_year, participant_id]:

- a union frame over all rows appearing in any of the three task frames
  (expected 16,440 rows for the 2019-2021 development snapshot), with
  per-model eligibility flags and null model outputs for ineligible rows;
- a three-way frame restricted to rows eligible for all three models
  (expected 15,607 rows).

This script does not fit, select, or calibrate any model, and does not compute
abdominal_obesity_probability, physical_score, aerobic_score, strength_score,
lifestyle_score, a composite tuntun index, or any matched-D1 stacking input.
Those require separate contract approval (see
reports/mvp_baseline_v0_1/TUNTUN_SCORE_INPUT_DATASET_DESIGN_2026-09-02.md).

Row-level participant identifiers are written only to the two output CSVs
under --output-root (never under reports/, never to stdout, never into the
validation-summary or manifest JSON, which carry aggregate values only).

Writing is staged and atomic: outputs are built and fully re-validated
(including a manifest hash self-check) inside a hidden sibling directory of
--output-root, then committed with a single same-volume directory rename onto
--output-root. --output-root must not already exist (even empty) before that
rename; if any check fails, the staging directory is discarded and
--output-root is never created.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd

BASELINE_VERSION = "mvp_baseline_v0_1"
SOURCE_TASK_VERSION = "development_v0_2"

KEY_COLUMNS = ["source_year", "participant_id"]
RAW_INPUT_COLUMNS = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]
SURVEY_DESIGN_COLUMNS = ["examination_weight", "strata", "psu"]
IDENTITY_COLUMNS = KEY_COLUMNS + RAW_INPUT_COLUMNS + SURVEY_DESIGN_COLUMNS

ALLOWED_YEARS = {2019, 2020, 2021}
ALLOWED_SEEDS = (42, 1042, 2042)
ALLOWED_OUTER_FOLDS = {0, 1, 2, 3, 4}
AGE_MIN, AGE_MAX = 19, 80

TASKS = ("waist", "diabetes", "hypertension")

# 2019-2021 development snapshot reference counts, confirmed by direct count
# against data/cohort_rebuild_20260830/task_frames/development_v0_2/*.
# A mismatch means the input snapshot changed and must be re-reviewed before
# this script is trusted again -- it is not silently tolerated.
EXPECTED_TASK_FRAME_ROWS = {"waist": 16403, "diabetes": 15720, "hypertension": 16325}
EXPECTED_UNION_ROWS = 16440
EXPECTED_THREE_WAY_ROWS = 15607

UNION_COLUMN_ORDER = [
    "source_year",
    "participant_id",
    "baseline_version",
    "source_task_version",
    "oof_seed",
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
    "examination_weight",
    "strata",
    "psu",
    "waist_eligible",
    "diabetes_eligible",
    "hypertension_eligible",
    "waist_oof_pred_cm",
    "waist_outer_fold",
    "waist_selected_candidate",
    "diabetes_oof_probability_raw",
    "diabetes_oof_probability_calibrated",
    "diabetes_outer_fold",
    "diabetes_selected_candidate",
    "diabetes_selected_calibrator",
    "hypertension_oof_probability_raw",
    "hypertension_oof_probability_calibrated",
    "hypertension_outer_fold",
    "hypertension_selected_candidate",
    "hypertension_selected_calibrator",
]

WAIST_ELIGIBLE_OUTPUT_COLUMNS = ["waist_oof_pred_cm", "waist_outer_fold", "waist_selected_candidate"]
D0_ELIGIBLE_OUTPUT_COLUMNS = {
    task: [
        f"{task}_oof_probability_raw",
        f"{task}_oof_probability_calibrated",
        f"{task}_outer_fold",
        f"{task}_selected_candidate",
        f"{task}_selected_calibrator",
    ]
    for task in ("diabetes", "hypertension")
}
ELIGIBLE_OUTPUT_COLUMNS = {"waist": WAIST_ELIGIBLE_OUTPUT_COLUMNS, **D0_ELIGIBLE_OUTPUT_COLUMNS}

# Columns that must never appear in this core OOF-only frame: measured labels,
# measured waist, anything derived from them, and every unapproved score.
FORBIDDEN_COLUMNS = {
    "waist_cm",
    "diabetes_measurement_label_raw",
    "hypertension_measurement_label_raw",
    "waist_prediction_error",
    "waist_lower_bound",
    "waist_upper_bound",
    "abdominal_obesity_probability",
    "physical_score",
    "aerobic_score",
    "strength_score",
    "lifestyle_score",
    "composite_tuntun_index",
}


class ValidationError(RuntimeError):
    """Raised for any fail-closed check failure. Messages must stay aggregate-only."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _multiindex(frame: pd.DataFrame) -> pd.MultiIndex:
    return pd.MultiIndex.from_frame(frame[KEY_COLUMNS])


# ---------------------------------------------------------------------------
# Strict scalar validators (no truncating astype(int) before range checks)
# ---------------------------------------------------------------------------


def validate_strict_integer(series: pd.Series, label: str, allowed_values: set[int]) -> pd.Series:
    """Fail-closed integer validation without truncation.

    Order: (1) numeric-convertible, (2) no missing/NaN/inf, (3) exactly integral
    (value == floor(value)) -- a fractional value like 2020.5 or 0.5 must be
    rejected here, not silently floored by astype(int), (4) within allowed_values.
    Returns a plain int64 Series.
    """
    if series.isna().any():
        raise ValidationError(f"{label} has missing value(s)")

    numeric = pd.to_numeric(series, errors="coerce")
    if numeric.isna().any():
        raise ValidationError(f"{label} has non-numeric value(s)")

    values = numeric.to_numpy(dtype="float64")
    if not np.isfinite(values).all():
        raise ValidationError(f"{label} has non-finite (NaN/inf) value(s)")

    if not np.array_equal(values, np.floor(values)):
        raise ValidationError(f"{label} has non-integer (fractional) value(s)")

    int_values = values.astype("int64")
    bad = sorted(set(int_values.tolist()) - allowed_values)
    if bad:
        raise ValidationError(f"{label} has disallowed value(s): {bad}")

    return pd.Series(int_values, index=series.index)


def validate_id_column(series: pd.Series, label: str) -> None:
    if series.isna().any():
        raise ValidationError(f"{label} has missing value(s)")
    stripped = series.astype(str).str.strip()
    if (stripped == "").any():
        raise ValidationError(f"{label} has empty/whitespace-only value(s)")


def validate_non_blank_string(series: pd.Series, label: str) -> None:
    if series.isna().any():
        raise ValidationError(f"{label} has missing value(s)")
    stripped = series.astype(str).str.strip()
    if (stripped == "").any():
        raise ValidationError(f"{label} has empty/whitespace-only value(s)")


def validate_unit_interval(series: pd.Series, label: str) -> None:
    values = pd.to_numeric(series, errors="coerce")
    if values.isna().any():
        raise ValidationError(f"{label} has missing/non-numeric value(s)")
    if not np.isfinite(values.to_numpy()).all():
        raise ValidationError(f"{label} has non-finite value(s)")
    if (values < 0).any() or (values > 1).any():
        raise ValidationError(f"{label} has value(s) outside [0, 1]")


def validate_finite_numeric(series: pd.Series, label: str) -> None:
    values = pd.to_numeric(series, errors="coerce")
    if values.isna().any():
        raise ValidationError(f"{label} has missing/non-numeric value(s)")
    if not np.isfinite(values.to_numpy()).all():
        raise ValidationError(f"{label} has non-finite value(s)")


# ---------------------------------------------------------------------------
# Loaders (input-side, pre-write validation)
# ---------------------------------------------------------------------------


def load_task_frame(path: Path, task: str, expected_rows: int | None = None) -> pd.DataFrame:
    if not path.exists():
        raise ValidationError(f"{task} task frame not found: {path}")
    frame = pd.read_csv(path)

    required = set(IDENTITY_COLUMNS)
    missing = required - set(frame.columns)
    if missing:
        raise ValidationError(f"{task} task frame missing required columns: {sorted(missing)}")

    validate_strict_integer(frame["source_year"], f"{task} task frame source_year", ALLOWED_YEARS)
    validate_id_column(frame["participant_id"], f"{task} task frame participant_id")

    if frame.duplicated(KEY_COLUMNS).any():
        n_dup = int(frame.duplicated(KEY_COLUMNS).sum())
        raise ValidationError(f"{task} task frame has {n_dup} duplicated (source_year, participant_id) key(s)")

    if frame[RAW_INPUT_COLUMNS].isna().any().any():
        n_missing = int(frame[RAW_INPUT_COLUMNS].isna().any(axis=1).sum())
        raise ValidationError(f"{task} task frame has {n_missing} row(s) with missing six-feature input")

    if frame[SURVEY_DESIGN_COLUMNS].isna().any().any():
        n_missing = int(frame[SURVEY_DESIGN_COLUMNS].isna().any(axis=1).sum())
        raise ValidationError(f"{task} task frame has {n_missing} row(s) with missing survey-design value")

    age = pd.to_numeric(frame["age_years"], errors="raise")
    if age.lt(AGE_MIN).any() or age.gt(AGE_MAX).any():
        raise ValidationError(f"{task} task frame has age_years outside [{AGE_MIN}, {AGE_MAX}]")

    if expected_rows is not None and len(frame) != expected_rows:
        raise ValidationError(
            f"{task} task frame row count {len(frame)} != expected {expected_rows} "
            "(input snapshot changed; re-review before trusting this script)"
        )

    return frame


def load_waist_oof(path: Path, seed: int) -> pd.DataFrame:
    if not path.exists():
        raise ValidationError(f"waist W2 OOF file not found: {path}")
    frame = pd.read_csv(path)

    required = {"source_year", "participant_id", "outer_fold", "estimated_waist_cm", "selected_candidate"}
    missing = required - set(frame.columns)
    if missing:
        raise ValidationError(f"waist W2 OOF (seed {seed}) missing required columns: {sorted(missing)}")

    validate_strict_integer(frame["source_year"], f"waist W2 OOF (seed {seed}) source_year", ALLOWED_YEARS)
    validate_id_column(frame["participant_id"], f"waist W2 OOF (seed {seed}) participant_id")

    if frame.duplicated(KEY_COLUMNS).any():
        n_dup = int(frame.duplicated(KEY_COLUMNS).sum())
        raise ValidationError(f"waist W2 OOF (seed {seed}) has {n_dup} duplicated key(s)")

    validate_finite_numeric(frame["estimated_waist_cm"], f"waist W2 OOF (seed {seed}) estimated_waist_cm")
    validate_strict_integer(frame["outer_fold"], f"waist W2 OOF (seed {seed}) outer_fold", ALLOWED_OUTER_FOLDS)
    validate_non_blank_string(frame["selected_candidate"], f"waist W2 OOF (seed {seed}) selected_candidate")

    return frame.rename(
        columns={
            "outer_fold": "waist_outer_fold",
            "estimated_waist_cm": "waist_oof_pred_cm",
            "selected_candidate": "waist_selected_candidate",
        }
    )


def load_d0_oof(path: Path, task: str, seed: int) -> pd.DataFrame:
    if not path.exists():
        raise ValidationError(f"{task} D0 OOF file not found: {path}")
    frame = pd.read_csv(path)

    required = {
        "source_year",
        "participant_id",
        "outer_fold",
        "oof_probability_raw",
        "oof_probability_calibrated",
        "selected_candidate",
        "selected_calibrator",
    }
    missing = required - set(frame.columns)
    if missing:
        raise ValidationError(f"{task} D0 OOF (seed {seed}) missing required columns: {sorted(missing)}")

    validate_strict_integer(frame["source_year"], f"{task} D0 OOF (seed {seed}) source_year", ALLOWED_YEARS)
    validate_id_column(frame["participant_id"], f"{task} D0 OOF (seed {seed}) participant_id")

    if frame.duplicated(KEY_COLUMNS).any():
        n_dup = int(frame.duplicated(KEY_COLUMNS).sum())
        raise ValidationError(f"{task} D0 OOF (seed {seed}) has {n_dup} duplicated key(s)")

    validate_unit_interval(frame["oof_probability_raw"], f"{task} D0 OOF (seed {seed}) oof_probability_raw")
    validate_unit_interval(
        frame["oof_probability_calibrated"], f"{task} D0 OOF (seed {seed}) oof_probability_calibrated"
    )
    validate_strict_integer(frame["outer_fold"], f"{task} D0 OOF (seed {seed}) outer_fold", ALLOWED_OUTER_FOLDS)
    validate_non_blank_string(frame["selected_candidate"], f"{task} D0 OOF (seed {seed}) selected_candidate")
    validate_non_blank_string(frame["selected_calibrator"], f"{task} D0 OOF (seed {seed}) selected_calibrator")

    rename = {
        "outer_fold": f"{task}_outer_fold",
        "oof_probability_raw": f"{task}_oof_probability_raw",
        "oof_probability_calibrated": f"{task}_oof_probability_calibrated",
        "selected_candidate": f"{task}_selected_candidate",
        "selected_calibrator": f"{task}_selected_calibrator",
    }
    return frame.rename(columns=rename)


def assert_exact_key_match(task_frame: pd.DataFrame, oof_frame: pd.DataFrame, task: str, seed: int) -> None:
    task_keys = set(_multiindex(task_frame))
    oof_keys = set(_multiindex(oof_frame))
    if task_keys != oof_keys:
        only_task = len(task_keys - oof_keys)
        only_oof = len(oof_keys - task_keys)
        raise ValidationError(
            f"{task} task frame and seed-{seed} OOF keys are not a 1:1 match "
            f"(in task frame only: {only_task}, in OOF only: {only_oof})"
        )


def assert_common_columns_consistent(task_frames: dict[str, pd.DataFrame]) -> None:
    """Pairwise-verify shared raw-input + survey-design values agree across tasks."""
    names = list(task_frames)
    total_mismatches = 0
    for i in range(len(names)):
        for j in range(i + 1, len(names)):
            a = task_frames[names[i]][IDENTITY_COLUMNS]
            b = task_frames[names[j]][IDENTITY_COLUMNS]
            merged = a.merge(b, on=KEY_COLUMNS, suffixes=("_a", "_b"))
            for col in RAW_INPUT_COLUMNS + SURVEY_DESIGN_COLUMNS:
                mismatched = (merged[f"{col}_a"] != merged[f"{col}_b"]).sum()
                total_mismatches += int(mismatched)
    if total_mismatches:
        raise ValidationError(
            f"found {total_mismatches} cross-task mismatch(es) in shared raw-input/survey-design values"
        )


# ---------------------------------------------------------------------------
# Frame construction
# ---------------------------------------------------------------------------


def build_identity_frame(task_frames: dict[str, pd.DataFrame]) -> pd.DataFrame:
    parts = [task_frames[task][IDENTITY_COLUMNS] for task in TASKS]
    combined = pd.concat(parts, ignore_index=True)
    combined = combined.drop_duplicates(subset=KEY_COLUMNS, keep="first").reset_index(drop=True)
    return combined


def build_union_frame(
    task_frames: dict[str, pd.DataFrame],
    oof_frames: dict[str, pd.DataFrame],
    seed: int,
    expected_union_rows: int | None = None,
) -> pd.DataFrame:
    identity = build_identity_frame(task_frames)

    eligible_index = {task: set(_multiindex(task_frames[task])) for task in TASKS}
    row_index = list(zip(identity["source_year"], identity["participant_id"]))
    for task in TASKS:
        identity[f"{task}_eligible"] = [key in eligible_index[task] for key in row_index]

    identity["baseline_version"] = BASELINE_VERSION
    identity["source_task_version"] = SOURCE_TASK_VERSION
    identity["oof_seed"] = seed

    union = identity.merge(oof_frames["waist"], on=KEY_COLUMNS, how="left")
    union = union.merge(oof_frames["diabetes"], on=KEY_COLUMNS, how="left")
    union = union.merge(oof_frames["hypertension"], on=KEY_COLUMNS, how="left")

    for fold_col in ("waist_outer_fold", "diabetes_outer_fold", "hypertension_outer_fold"):
        union[fold_col] = union[fold_col].astype("Int64")

    if expected_union_rows is not None and len(union) != expected_union_rows:
        raise ValidationError(f"union frame row count {len(union)} != expected {expected_union_rows}")

    for task in TASKS:
        eligible_count = int(union[f"{task}_eligible"].sum())
        expected = len(task_frames[task])
        if eligible_count != expected:
            raise ValidationError(
                f"{task}_eligible count {eligible_count} != loaded {task} task frame row count {expected}"
            )

    for task, columns in ELIGIBLE_OUTPUT_COLUMNS.items():
        eligible_rows = union[union[f"{task}_eligible"]]
        for col in columns:
            n_missing = int(eligible_rows[col].isna().sum())
            if n_missing:
                raise ValidationError(f"{n_missing} {task}-eligible row(s) missing {col}")

    return union[UNION_COLUMN_ORDER]


def build_three_way_frame(union: pd.DataFrame, expected_rows: int | None = None) -> pd.DataFrame:
    three_way = union[
        union["waist_eligible"] & union["diabetes_eligible"] & union["hypertension_eligible"]
    ].reset_index(drop=True)
    if expected_rows is not None and len(three_way) != expected_rows:
        raise ValidationError(f"three-way frame row count {len(three_way)} != expected {expected_rows}")
    return three_way


# ---------------------------------------------------------------------------
# Output-contract validation (post-write, re-read from disk)
# ---------------------------------------------------------------------------


def validate_output_contract(
    path: Path,
    expected_rows: int,
    seed: int,
    expected_eligible: dict[str, int],
    require_all_eligible: bool,
) -> None:
    """Re-read a written CSV from disk and fully re-verify the output contract.

    Every numeric conversion below uses errors="raise" (never "coerce" followed
    by dropna) so a value that fails to parse aborts the run instead of being
    silently excluded from the check.
    """
    reread = pd.read_csv(path)

    if list(reread.columns) != UNION_COLUMN_ORDER:
        raise ValidationError(f"{path.name} column list/order does not match the frozen contract")

    if len(reread) != expected_rows:
        raise ValidationError(f"{path.name} re-read row count {len(reread)} != expected {expected_rows}")

    if reread[KEY_COLUMNS].isna().any().any():
        raise ValidationError(f"{path.name} has missing key value(s)")
    if reread.duplicated(KEY_COLUMNS).any():
        raise ValidationError(f"{path.name} has duplicated keys")

    if not (reread["baseline_version"] == BASELINE_VERSION).all():
        raise ValidationError(f"{path.name} has baseline_version value(s) != {BASELINE_VERSION}")
    if not (reread["source_task_version"] == SOURCE_TASK_VERSION).all():
        raise ValidationError(f"{path.name} has source_task_version value(s) != {SOURCE_TASK_VERSION}")
    validate_strict_integer(reread["oof_seed"], f"{path.name} oof_seed", {seed})

    validate_strict_integer(reread["source_year"], f"{path.name} source_year", ALLOWED_YEARS)
    age = pd.to_numeric(reread["age_years"], errors="raise")
    if age.lt(AGE_MIN).any() or age.gt(AGE_MAX).any():
        raise ValidationError(f"{path.name} has age_years outside [{AGE_MIN}, {AGE_MAX}]")

    validate_id_column(reread["participant_id"], f"{path.name} participant_id")

    for col in RAW_INPUT_COLUMNS + SURVEY_DESIGN_COLUMNS:
        if reread[col].isna().any():
            raise ValidationError(f"{path.name} has missing {col}")

    for task in TASKS:
        col = f"{task}_eligible"
        non_bool = set(reread[col].unique()) - {True, False}
        if non_bool:
            raise ValidationError(f"{path.name} has non-boolean {col} value(s): {sorted(map(str, non_bool))}")

    for task in TASKS:
        eligible_count = int(reread[f"{task}_eligible"].sum())
        if eligible_count != expected_eligible[task]:
            raise ValidationError(
                f"{path.name} {task}_eligible count {eligible_count} != expected {expected_eligible[task]}"
            )

    if require_all_eligible:
        all_three = reread["waist_eligible"] & reread["diabetes_eligible"] & reread["hypertension_eligible"]
        if not all_three.all():
            raise ValidationError(f"{path.name} has row(s) not eligible for all three models")

    waist_elig = reread["waist_eligible"]
    validate_finite_numeric(reread.loc[waist_elig, "waist_oof_pred_cm"], f"{path.name} waist_oof_pred_cm")
    validate_strict_integer(
        reread.loc[waist_elig, "waist_outer_fold"], f"{path.name} waist_outer_fold", ALLOWED_OUTER_FOLDS
    )
    validate_non_blank_string(
        reread.loc[waist_elig, "waist_selected_candidate"], f"{path.name} waist_selected_candidate"
    )
    waist_inelig = ~waist_elig
    for col in WAIST_ELIGIBLE_OUTPUT_COLUMNS:
        if reread.loc[waist_inelig, col].notna().any():
            raise ValidationError(f"{path.name} has non-null {col} for waist-ineligible row(s)")

    for task in ("diabetes", "hypertension"):
        elig = reread[f"{task}_eligible"]
        inelig = ~elig
        validate_unit_interval(
            reread.loc[elig, f"{task}_oof_probability_raw"], f"{path.name} {task}_oof_probability_raw"
        )
        validate_unit_interval(
            reread.loc[elig, f"{task}_oof_probability_calibrated"],
            f"{path.name} {task}_oof_probability_calibrated",
        )
        validate_strict_integer(
            reread.loc[elig, f"{task}_outer_fold"], f"{path.name} {task}_outer_fold", ALLOWED_OUTER_FOLDS
        )
        validate_non_blank_string(
            reread.loc[elig, f"{task}_selected_candidate"], f"{path.name} {task}_selected_candidate"
        )
        validate_non_blank_string(
            reread.loc[elig, f"{task}_selected_calibrator"], f"{path.name} {task}_selected_calibrator"
        )
        for col in D0_ELIGIBLE_OUTPUT_COLUMNS[task]:
            if reread.loc[inelig, col].notna().any():
                raise ValidationError(f"{path.name} has non-null {col} for {task}-ineligible row(s)")

    # The exact column-list check above already rejects any extra column, so a
    # forbidden column is normally caught there first. This check stays as an
    # explicit, independently testable guard for the specific requirement --
    # it does not depend on the column-order check remaining exact-match.
    forbidden_present = FORBIDDEN_COLUMNS.intersection(reread.columns)
    if forbidden_present:
        raise ValidationError(f"{path.name} contains forbidden column(s): {sorted(forbidden_present)}")


def validate_union_three_way_relationship(union_path: Path, three_way_path: Path) -> None:
    union = pd.read_csv(union_path)
    three_way = pd.read_csv(three_way_path)

    union_keys = set(_multiindex(union))
    three_way_keys = set(_multiindex(three_way))
    if not three_way_keys.issubset(union_keys):
        raise ValidationError("three-way frame contains key(s) absent from the union frame")

    all_three_in_union = set(
        _multiindex(union[union["waist_eligible"] & union["diabetes_eligible"] & union["hypertension_eligible"]])
    )
    if three_way_keys != all_three_in_union:
        raise ValidationError(
            "three-way frame keys do not exactly match the all-eligible subset of the union frame"
        )


# ---------------------------------------------------------------------------
# Manifest
# ---------------------------------------------------------------------------


def build_manifest(seed: int, input_paths: dict[str, Path], output_paths: list[Path]) -> dict:
    manifest = {"seed": seed, "inputs": {}, "code": {}, "outputs": {}}
    for label, path in input_paths.items():
        manifest["inputs"][label] = {"path": str(path), "sha256": sha256_file(path)}
    manifest["code"][Path(__file__).name] = sha256_file(Path(__file__))
    for path in output_paths:
        manifest["outputs"][path.name] = sha256_file(path)
    return manifest


def verify_manifest_hashes(
    manifest: dict,
    input_paths: dict[str, Path],
    staging_dir: Path,
    code_path: Path | None = None,
) -> None:
    """Recompute every hash recorded in a just-built manifest and compare.

    `code_path` defaults to this module's own file (build_tuntun_input_frame.py)
    so existing call sites within this module are unaffected. A caller from a
    different script (e.g. analyze_tuntun_input_three_seed_stability.py, which
    reuses this function rather than duplicating it) must pass its own
    `Path(__file__)` here -- otherwise this would always check for *this*
    module's filename in `manifest["code"]`, which would never be there.
    """
    code_path = code_path or Path(__file__)

    for label, path in input_paths.items():
        recomputed = sha256_file(path)
        recorded = manifest["inputs"][label]["sha256"]
        if recomputed != recorded:
            raise ValidationError(f"manifest input hash mismatch for {label} (input changed mid-run)")

    recomputed_code = sha256_file(code_path)
    recorded_code = manifest["code"].get(code_path.name)
    if recorded_code is None:
        raise ValidationError(f"manifest has no recorded code hash for {code_path.name}")
    if recomputed_code != recorded_code:
        raise ValidationError(f"manifest code hash mismatch for {code_path.name} (script changed mid-run)")

    for name, recorded in manifest["outputs"].items():
        recomputed = sha256_file(staging_dir / name)
        if recomputed != recorded:
            raise ValidationError(f"manifest output hash mismatch for {name}")


# ---------------------------------------------------------------------------
# Validation summary (stage-labeled: only records checks that actually ran)
# ---------------------------------------------------------------------------

PRE_WRITE_CHECKS = [
    "task_frame_source_year_strict_integer_2019_2021",
    "task_frame_participant_id_present_non_blank",
    "task_frame_key_duplicates_zero",
    "task_frame_six_feature_and_survey_design_completeness",
    "task_frame_age_19_to_80",
    "task_frame_row_counts_match_expected_snapshot",
    "oof_source_year_strict_integer_2019_2021",
    "oof_participant_id_present_non_blank",
    "oof_key_duplicates_zero",
    "oof_selected_candidate_calibrator_non_blank",
    "oof_outer_fold_strict_integer_0_to_4",
    "d0_probability_finite_and_0_to_1",
    "waist_prediction_finite",
    "task_frame_oof_key_1to1_per_model",
    "cross_task_shared_columns_consistent",
    "eligible_counts_match_task_frames",
    "eligible_row_outputs_present",
    "union_and_three_way_row_counts_match_expected",
]

POST_WRITE_CHECKS = [
    "reread_column_list_and_order",
    "reread_row_counts",
    "reread_key_duplicates_and_missing_zero",
    "reread_metadata_version_and_seed_match",
    "reread_source_year_and_age_range",
    "reread_participant_id_non_blank",
    "reread_six_feature_and_survey_design_completeness",
    "reread_eligibility_columns_boolean",
    "reread_eligible_counts_match_expected",
    "reread_three_way_all_eligible_true",
    "reread_eligible_outputs_finite_and_in_range",
    "reread_ineligible_outputs_null",
    "reread_outer_fold_strict_integer_0_to_4",
    "reread_candidate_calibrator_non_blank",
    "reread_forbidden_columns_absent",
    "reread_union_three_way_key_relationship",
]

MANIFEST_VERIFICATION_CHECKS = [
    "manifest_input_hashes_recomputed_and_matched",
    "manifest_code_hash_recomputed_and_matched",
    "manifest_output_hashes_recomputed_and_matched",
]


def build_validation_summary(
    seed: int,
    task_frames: dict[str, pd.DataFrame],
    union: pd.DataFrame,
    three_way: pd.DataFrame,
    dry_run: bool,
    stage: str,
) -> dict:
    summary = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "baseline_version": BASELINE_VERSION,
        "source_task_version": SOURCE_TASK_VERSION,
        "seed": seed,
        "dry_run": dry_run,
        "stage": stage,
        "task_frame_row_counts": {task: int(len(task_frames[task])) for task in TASKS},
        "union_rows": int(len(union)),
        "three_way_rows": int(len(three_way)),
        "eligible_counts": {task: int(union[f"{task}_eligible"].sum()) for task in TASKS},
        "diabetes_probability_range": {
            "raw_min": float(union["diabetes_oof_probability_raw"].min(skipna=True)),
            "raw_max": float(union["diabetes_oof_probability_raw"].max(skipna=True)),
            "calibrated_min": float(union["diabetes_oof_probability_calibrated"].min(skipna=True)),
            "calibrated_max": float(union["diabetes_oof_probability_calibrated"].max(skipna=True)),
        },
        "hypertension_probability_range": {
            "raw_min": float(union["hypertension_oof_probability_raw"].min(skipna=True)),
            "raw_max": float(union["hypertension_oof_probability_raw"].max(skipna=True)),
            "calibrated_min": float(union["hypertension_oof_probability_calibrated"].min(skipna=True)),
            "calibrated_max": float(union["hypertension_oof_probability_calibrated"].max(skipna=True)),
        },
        "waist_oof_pred_cm_range": {
            "min": float(union["waist_oof_pred_cm"].min(skipna=True)),
            "max": float(union["waist_oof_pred_cm"].max(skipna=True)),
        },
        "pre_write_checks_passed": list(PRE_WRITE_CHECKS),
        "post_write_checks_passed": list(POST_WRITE_CHECKS) if stage in ("post_write", "manifest_verified") else [],
        "manifest_verification_checks_passed": (
            list(MANIFEST_VERIFICATION_CHECKS) if stage == "manifest_verified" else []
        ),
        "not_computed_out_of_scope": [
            "abdominal_obesity_probability",
            "physical_score",
            "aerobic_score",
            "strength_score",
            "lifestyle_score",
            "composite_tuntun_index",
            "measured_labels_or_waist_cm",
            "waist_prediction_error_or_residual",
            "production_refit_model",
        ],
    }
    return summary


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def parse_args() -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--task-frame-root",
        type=Path,
        default=repo_root / "data/cohort_rebuild_20260830/task_frames/development_v0_2",
    )
    parser.add_argument(
        "--waist-oof-root",
        type=Path,
        default=repo_root / "artifacts/model_development_v0_1/waist/oof",
    )
    parser.add_argument(
        "--d0-oof-root",
        type=Path,
        default=repo_root / "artifacts/model_development_v0_1/d0",
    )
    parser.add_argument("--seed", type=int, required=True, choices=ALLOWED_SEEDS)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def resolve_input_paths(args: argparse.Namespace) -> dict[str, Path]:
    return {
        "waist_task_frame": args.task_frame_root / "waist" / "development_2019_2021.csv",
        "diabetes_task_frame": args.task_frame_root / "diabetes" / "development_2019_2021.csv",
        "hypertension_task_frame": args.task_frame_root / "hypertension" / "development_2019_2021.csv",
        "waist_oof": args.waist_oof_root / f"W2_seed{args.seed}.csv",
        "diabetes_oof": args.d0_oof_root / "diabetes" / "oof" / f"seed{args.seed}.csv",
        "hypertension_oof": args.d0_oof_root / "hypertension" / "oof" / f"seed{args.seed}.csv",
    }


def _load_and_check_inputs(args: argparse.Namespace, paths: dict[str, Path]):
    task_frames = {
        task: load_task_frame(paths[f"{task}_task_frame"], task, expected_rows=EXPECTED_TASK_FRAME_ROWS[task])
        for task in TASKS
    }
    oof_frames = {
        "waist": load_waist_oof(paths["waist_oof"], args.seed),
        "diabetes": load_d0_oof(paths["diabetes_oof"], "diabetes", args.seed),
        "hypertension": load_d0_oof(paths["hypertension_oof"], "hypertension", args.seed),
    }
    for task in TASKS:
        assert_exact_key_match(task_frames[task], oof_frames[task], task, args.seed)
    assert_common_columns_consistent(task_frames)
    return task_frames, oof_frames


def _write_json(path: Path, data: dict) -> None:
    """The single choke point for writing a JSON artifact (summary or
    manifest). Kept as its own function -- rather than an inline
    `path.write_text(...)` -- so a test can spy on it and count, by path,
    exactly how many times each artifact was written.
    """
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")


def _apply_best_effort_posix_permissions(root: Path) -> None:
    """POSIX chmod is best-effort only (a failure here is swallowed, not
    fail-closed).

    This is a deliberate, asymmetric decision versus the Windows ACL repair
    below: a chmod failure on POSIX has never been shown to actually block a
    normal user's read access to the committed output, this repository's
    real deployment target is Windows (see the ACL case), and POSIX mode
    bits are a much weaker/legacy access-control mechanism than an NTFS DACL.
    If that assumption changes, this should become fail-closed too -- but
    today there is no evidence it needs to be.
    """
    try:
        root.chmod(0o755)
        for child in root.rglob("*"):
            child.chmod(0o755 if child.is_dir() else 0o644)
    except OSError:
        pass


def _fix_windows_acl_inheritance(staging_dir: Path) -> None:
    """Windows-only, fail-closed: restore the staging directory's ACL to
    inherit from its parent, before it is committed by rename.

    A tempfile-created directory gets an explicit, non-inherited DACL on
    Windows -- confirmed directly: `Get-Acl` on a tempfile.mkdtemp'd
    directory showed only OWNER RIGHTS/SYSTEM/Administrators, while an
    ordinary sibling directory created the normal way inherited the parent's
    full ACL (including the account/group other team members and sandboxed
    processes use to read anywhere else in this repository). Renaming does
    not by itself fix this -- the DACL travels with the directory -- so this
    must run, and must succeed, before the rename. Unlike the POSIX case
    above, a failure here was proven to cause real, silent unreadability for
    other accounts, so this is fail-closed: any non-zero icacls exit code,
    or a failure to invoke icacls at all, aborts the run (the staging
    directory is discarded by its context manager; output_root is never
    created) instead of being swallowed.

    Runs only when os.name == "nt"; never invokes icacls on other platforms.
    """
    if os.name != "nt":
        return
    # icacls rejects /inheritance:e and /reset combined in one invocation
    # (exit code 87, "invalid parameter") -- confirmed directly. They must be
    # run as two separate commands: first re-enable inheritance in case the
    # staging directory's DACL is marked "protected" (inheritance disabled),
    # then reset it to purely-inherited entries.
    for icacls_args in (["/inheritance:e"], ["/reset", "/T", "/C"]):
        try:
            proc = subprocess.run(
                ["icacls", str(staging_dir), *icacls_args],
                capture_output=True,
                check=False,
            )
        except OSError as exc:
            raise ValidationError(f"failed to invoke icacls for ACL inheritance repair: {exc}") from exc
        if proc.returncode != 0:
            raise ValidationError(
                f"icacls {' '.join(icacls_args)} failed on staging output (exit code {proc.returncode})"
            )


def _verify_final_files_readable(output_root: Path, names: list[str]) -> None:
    """After commit, actually open and read each final file (not just
    os.access), so a permissions problem that would only surface on a real
    read (rather than the access() syscall) is still caught fail-closed.
    """
    for name in names:
        path = output_root / name
        try:
            with path.open("rb") as fh:
                fh.read(1)
        except OSError as exc:
            raise ValidationError(f"committed output file is not readable after commit: {name} ({exc})") from exc


def run(args: argparse.Namespace) -> dict:
    paths = resolve_input_paths(args)

    # 1. 입력 및 OOF fail-closed 검증
    task_frames, oof_frames = _load_and_check_inputs(args, paths)

    # 2. union/three-way frame 생성
    union = build_union_frame(task_frames, oof_frames, args.seed, expected_union_rows=EXPECTED_UNION_ROWS)
    three_way = build_three_way_frame(union, expected_rows=EXPECTED_THREE_WAY_ROWS)

    if args.dry_run:
        summary = build_validation_summary(args.seed, task_frames, union, three_way, args.dry_run, stage="pre_write")
        return {"summary": summary}

    output_root: Path = args.output_root
    # Strict fail-closed: reject if output_root exists at all, even empty --
    # the final commit step below is a single directory rename onto
    # output_root, and a rename target must not already exist.
    if output_root.exists():
        raise ValidationError(f"output_root already exists, refusing to write: {output_root}")

    output_root.parent.mkdir(parents=True, exist_ok=True)

    union_name = f"tuntun_oof_union_seed{args.seed}.csv"
    three_way_name = f"tuntun_oof_three_way_seed{args.seed}.csv"
    summary_name = f"validation_summary_seed{args.seed}.json"
    manifest_name = f"manifest_sha256_seed{args.seed}.json"

    # Staging lives as a sibling of output_root (same parent directory) so the
    # final commit can be a single same-volume directory rename rather than a
    # per-file copy/move -- either the whole result appears atomically or
    # output_root is never created at all.
    with tempfile.TemporaryDirectory(
        dir=output_root.parent, prefix=f".tuntun_build_seed{args.seed}_", ignore_cleanup_errors=True
    ) as staging:
        staging_dir = Path(staging)
        union_staging = staging_dir / union_name
        three_way_staging = staging_dir / three_way_name
        summary_staging = staging_dir / summary_name
        manifest_staging = staging_dir / manifest_name

        # 3. staging에 CSV 저장
        union.to_csv(union_staging, index=False)
        three_way.to_csv(three_way_staging, index=False)

        # 4. CSV 재읽기 검증

        validate_output_contract(
            union_staging,
            EXPECTED_UNION_ROWS,
            args.seed,
            expected_eligible=EXPECTED_TASK_FRAME_ROWS,
            require_all_eligible=False,
        )
        three_way_expected_eligible = {task: EXPECTED_THREE_WAY_ROWS for task in TASKS}
        validate_output_contract(
            three_way_staging,
            EXPECTED_THREE_WAY_ROWS,
            args.seed,
            expected_eligible=three_way_expected_eligible,
            require_all_eligible=True,
        )
        validate_union_three_way_relationship(union_staging, three_way_staging)

        # 5. 최종 summary를 stage=manifest_verified 및 최종 검사 목록으로 한 번만 저장.
        # (이 시점에는 아직 7단계 hash 재검증이 실행되지 않았지만, staging 전체가
        # 이 함수를 정상 종료하고 10단계에서 원자적으로 커밋될 때만 output_root에
        # 나타나므로, 검증이 실제로 실패하면 이 summary를 포함한 staging 전체가
        # 그대로 버려진다 -- "manifest_verified"라고 적힌 summary가 실패 상태로
        # 외부에 노출되는 경우는 없다.)
        summary = build_validation_summary(
            args.seed, task_frames, union, three_way, args.dry_run, stage="manifest_verified"
        )
        _write_json(summary_staging, summary)

        # 6. 최종 CSV와 최종 summary를 대상으로 manifest 생성
        manifest = build_manifest(args.seed, paths, [union_staging, three_way_staging, summary_staging])

        # 7. 입력·코드·최종 출력 hash를 재계산해 manifest 기록과 비교
        verify_manifest_hashes(manifest, paths, staging_dir)

        # 8. 검증 성공 후 manifest 저장
        _write_json(manifest_staging, manifest)

        # 이후 summary나 CSV를 다시 변경하지 않는다 (여기서부터 끝까지 CSV/summary/manifest
        # 파일에 대한 쓰기 없음).

        # 9. ACL 복구 및 성공 확인 (rename 이전에 staging 자체에 적용) -- POSIX는
        # best-effort, Windows는 fail-closed. 실패하면 output_root는 만들어지지 않는다.
        _apply_best_effort_posix_permissions(staging_dir)
        _fix_windows_acl_inheritance(staging_dir)

        # 10. staging 디렉터리 전체를 단 한 번의 동일 볼륨 rename으로 원자적 커밋.
        staging_dir.rename(output_root)

    # 11. 최종 파일을 실제로 열어 읽을 수 있는지 확인.
    _verify_final_files_readable(output_root, [union_name, three_way_name, summary_name, manifest_name])

    # 12. 결과 반환.
    return {
        "summary": summary,
        "outputs": {
            "union_csv": str(output_root / union_name),
            "three_way_csv": str(output_root / three_way_name),
            "validation_summary_json": str(output_root / summary_name),
            "manifest_sha256_json": str(output_root / manifest_name),
        },
    }


def main() -> None:
    args = parse_args()
    try:
        result = run(args)
    except ValidationError as exc:
        print(f"VALIDATION FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
