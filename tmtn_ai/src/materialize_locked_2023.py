"""Custodian-only packaging of KNHANES 2023 locked-test features and labels."""

from __future__ import annotations

import argparse
import json
import logging
import shutil
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

import pandas as pd
import yaml

from .audit_raw_values import read_value_columns
from .canonical_etl import REQUIRED_CANONICAL_INPUT_COLUMNS
from .file_integrity import collect_code_version, sha256_file, verify_unchanged
from .locked_2023_canonical import build_locked_2023_canonical_frame
from .split_dataset import _commit_staging_pair, _paths_overlap


LOGGER = logging.getLogger(__name__)
IMPLEMENTATION_VERSION = "v0.1-custodian-only"
KEY_COLUMNS = ["participant_id", "source_year"]
EXPECTED_FEATURES = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]
EXPECTED_TASKS = {
    "waist": ("waist_target_eligible", "waist_cm"),
    "diabetes": (
        "diabetes_target_eligible",
        "diabetes_measurement_label_raw",
    ),
    "hypertension": (
        "hypertension_target_eligible",
        "hypertension_measurement_label_raw",
    ),
}


def load_materialization_contract(path: Path) -> dict[str, Any]:
    """Load and fail closed on any materialization-contract drift."""

    raw = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
    if not isinstance(raw, Mapping):
        raise ValueError("Locked materialization contract must be a YAML mapping.")
    if raw.get("do_not_train") is not True:
        raise ValueError("Locked materialization requires do_not_train: true.")
    scope = raw.get("scope")
    if not isinstance(scope, Mapping) or scope.get("source_year") != 2023:
        raise ValueError("Locked materialization source_year must be exactly 2023.")
    if list(raw.get("features", [])) != EXPECTED_FEATURES:
        raise ValueError("Materialization feature contract does not match the frozen six features.")
    tasks = raw.get("tasks")
    if not isinstance(tasks, Mapping) or set(tasks) != set(EXPECTED_TASKS):
        raise ValueError("Materialization tasks must be exactly waist, diabetes, hypertension.")
    for task, (eligibility, label) in EXPECTED_TASKS.items():
        declared = tasks[task]
        if not isinstance(declared, Mapping) or declared.get("eligibility") != eligibility:
            raise ValueError(f"Task '{task}' eligibility contract mismatch.")
        if declared.get("label") != label:
            raise ValueError(f"Task '{task}' label contract mismatch.")
    return dict(raw)


def _write_json(path: Path, payload: Mapping[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def _validate_record_identity(canonical: pd.DataFrame) -> None:
    if canonical[KEY_COLUMNS].isna().any().any():
        raise ValueError("2023 canonical composite record key contains missing values.")
    if canonical.duplicated(KEY_COLUMNS).any():
        raise ValueError("2023 canonical composite record key contains duplicates.")


def _task_cohort(
    canonical: pd.DataFrame, task: str
) -> tuple[pd.DataFrame, pd.DataFrame]:
    eligibility_column, label_column = EXPECTED_TASKS[task]
    eligible = canonical[eligibility_column].fillna(False).astype(bool)
    outside = canonical[label_column].notna() & ~eligible
    if task != "waist" and outside.any():
        raise ValueError(f"Task '{task}' has labels outside its eligibility rule.")
    cohort = canonical.loc[eligible & canonical[label_column].notna()].copy()
    if cohort.empty:
        raise ValueError(f"Task '{task}' has no eligible labeled 2023 rows.")
    features = cohort.loc[:, [*KEY_COLUMNS, *EXPECTED_FEATURES]]
    labels = cohort.loc[:, [*KEY_COLUMNS, label_column]]
    if task != "waist" and not set(labels[label_column].dropna().unique()).issubset({0, 1}):
        raise ValueError(f"Task '{task}' label contains a value outside 0/1.")
    return features, labels


def materialize_locked_2023(
    contract_path: Path,
    raw_path: Path,
    developer_output_root: Path,
    locked_label_output_root: Path,
) -> tuple[Path, Path]:
    """Create two non-overlapping atomic packages without model evaluation."""

    contract_path = contract_path.resolve()
    raw_path = raw_path.resolve()
    developer_output_root = developer_output_root.resolve()
    locked_label_output_root = locked_label_output_root.resolve()
    if _paths_overlap(developer_output_root, locked_label_output_root):
        raise ValueError("Developer and locked-label output roots must be separate and non-nested.")

    contract = load_materialization_contract(contract_path)
    if contract.get("do_not_materialize") is True:
        raise RuntimeError(
            "Contract has do_not_materialize: true. The custodian must review permissions and "
            "use a newly approved contract version before any 2023 file is read."
        )
    if not raw_path.is_file():
        raise FileNotFoundError(f"2023 raw file does not exist: {raw_path}")

    version = str(contract["materialization_version"])
    final_developer = developer_output_root / version
    final_locked = locked_label_output_root / version
    if final_developer.exists() or final_locked.exists():
        raise FileExistsError(
            "The exact materialization version already exists. Create a new version; overwrite is prohibited."
        )

    raw_sha256 = sha256_file(raw_path)
    contract_sha256 = sha256_file(contract_path)
    raw, metadata = read_value_columns(
        raw_path, set(REQUIRED_CANONICAL_INPUT_COLUMNS), set()
    )
    canonical = build_locked_2023_canonical_frame(raw)
    del raw
    _validate_record_identity(canonical)

    final_developer.parent.mkdir(parents=True, exist_ok=True)
    final_locked.parent.mkdir(parents=True, exist_ok=True)
    developer_stage = Path(
        tempfile.mkdtemp(prefix=f".{version}.developer-", dir=final_developer.parent)
    )
    locked_stage = Path(
        tempfile.mkdtemp(prefix=f".{version}.locked-", dir=final_locked.parent)
    )
    committed = False
    try:
        developer_files: dict[str, dict[str, Any]] = {}
        locked_files: dict[str, dict[str, Any]] = {}
        for task in EXPECTED_TASKS:
            features, labels = _task_cohort(canonical, task)
            feature_relative = Path(task) / "test_2023_features.csv"
            label_relative = Path(task) / "test_2023_labels.csv"
            (developer_stage / task).mkdir(parents=True, exist_ok=True)
            (locked_stage / task).mkdir(parents=True, exist_ok=True)
            features.to_csv(
                developer_stage / feature_relative,
                index=False,
                encoding="utf-8-sig",
                lineterminator="\n",
            )
            labels.to_csv(
                locked_stage / label_relative,
                index=False,
                encoding="utf-8-sig",
                lineterminator="\n",
            )
            developer_files[feature_relative.as_posix()] = {
                "rows": int(len(features)),
                "sha256": sha256_file(developer_stage / feature_relative),
            }
            locked_files[label_relative.as_posix()] = {
                "rows": int(len(labels)),
                "sha256": sha256_file(locked_stage / label_relative),
            }

        implementation = Path(__file__).resolve()
        code_version = collect_code_version(implementation.parent)
        common = {
            "schema_version": 1,
            "materialization_version": version,
            "source": {
                "filename": raw_path.name,
                "sha256": raw_sha256,
                "file_format": metadata.get("file_format"),
                "encoding": metadata.get("encoding"),
            },
            "contract": {"filename": contract_path.name, "sha256": contract_sha256},
            "implementation": {
                "version": IMPLEMENTATION_VERSION,
                "code_version": code_version,
                "materializer_sha256": sha256_file(implementation),
                "locked_extension_sha256": sha256_file(
                    implementation.with_name("locked_2023_canonical.py")
                ),
                "frozen_canonical_sha256": sha256_file(
                    implementation.with_name("canonical_etl.py")
                ),
            },
            "policy": {
                "source_year": 2023,
                "full_canonical_rows_exported": False,
                "label_statistics_included": False,
                "sample_rows_included": False,
                "model_training_performed": False,
                "model_evaluation_performed": False,
            },
        }
        _write_json(
            developer_stage / "developer_manifest.json",
            {**common, "package_role": "developer_features_only", "files": developer_files},
        )
        _write_json(
            locked_stage / "locked_manifest.json",
            {**common, "package_role": "custodian_labels_only", "files": locked_files},
        )
        shutil.copyfile(contract_path, developer_stage / "contract_snapshot.yaml")
        shutil.copyfile(contract_path, locked_stage / "contract_snapshot.yaml")

        verify_unchanged(raw_path, raw_sha256)
        if sha256_file(contract_path) != contract_sha256:
            raise RuntimeError("Materialization contract changed during execution.")
        _commit_staging_pair(
            (locked_stage, developer_stage),
            (final_locked, final_developer),
            False,
        )
        committed = True
    finally:
        del canonical
        if not committed and developer_stage.exists():
            shutil.rmtree(developer_stage)
        if not committed and locked_stage.exists():
            shutil.rmtree(locked_stage)

    LOGGER.info("Locked 2023 packages created without label statistics or model evaluation.")
    return final_developer, final_locked


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Custodian-only KNHANES 2023 canonical task packaging."
    )
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--raw-2023", required=True, type=Path)
    parser.add_argument("--developer-output", required=True, type=Path)
    parser.add_argument("--locked-label-output", required=True, type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    args = build_parser().parse_args(argv)
    try:
        materialize_locked_2023(
            args.contract,
            args.raw_2023,
            args.developer_output,
            args.locked_label_output,
        )
    except (FileNotFoundError, FileExistsError, RuntimeError, ValueError) as exc:
        LOGGER.error("%s", exc)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
