"""Create task-specific 2019-2021 development frames without fitting or imputation."""

from __future__ import annotations

import argparse
import json
import shutil
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

import pandas as pd

from .experiment_setup import (
    EXPECTED_FEATURES,
    EXPECTED_TASKS,
    assert_development_years,
    load_authorized_config,
)
from .file_integrity import sha256_file, verify_unchanged


KEY_COLUMNS = ["source_year", "participant_id"]
DESIGN_COLUMNS = ["examination_weight", "strata", "psu"]
IMPLEMENTATION_VERSION = "v0.1"


def _boolean(series: pd.Series, name: str, *, expected_missing: int = 0) -> pd.Series:
    missing = int(series.isna().sum())
    if missing != expected_missing:
        raise ValueError(
            f"{name} missing count is {missing}, expected exactly {expected_missing}."
        )
    mapped = series.map(
        lambda value: value
        if isinstance(value, bool)
        else {0: False, 1: True, "0": False, "1": True, "false": False, "true": True}.get(
            str(value).strip().lower() if isinstance(value, str) else value
        )
    )
    nonboolean = mapped.isna() & series.notna()
    if nonboolean.any():
        raise ValueError(f"{name} contains non-boolean values.")
    # Eligibility NA is not feature imputation.  For an exact, predeclared count
    # in the accepted canonical snapshot it means a required measurement was
    # unavailable, hence the row is deterministically outside that task cohort.
    return mapped.astype("boolean").fillna(False).astype(bool)


def validate_canonical_development(frame: pd.DataFrame, config: Mapping[str, Any]) -> None:
    """Validate identity and year boundaries before any task filtering."""

    required = set(KEY_COLUMNS + EXPECTED_FEATURES + DESIGN_COLUMNS)
    for eligibility, label, _ in EXPECTED_TASKS.values():
        required.update((eligibility, label))
    missing = sorted(required - set(frame.columns))
    if missing:
        raise ValueError(f"Canonical development input is missing columns: {missing}")
    years_numeric = pd.to_numeric(frame["source_year"], errors="coerce")
    if years_numeric.isna().any() or years_numeric.mod(1).ne(0).any():
        raise ValueError("source_year must contain non-missing integers.")
    assert_development_years(set(years_numeric.astype(int).unique()), config)
    if frame[KEY_COLUMNS].isna().any().any() or frame.duplicated(KEY_COLUMNS).any():
        raise ValueError("Canonical composite record key is missing or duplicated.")


def build_task_frame(
    canonical: pd.DataFrame, task: str, config: Mapping[str, Any]
) -> pd.DataFrame:
    """Apply eligibility and complete-case rules without altering source values."""

    validate_canonical_development(canonical, config)
    if task not in EXPECTED_TASKS:
        raise ValueError(f"Unknown task: {task}")
    eligibility, label, task_type = EXPECTED_TASKS[task]
    expected_missing = int(
        config.get("canonical_materialization", {})
        .get("accepted_snapshot", {})
        .get("eligibility_missing_expected", {})
        .get(eligibility, 0)
    )
    eligible = _boolean(
        canonical[eligibility], eligibility, expected_missing=expected_missing
    )
    outside = canonical[label].notna() & ~eligible
    # Waist is a row-preserved continuous measurement: canonical ETL intentionally
    # retains an observed value even when the row is outside the P0 target cohort.
    # Disease labels, unlike the raw waist measurement, must never be generated
    # outside their measurement eligibility rules.
    if task_type == "classification" and outside.any():
        raise ValueError(f"Task {task} has labels outside the eligibility rule.")

    complete = canonical[EXPECTED_FEATURES].notna().all(axis=1)
    cohort = canonical.loc[eligible & canonical[label].notna() & complete].copy()
    if cohort.empty:
        raise ValueError(f"Task {task} has no eligible complete-case development rows.")
    label_numeric = pd.to_numeric(cohort[label], errors="coerce")
    if label_numeric.isna().any():
        raise ValueError(f"Task {task} label must be numeric.")
    if task_type == "classification" and not set(label_numeric.unique()).issubset({0, 1}):
        raise ValueError(f"Task {task} label contains a value outside 0/1.")
    cohort[label] = label_numeric
    columns = [*KEY_COLUMNS, *EXPECTED_FEATURES, *DESIGN_COLUMNS, label]
    result = cohort.loc[:, columns].reset_index(drop=True)
    if result[EXPECTED_FEATURES].isna().any().any():
        raise AssertionError("Complete-case preparation unexpectedly retained missing features.")
    return result


def _write_json(path: Path, payload: Mapping[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def materialize_development_frames(
    config_path: Path, canonical_path: Path, output_root: Path
) -> Path:
    """Atomically write all three task frames and a hash manifest."""

    config_path = config_path.resolve()
    canonical_path = canonical_path.resolve()
    output_root = output_root.resolve()
    config = load_authorized_config(config_path)
    version = str(config["output_version"])
    final = output_root / version
    if final.exists():
        raise FileExistsError(f"Output version already exists; overwrite is prohibited: {final}")
    input_hash = sha256_file(canonical_path)
    config_hash = sha256_file(config_path)
    canonical_contract = config.get("canonical_materialization", {})
    accepted = canonical_contract.get("accepted_snapshot", {})
    if not accepted or input_hash != accepted.get("sha256"):
        raise ValueError(
            "Canonical input is not the exact accepted snapshot declared by the authorized config."
        )
    canonical = pd.read_csv(canonical_path, low_memory=False)
    if len(canonical) != int(accepted.get("rows", -1)):
        raise ValueError("Canonical input row count differs from the accepted snapshot.")
    validate_canonical_development(canonical, config)

    output_root.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix=f".{version}-", dir=output_root))
    try:
        files: dict[str, dict[str, Any]] = {}
        for task in EXPECTED_TASKS:
            task_frame = build_task_frame(canonical, task, config)
            relative = Path(task) / "development_2019_2021.csv"
            (stage / task).mkdir(parents=True, exist_ok=True)
            task_frame.to_csv(stage / relative, index=False, encoding="utf-8-sig", lineterminator="\n")
            files[relative.as_posix()] = {
                "rows": int(len(task_frame)),
                "sha256": sha256_file(stage / relative),
            }
        _write_json(
            stage / "manifest.json",
            {
                "schema_version": 1,
                "implementation_version": IMPLEMENTATION_VERSION,
                "output_version": version,
                "source_years": [2019, 2020, 2021],
                "input_sha256": input_hash,
                "config_sha256": config_hash,
                "policy": {
                    "feature_imputation_performed": False,
                    "training_performed": False,
                    "evaluation_performed": False,
                    "reserved_year_accessed": False,
                    "eligibility_missing_mapped_to_ineligible": config.get(
                        "canonical_materialization", {}
                    )
                    .get("accepted_snapshot", {})
                    .get("eligibility_missing_expected", {}),
                },
                "files": files,
            },
        )
        verify_unchanged(canonical_path, input_hash)
        verify_unchanged(config_path, config_hash)
        stage.replace(final)
    except BaseException:
        shutil.rmtree(stage, ignore_errors=True)
        raise
    return final


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--canonical", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    final = materialize_development_frames(args.config, args.canonical, args.output_root)
    print(f"Development task frames created: {final}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
