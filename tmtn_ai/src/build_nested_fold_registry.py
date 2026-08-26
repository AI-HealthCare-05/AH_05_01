"""Build deterministic nested-fold key registries for development-only task frames."""

from __future__ import annotations

import argparse
import json
import shutil
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

import pandas as pd
from sklearn.model_selection import StratifiedKFold

from .experiment_setup import EXPECTED_TASKS, load_authorized_config
from .file_integrity import sha256_file, verify_unchanged
from .prepare_development_frames import KEY_COLUMNS


def _candidate_strata(frame: pd.DataFrame, label: str, task_type: str) -> list[pd.Series]:
    year = frame["source_year"].astype(str)
    sex = frame["sex_code"].astype(str)
    if task_type == "classification":
        base = pd.to_numeric(frame[label], errors="raise").astype(int).astype(str)
    else:
        numeric = pd.to_numeric(frame[label], errors="raise")
        bins = min(5, len(frame))
        if bins < 2:
            raise ValueError("Regression fold creation needs at least two rows.")
        base = pd.qcut(numeric.rank(method="first"), q=bins, labels=False).astype(str)
    return [base + "|" + year + "|" + sex, base + "|" + year, base + "|" + sex, base]


def _strata(frame: pd.DataFrame, label: str, task_type: str, folds: int) -> pd.Series:
    for candidate in _candidate_strata(frame, label, task_type):
        if candidate.value_counts().min() >= folds:
            return candidate
    raise ValueError(
        f"No declared stratification fallback has at least {folds} rows per stratum."
    )


def build_nested_registries(
    task_frame: pd.DataFrame,
    *,
    label: str,
    task_type: str,
    outer_folds: int,
    inner_folds: int,
    seed: int,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Return one outer registry and outer-train-specific inner registries."""

    required = set(KEY_COLUMNS + ["sex_code", label])
    missing = sorted(required - set(task_frame.columns))
    if missing:
        raise ValueError(f"Task frame is missing fold columns: {missing}")
    if task_frame[KEY_COLUMNS].isna().any().any() or task_frame.duplicated(KEY_COLUMNS).any():
        raise ValueError("Task-frame keys are missing or duplicated.")
    if set(pd.to_numeric(task_frame["source_year"], errors="raise").astype(int).unique()) - {
        2019,
        2020,
        2021,
    }:
        raise ValueError("Fold registry may use only 2019-2021 development rows.")

    # Fold assignment must be a function of the frozen keys and seed, not of the
    # incidental CSV row order presented by a caller.
    task_frame = task_frame.sort_values(KEY_COLUMNS, kind="stable").reset_index(drop=True)

    outer_strata = _strata(task_frame, label, task_type, outer_folds)
    outer_assignment = pd.Series(index=task_frame.index, dtype="int16")
    outer_splitter = StratifiedKFold(n_splits=outer_folds, shuffle=True, random_state=seed)
    for fold, (_, holdout_idx) in enumerate(outer_splitter.split(task_frame, outer_strata)):
        outer_assignment.iloc[holdout_idx] = fold
    outer = task_frame.loc[:, KEY_COLUMNS].copy()
    outer["seed"] = seed
    outer["outer_fold"] = outer_assignment.astype(int)

    inner_parts: list[pd.DataFrame] = []
    for outer_fold in range(outer_folds):
        train_mask = outer_assignment.ne(outer_fold)
        train = task_frame.loc[train_mask]
        inner_strata = _strata(train, label, task_type, inner_folds)
        splitter = StratifiedKFold(
            n_splits=inner_folds,
            shuffle=True,
            random_state=seed + 10_000 + outer_fold,
        )
        assignment = pd.Series(index=train.index, dtype="int16")
        for inner_fold, (_, holdout_pos) in enumerate(splitter.split(train, inner_strata)):
            assignment.iloc[holdout_pos] = inner_fold
        part = train.loc[:, KEY_COLUMNS].copy()
        part["seed"] = seed
        part["outer_fold"] = outer_fold
        part["inner_fold"] = assignment.astype(int)
        inner_parts.append(part)
    inner = pd.concat(inner_parts, ignore_index=True)

    key_tuples = set(map(tuple, task_frame[KEY_COLUMNS].to_numpy()))
    if set(map(tuple, outer[KEY_COLUMNS].to_numpy())) != key_tuples:
        raise AssertionError("Outer registry does not cover task-frame keys exactly once.")
    for outer_fold in range(outer_folds):
        holdout = set(
            map(tuple, outer.loc[outer["outer_fold"].eq(outer_fold), KEY_COLUMNS].to_numpy())
        )
        inner_keys = set(
            map(tuple, inner.loc[inner["outer_fold"].eq(outer_fold), KEY_COLUMNS].to_numpy())
        )
        if holdout & inner_keys or holdout | inner_keys != key_tuples:
            raise AssertionError("Outer holdout and inner-training keys are not disjoint/exhaustive.")
    return outer.reset_index(drop=True), inner.reset_index(drop=True)


def materialize_fold_registries(
    config_path: Path, task: str, task_frame_path: Path, output_root: Path
) -> Path:
    config = load_authorized_config(config_path.resolve())
    if task not in EXPECTED_TASKS:
        raise ValueError(f"Unknown task: {task}")
    input_hash = sha256_file(task_frame_path.resolve())
    config_hash = sha256_file(config_path.resolve())
    frame = pd.read_csv(task_frame_path, low_memory=False)
    eligibility, label, task_type = EXPECTED_TASKS[task]
    del eligibility
    resampling = config["resampling"]
    version = str(config["output_version"])
    final = output_root.resolve() / version / task
    if final.exists():
        raise FileExistsError(f"Fold registry already exists; overwrite is prohibited: {final}")
    final.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix=f".{task}-", dir=final.parent))
    try:
        files: dict[str, dict[str, Any]] = {}
        for seed in resampling["stability_seeds"]:
            outer, inner = build_nested_registries(
                frame,
                label=label,
                task_type=task_type,
                outer_folds=int(resampling["outer_folds"]),
                inner_folds=int(resampling["inner_folds"]),
                seed=int(seed),
            )
            for name, registry in (("outer", outer), ("inner", inner)):
                relative = Path(f"seed_{seed}") / f"{name}_fold_registry.csv"
                (stage / relative.parent).mkdir(parents=True, exist_ok=True)
                registry.to_csv(stage / relative, index=False, encoding="utf-8-sig", lineterminator="\n")
                files[relative.as_posix()] = {
                    "rows": int(len(registry)),
                    "sha256": sha256_file(stage / relative),
                }
        (stage / "manifest.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "task": task,
                    "source_years": [2019, 2020, 2021],
                    "task_frame_sha256": input_hash,
                    "config_sha256": config_hash,
                    "training_performed": False,
                    "files": files,
                },
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        verify_unchanged(task_frame_path.resolve(), input_hash)
        verify_unchanged(config_path.resolve(), config_hash)
        stage.replace(final)
    except BaseException:
        shutil.rmtree(stage, ignore_errors=True)
        raise
    return final


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--task", choices=sorted(EXPECTED_TASKS), required=True)
    parser.add_argument("--task-frame", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    final = materialize_fold_registries(args.config, args.task, args.task_frame, args.output_root)
    print(f"Nested fold registry created: {final}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
