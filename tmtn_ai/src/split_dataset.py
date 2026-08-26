"""CLI for random development, temporal validation, and locked internal testing."""

from __future__ import annotations

import argparse
import json
import logging
import shutil
import tempfile
import uuid
from pathlib import Path
from typing import Sequence

import pandas as pd
from sklearn.model_selection import train_test_split

from .file_integrity import collect_code_version, sha256_file, verify_unchanged
from .split_validation import (
    SplitConfig,
    load_config,
    validate_source_data,
    validate_split_integrity,
)


LOGGER = logging.getLogger(__name__)


def _regression_strata(target: pd.Series, bins: int) -> pd.Series:
    """Create quantile strata for approximate regression-target stratification."""
    try:
        strata = pd.qcut(target, q=bins, labels=False, duplicates="drop")
    except (TypeError, ValueError) as exc:
        raise ValueError(
            "Could not form regression quantile bins. Ensure the target is numeric and has "
            "enough distinct values, or set regression_stratify: none."
        ) from exc
    if strata.nunique(dropna=False) < 2 or strata.isna().any():
        raise ValueError(
            "Regression quantile stratification produced fewer than two valid bins. "
            "Reduce regression_stratify_bins or set regression_stratify: none."
        )
    if strata.value_counts().min() < 2:
        raise ValueError(
            "A regression quantile bin has fewer than two rows. Reduce "
            "regression_stratify_bins or provide a larger development cohort."
        )
    return strata


def build_splits(
    frame: pd.DataFrame, config: SplitConfig
) -> dict[str, pd.DataFrame]:
    """Build deterministic development, temporal-validation, and locked-test frames."""
    development = frame[frame[config.year_column].isin(config.development_years)].copy()
    temporal_train = development.copy()
    temporal_validation = frame[
        frame[config.year_column].isin(config.temporal_validation_years)
    ].copy()
    locked_test = frame[frame[config.year_column].isin(config.locked_test_years)].copy()

    stratify: pd.Series | None = None
    if config.task_type == "classification" and config.classification_stratify:
        stratify = development[config.label_column]
    elif config.task_type == "regression" and config.regression_stratify == "quantile":
        stratify = _regression_strata(
            development[config.label_column], config.regression_stratify_bins
        )

    try:
        random_train, random_validation = train_test_split(
            development,
            test_size=config.random_validation_size,
            random_state=config.random_seed,
            shuffle=True,
            stratify=stratify,
        )
    except ValueError as exc:
        raise ValueError(
            "Random split failed, usually because a stratum has too few rows for the requested "
            "validation size. Enlarge the cohort, adjust the validation size/bins, or disable "
            f"stratification. Original error: {exc}"
        ) from exc

    splits = {
        "random_train": random_train.reset_index(drop=True),
        "random_validation": random_validation.reset_index(drop=True),
        "temporal_train": temporal_train.reset_index(drop=True),
        "temporal_validation": temporal_validation.reset_index(drop=True),
        "locked_test": locked_test.reset_index(drop=True),
    }
    validate_split_integrity(config=config, **splits)
    return splits


def _write_json(path: Path, payload: dict[str, object]) -> None:
    """Write stable, human-readable UTF-8 JSON."""
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def _write_staged_outputs(
    staging_root: Path,
    sealed_staging_root: Path,
    splits: dict[str, pd.DataFrame],
    config: SplitConfig,
    config_path: Path,
    input_path: Path,
    input_sha256: str,
    config_sha256: str,
) -> None:
    """Write development outputs and a separately staged locked-test answer key."""
    relative_paths = {
        "random_train": Path("development/random/train.csv"),
        "random_validation": Path("development/random/validation.csv"),
        "temporal_train": Path("development/temporal/train.csv"),
        "temporal_validation": Path("development/temporal/validation.csv"),
        "locked_test_features": Path(
            "locked_internal_test/"
            f"test_{'_'.join(map(str, config.locked_test_years))}_features.csv"
        ),
    }
    for relative_path in relative_paths.values():
        (staging_root / relative_path).parent.mkdir(parents=True, exist_ok=True)
    manifests_dir = staging_root / "manifests"
    manifests_dir.mkdir(parents=True, exist_ok=True)

    for name in [
        "random_train",
        "random_validation",
        "temporal_train",
        "temporal_validation",
    ]:
        frame = splits[name].loc[:, config.development_columns]
        frame.to_csv(
            staging_root / relative_paths[name],
            index=False,
            encoding=config.csv_encoding,
            lineterminator="\n",
        )

    locked_test_features = splits["locked_test"].loc[:, config.locked_test_feature_columns]
    locked_test_features.to_csv(
        staging_root / relative_paths["locked_test_features"],
        index=False,
        encoding=config.csv_encoding,
        lineterminator="\n",
    )

    sealed_staging_root.mkdir(parents=True, exist_ok=True)
    locked_label_filename = (
        f"test_{'_'.join(map(str, config.locked_test_years))}_labels.csv"
    )
    locked_label_path = sealed_staging_root / locked_label_filename
    locked_test_labels = splits["locked_test"].loc[:, config.locked_test_label_columns]
    locked_test_labels.to_csv(
        locked_label_path,
        index=False,
        encoding=config.csv_encoding,
        lineterminator="\n",
    )

    snapshot_path = manifests_dir / "config_snapshot.yaml"
    shutil.copyfile(config_path, snapshot_path)
    output_entries: dict[str, dict[str, object]] = {}
    output_frames = {
        **{
            name: splits[name]
            for name in [
                "random_train",
                "random_validation",
                "temporal_train",
                "temporal_validation",
            ]
        },
        "locked_test_features": locked_test_features,
    }
    for name, relative_path in relative_paths.items():
        output_entries[relative_path.as_posix()] = {
            "rows": int(len(output_frames[name])),
            "sha256": sha256_file(staging_root / relative_path),
        }

    split_manifest_path = manifests_dir / "split_manifest.json"
    split_manifest: dict[str, object] = {
        "schema_version": 3,
        "task": config.task,
        "task_type": config.task_type,
        "dataset_version": config.dataset_version,
        "year_policy": {
            "development_years": list(config.development_years),
            "temporal_train_years": list(config.development_years),
            "temporal_validation_years": list(config.temporal_validation_years),
            "locked_test_years": list(config.locked_test_years),
            "forbidden_years": list(config.forbidden_years),
        },
        "random_split": {
            "validation_size": config.random_validation_size,
            "random_seed": config.random_seed,
            "classification_stratify": config.classification_stratify,
            "regression_stratify": config.regression_stratify,
            "regression_stratify_bins": config.regression_stratify_bins,
        },
        "outputs": output_entries,
        "privacy": {
            "locked_test_labels_in_developer_outputs": False,
            "locked_test_answer_key_stored_separately": True,
            "locked_test_label_statistics_included": False,
            "row_examples_included": False,
        },
    }
    _write_json(split_manifest_path, split_manifest)

    artifact_hashes = {
        relative_path: details["sha256"] for relative_path, details in output_entries.items()
    }
    artifact_hashes["manifests/config_snapshot.yaml"] = sha256_file(snapshot_path)
    artifact_hashes["manifests/split_manifest.json"] = sha256_file(split_manifest_path)
    integrity_manifest: dict[str, object] = {
        "schema_version": 3,
        "input": {"filename": input_path.name, "sha256": input_sha256},
        "configuration": {"filename": config_path.name, "sha256": config_sha256},
        "code_version": collect_code_version(Path(__file__).resolve().parent),
        "artifact_sha256": artifact_hashes,
        "note": "This manifest intentionally contains no label distributions or row examples.",
    }
    _write_json(manifests_dir / "integrity_manifest.json", integrity_manifest)

    sealed_manifest: dict[str, object] = {
        "schema_version": 1,
        "task": config.task,
        "dataset_version": config.dataset_version,
        "locked_test_years": list(config.locked_test_years),
        "answer_key": {
            "filename": locked_label_filename,
            "rows": int(len(locked_test_labels)),
            "sha256": sha256_file(locked_label_path),
        },
        "input_sha256": input_sha256,
        "configuration_sha256": config_sha256,
        "note": "Restricted internal-test answer key: release only after model and calibration freeze.",
    }
    _write_json(sealed_staging_root / "sealed_manifest.json", sealed_manifest)


def _commit_staging_pair(
    staging_roots: tuple[Path, Path], final_roots: tuple[Path, Path], force: bool
) -> None:
    """Publish development and answer-key directories together with rollback protection."""
    for final_root in final_roots:
        if final_root.exists() and not final_root.is_dir():
            raise FileExistsError(
                f"Output target exists and is not a directory: {final_root}. Choose another output."
            )
        if final_root.exists() and not force:
            raise FileExistsError(
                f"Output directory already exists: {final_root}. Use --force only after "
                "confirming that replacing this exact task/version output is intended."
            )

    backups: dict[Path, Path] = {}
    published: list[Path] = []
    try:
        for final_root in final_roots:
            if final_root.exists():
                backup = final_root.with_name(
                    f".{final_root.name}.backup-{uuid.uuid4().hex}"
                )
                final_root.replace(backup)
                backups[final_root] = backup
        for staging_root, final_root in zip(staging_roots, final_roots, strict=True):
            staging_root.replace(final_root)
            published.append(final_root)
    except Exception:
        for final_root in reversed(published):
            if final_root.exists():
                shutil.rmtree(final_root)
        for final_root, backup in backups.items():
            if backup.exists() and not final_root.exists():
                backup.replace(final_root)
        raise
    for backup in backups.values():
        if backup.exists():
            shutil.rmtree(backup)


def _paths_overlap(left: Path, right: Path) -> bool:
    """Return true when two resolved paths are equal or nested in either direction."""
    return left == right or left in right.parents or right in left.parents


def run_split_pipeline(
    config_path: Path,
    input_path: Path,
    output_root: Path,
    locked_label_output_root: Path,
    force: bool = False,
) -> Path:
    """Validate, split, and publish data plus a separate locked-test answer key."""
    config_path = config_path.resolve()
    input_path = input_path.resolve()
    output_root = output_root.resolve()
    locked_label_output_root = locked_label_output_root.resolve()
    if _paths_overlap(output_root, locked_label_output_root):
        raise ValueError(
            "--locked-label-output must be a separate, non-nested root outside --output. "
            "Use a restricted directory controlled by the evaluation custodian."
        )
    config = load_config(config_path)
    if config.do_not_split:
        raise RuntimeError(
            "This configuration has do_not_split: true. Jointly approve the canonical contract, "
            "assign the locked-test custodian, then create a new authorized config version."
        )
    if not input_path.is_file():
        raise FileNotFoundError(
            f"Input CSV does not exist: {input_path}. Supply an existing --input file path."
        )
    final_root = output_root / config.task / config.dataset_version
    sealed_final_root = locked_label_output_root / config.task / config.dataset_version
    existing_roots = [path for path in (final_root, sealed_final_root) if path.exists()]
    if existing_roots and not force:
        raise FileExistsError(
            f"Output directory already exists: {existing_roots[0]}. Use --force only for an "
            "intentional replacement. The input file was not read."
        )

    input_sha256 = sha256_file(input_path)
    config_sha256 = sha256_file(config_path)
    LOGGER.info("Validating configured columns, IDs, years, and labels.")
    try:
        raw = pd.read_csv(input_path, encoding=config.csv_encoding)
    except (OSError, UnicodeError, pd.errors.ParserError) as exc:
        raise ValueError(
            f"Could not read input CSV {input_path} with encoding {config.csv_encoding}: {exc}. "
            "Confirm the encoding and CSV structure without modifying the original."
        ) from exc
    frame = validate_source_data(raw, config)
    del raw
    splits = build_splits(frame, config)
    LOGGER.info(
        "Year-role splits validated; locked-test labels will be written only to the restricted root."
    )

    final_root.parent.mkdir(parents=True, exist_ok=True)
    sealed_final_root.parent.mkdir(parents=True, exist_ok=True)
    staging_root = Path(
        tempfile.mkdtemp(prefix=f".{config.dataset_version}.staging-", dir=final_root.parent)
    )
    sealed_staging_root = Path(
        tempfile.mkdtemp(
            prefix=f".{config.dataset_version}.sealed-staging-",
            dir=sealed_final_root.parent,
        )
    )
    committed = False
    try:
        _write_staged_outputs(
            staging_root=staging_root,
            sealed_staging_root=sealed_staging_root,
            splits=splits,
            config=config,
            config_path=config_path,
            input_path=input_path,
            input_sha256=input_sha256,
            config_sha256=config_sha256,
        )
        verify_unchanged(input_path, input_sha256)
        if sha256_file(config_path) != config_sha256:
            raise RuntimeError(
                "Configuration changed during execution. Restore it and rerun; outputs were not committed."
            )
        _commit_staging_pair(
            (sealed_staging_root, staging_root),
            (sealed_final_root, final_root),
            force,
        )
        committed = True
    finally:
        if not committed and staging_root.exists():
            shutil.rmtree(staging_root)
        if not committed and sealed_staging_root.exists():
            shutil.rmtree(sealed_staging_root)

    LOGGER.info("Split artifacts committed to %s", final_root)
    return final_root


def build_argument_parser() -> argparse.ArgumentParser:
    """Construct the command-line argument parser."""
    parser = argparse.ArgumentParser(
        description="Create random development, temporal validation, and locked-test CSV splits."
    )
    parser.add_argument("--config", required=True, type=Path, help="Path to split YAML.")
    parser.add_argument("--input", required=True, type=Path, help="Path to source modeling CSV.")
    parser.add_argument("--output", required=True, type=Path, help="Root directory for outputs.")
    parser.add_argument(
        "--locked-label-output",
        required=True,
        type=Path,
        help="Separate restricted root for the locked-test answer key; cannot overlap --output.",
    )
    parser.add_argument(
        "--force", action="store_true", help="Replace the exact existing task/version output."
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    """Run the split CLI and return a process status code."""
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    args = build_argument_parser().parse_args(argv)
    try:
        run_split_pipeline(
            args.config,
            args.input,
            args.output,
            args.locked_label_output,
            args.force,
        )
    except (FileNotFoundError, FileExistsError, ValueError, RuntimeError) as exc:
        LOGGER.error("%s", exc)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
