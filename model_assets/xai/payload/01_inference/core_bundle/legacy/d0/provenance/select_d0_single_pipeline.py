"""Select one fixed D0 base-model + calibrator combination per disease task.

This is a development-only, aggregate-only evaluator governed by
contracts/d0_single_pipeline_selection_v0_1_owner_approved.yaml.  It never
uses 2022+, never writes row-level predictions, and never serves OOF values.
Long evaluations checkpoint one aggregate JSON per task/seed/base candidate;
the final result is committed with one directory rename only after the full
46 x 2 x 2 x 3 grid and its manifest validate.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import shutil
import sys
import tempfile
import time
from pathlib import Path

import numpy as np
import pandas as pd
import yaml

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from src import disease_model_pipeline as dmp

SEEDS = (42, 1042, 2042)
TASKS = {
    "diabetes": "diabetes_measurement_label_raw",
    "hypertension": "hypertension_measurement_label_raw",
}
FEATURES = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]
CONTRACT_REL = Path("contracts/d0_single_pipeline_selection_v0_1_owner_approved.yaml")
EXPECTED_METRIC_ROWS = len(TASKS) * len(SEEDS) * 46 * 2
EXPECTED_RANKING_ROWS = len(TASKS) * 46 * 2


class SelectionError(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def canonical_json_hash(value: object) -> str:
    payload = json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(payload).hexdigest()


def atomic_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(tmp, path)


def load_contract(path: Path) -> dict:
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (yaml.YAMLError, UnicodeError) as exc:
        raise SelectionError("invalid selection contract YAML") from exc
    if not isinstance(data, dict):
        raise SelectionError("selection contract must be a mapping")
    # Bind every field, including tie-break order, to the owner-approved file.
    # A new contract requires an explicit version/hash update, not coercion.
    if sha256_file(path) != "31e72cbbc9583239877a4784fcca65426a435f94c2eabe18e4598f5080afbd24":
        raise SelectionError("selection contract differs from the approved v0.1 bytes")
    expected = {
        "contract_name": "d0_single_pipeline_selection",
        "contract_version": "v0.1",
        "status": "owner_approved_for_d0_only_mvp_selection",
        "approved_by": "project_owner",
    }
    for key, value in expected.items():
        if data.get(key) != value:
            raise SelectionError(f"contract {key} must equal {value!r}")
    scope = data.get("scope", {})
    if scope.get("tasks") != ["diabetes", "hypertension"]:
        raise SelectionError("contract task order mismatch")
    if scope.get("seeds") != list(SEEDS) or scope.get("candidate_count") != 46:
        raise SelectionError("contract seed/candidate count mismatch")
    if scope.get("calibrators") != ["identity", "platt"]:
        raise SelectionError("contract calibrator set mismatch")
    ranking = data.get("ranking", {})
    if ranking.get("primary") != "three_seed_mean_calibrated_brier":
        raise SelectionError("unexpected ranking primary")
    if float(ranking.get("equivalence_margin_from_best")) != 0.0005:
        raise SelectionError("equivalence margin must be 0.0005")
    if float(ranking.get("stability_guard", {}).get("maximum_inclusive")) != 0.001:
        raise SelectionError("stability maximum must be 0.001")
    if data.get("post_selection", {}).get("production_release_gate") != "BLOCKED":
        raise SelectionError("production release gate must remain BLOCKED")
    return data


def validate_candidate_grid(candidates: list[dict]) -> None:
    if len(candidates) != 46:
        raise SelectionError(f"approved base grid must contain 46 candidates, found {len(candidates)}")
    names = [dmp.candidate_name(c) for c in candidates]
    if len(set(names)) != 46:
        raise SelectionError("candidate names are not unique")


def load_inputs(task_frame_root: Path, fold_root: Path, task: str, seed: int) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    task_path = task_frame_root / task / "development_2019_2021.csv"
    seed_dir = fold_root / task / f"seed_{seed}"
    outer_path = seed_dir / "outer_fold_registry.csv"
    inner_path = seed_dir / "inner_fold_registry.csv"
    for path in (task_path, outer_path, inner_path):
        if not path.is_file():
            raise SelectionError(f"required input missing: {path}")
    task_df = pd.read_csv(task_path)
    outer_df = pd.read_csv(outer_path)
    inner_df = pd.read_csv(inner_path)
    for frame in (task_df, outer_df, inner_df):
        dmp._assert_years_allowed(frame)
        if frame.duplicated(dmp.KEY_COLS).any() and frame is not inner_df:
            raise SelectionError("duplicate task/outer keys")
    if set(outer_df["outer_fold"].unique()) != {0, 1, 2, 3, 4}:
        raise SelectionError(f"outer folds must be exactly 0..4 for {task}/seed{seed}")
    return task_df, outer_df, inner_df


def evaluate_base_candidate(
    task_df: pd.DataFrame,
    outer_df: pd.DataFrame,
    inner_df: pd.DataFrame,
    label_col: str,
    candidate: dict,
    *,
    task: str,
    seed: int,
) -> list[dict]:
    """Evaluate identity and Platt together while reusing outer raw fits."""
    merged = task_df.merge(
        outer_df[dmp.KEY_COLS + ["outer_fold"]], on=dmp.KEY_COLS, how="inner", validate="one_to_one"
    )
    if len(merged) != len(task_df):
        raise SelectionError("task frame and outer registry do not align 1:1")

    labels: list[np.ndarray] = []
    raw_parts: list[np.ndarray] = []
    platt_parts: list[np.ndarray] = []
    for fold in range(5):
        train = merged.loc[merged["outer_fold"] != fold].reset_index(drop=True)
        holdout = merged.loc[merged["outer_fold"] == fold].reset_index(drop=True)
        train_keys = set(train[dmp.KEY_COLS].itertuples(index=False, name=None))
        holdout_keys = set(holdout[dmp.KEY_COLS].itertuples(index=False, name=None))
        if train_keys & holdout_keys:
            raise SelectionError("outer train/holdout leakage")
        inner = inner_df.loc[inner_df["outer_fold"] == fold, dmp.KEY_COLS + ["inner_fold"]]
        if set(inner[dmp.KEY_COLS].itertuples(index=False, name=None)) & holdout_keys:
            raise SelectionError("outer holdout leaked into inner registry")
        inner_train = train.merge(inner, on=dmp.KEY_COLS, how="inner", validate="one_to_one")
        if len(inner_train) != len(train):
            raise SelectionError("inner registry does not cover outer training rows")

        context = f"task={task}|seed={seed}|candidate={dmp.candidate_name(candidate)}|fold={fold}"
        y_train = train[label_col].to_numpy()
        dmp._assert_both_classes_present(pd.Series(y_train), context)
        model = dmp.build_pipeline(candidate["family"], candidate["params"])
        model.fit(train[FEATURES], y_train)
        raw_holdout = model.predict_proba(holdout[FEATURES])[:, 1]
        dmp._assert_valid_probabilities(raw_holdout, context)

        raw_cv = dmp._leave_one_group_out_raw(
            inner_train, "inner_fold", FEATURES, label_col, candidate, context=f"{context}|platt_cv"
        )
        platt = dmp.PlattCalibrator().fit(raw_cv, inner_train[label_col].to_numpy())
        platt_holdout = platt.predict(raw_holdout)
        dmp._assert_valid_probabilities(platt_holdout, context)

        labels.append(holdout[label_col].to_numpy())
        raw_parts.append(raw_holdout)
        platt_parts.append(platt_holdout)

    y = np.concatenate(labels)
    raw = np.concatenate(raw_parts)
    platt = np.concatenate(platt_parts)
    if len(y) != len(task_df):
        raise SelectionError("fixed-candidate OOF coverage mismatch")

    rows = []
    for calibrator, calibrated in (("identity", raw), ("platt", platt)):
        intercept, slope = dmp.calibration_intercept_slope(y, calibrated)
        rows.append(
            {
                "task": task,
                "seed": seed,
                "candidate": dmp.candidate_name(candidate),
                "family": candidate["family"],
                "calibrator": calibrator,
                "n": int(len(y)),
                "n_events": int(np.sum(y)),
                "brier_calibrated": dmp.brier_score(y, calibrated),
                "log_loss_calibrated": dmp.log_loss_safe(y, calibrated),
                "calibration_intercept_calibrated": intercept,
                "calibration_slope_calibrated": slope,
                "pr_auc": dmp.pr_auc(y, calibrated),
                "roc_auc": dmp.roc_auc(y, calibrated),
            }
        )
    return rows


def aggregate_and_select(metrics: pd.DataFrame, equivalence: float = 0.0005, stability_max: float = 0.001) -> tuple[pd.DataFrame, dict]:
    required_seeds = set(SEEDS)
    rows: list[dict] = []
    selected: dict = {}
    for task in TASKS:
        task_df = metrics.loc[metrics["task"] == task]
        for (candidate, family, calibrator), group in task_df.groupby(["candidate", "family", "calibrator"], sort=False):
            if set(group["seed"]) != required_seeds or len(group) != 3:
                raise SelectionError(f"incomplete seed coverage for {task}/{candidate}/{calibrator}")
            rows.append(
                {
                    "task": task,
                    "candidate": candidate,
                    "family": family,
                    "calibrator": calibrator,
                    "mean_brier_calibrated": group["brier_calibrated"].mean(),
                    "brier_seed_range": group["brier_calibrated"].max() - group["brier_calibrated"].min(),
                    "mean_log_loss_calibrated": group["log_loss_calibrated"].mean(),
                    "mean_slope_distance": (group["calibration_slope_calibrated"] - 1.0).abs().mean(),
                    "mean_intercept_distance": group["calibration_intercept_calibrated"].abs().mean(),
                    "mean_pr_auc": group["pr_auc"].mean(),
                    "mean_roc_auc": group["roc_auc"].mean(),
                    "simplicity_rank": dmp.SIMPLICITY_RANK[family],
                }
            )
    ranking = pd.DataFrame(rows)
    if len(ranking) != EXPECTED_RANKING_ROWS:
        raise SelectionError(f"expected {EXPECTED_RANKING_ROWS} ranking rows, found {len(ranking)}")
    ranking["stability_pass"] = ranking["brier_seed_range"] <= stability_max
    ranking["equivalent_to_best"] = False
    ranking["selected"] = False
    ranking["rank"] = 0

    ordered_parts = []
    for task in TASKS:
        part = ranking.loc[ranking["task"] == task].copy()
        stable = part.loc[part["stability_pass"]]
        if stable.empty:
            raise SelectionError(f"no stable candidate for {task}")
        best = stable["mean_brier_calibrated"].min()
        part["equivalent_to_best"] = part["stability_pass"] & (part["mean_brier_calibrated"] <= best + equivalence)
        part = part.sort_values(
            ["stability_pass", "equivalent_to_best", "mean_log_loss_calibrated", "mean_slope_distance", "mean_intercept_distance", "mean_pr_auc", "mean_roc_auc", "simplicity_rank", "candidate", "calibrator"],
            ascending=[False, False, True, True, True, False, False, True, True, True],
            kind="mergesort",
        ).reset_index(drop=True)
        part["rank"] = np.arange(1, len(part) + 1)
        part.loc[0, "selected"] = True
        winner = part.iloc[0]
        selected[task] = {
            "candidate": winner["candidate"],
            "family": winner["family"],
            "calibrator": winner["calibrator"],
            "mean_brier_calibrated": float(winner["mean_brier_calibrated"]),
            "brier_seed_range": float(winner["brier_seed_range"]),
            "selection_status": "D0_ONLY_MVP_BASELINE_SELECTED_PRODUCTION_REFIT_REQUIRED",
        }
        ordered_parts.append(part)
    return pd.concat(ordered_parts, ignore_index=True), selected


def validate_no_pii_text(root: Path) -> None:
    import re

    pattern = re.compile(r"\b[A-Z][0-9]{9}\b")
    for path in root.rglob("*"):
        if path.is_file() and path.suffix.lower() in {".json", ".csv", ".md", ".txt"}:
            if pattern.search(path.read_text(encoding="utf-8")):
                raise SelectionError(f"participant-ID-like pattern found in output: {path}")


def load_checkpoint(path: Path, fingerprint: str, candidate: dict, task: str,
                    seed: int, index: int, expected_n: int) -> list[dict]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        name = dmp.candidate_name(candidate)
        if (payload["fingerprint"] != fingerprint or payload["candidate"] != name
                or type(payload["candidate_index"]) is not int or payload["candidate_index"] != index):
            raise SelectionError("checkpoint identity mismatch")
        rows = payload["metrics"]
        if not isinstance(rows, list) or len(rows) != 2:
            raise SelectionError("checkpoint must contain two calibrators")
        for row, calibrator in zip(rows, ("identity", "platt"), strict=True):
            if any(row[k] != v for k, v in {"task": task, "seed": seed, "candidate": name,
                    "family": candidate["family"], "calibrator": calibrator, "n": expected_n}.items()):
                raise SelectionError("checkpoint metric identity mismatch")
            if type(row["seed"]) is not int or type(row["n"]) is not int:
                raise SelectionError("checkpoint integer metadata invalid")
            if type(row["n_events"]) is not int or not 0 < row["n_events"] < expected_n:
                raise SelectionError("checkpoint class counts invalid")
            for key in ("brier_calibrated", "log_loss_calibrated", "calibration_intercept_calibrated",
                        "calibration_slope_calibrated", "pr_auc", "roc_auc"):
                value = row[key]
                if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
                    raise SelectionError("checkpoint metric is not finite numeric")
                if key in ("brier_calibrated", "pr_auc", "roc_auc") and not 0 <= value <= 1:
                    raise SelectionError("checkpoint metric outside range")
                if key == "log_loss_calibrated" and value < 0:
                    raise SelectionError("negative checkpoint log loss")
        return rows
    except (ValueError, KeyError, TypeError, UnicodeError) as exc:
        raise SelectionError("invalid checkpoint structure") from exc


def run(args: argparse.Namespace) -> None:
    repo_root = Path(__file__).resolve().parents[2]
    contract_path = args.contract or repo_root / CONTRACT_REL
    contract = load_contract(contract_path)
    candidates = dmp.candidate_grid()
    validate_candidate_grid(candidates)
    if args.output_root.exists():
        raise SelectionError(f"output_root must not exist: {args.output_root}")
    args.work_root.mkdir(parents=True, exist_ok=True)

    input_hashes = {"contract": sha256_file(contract_path), "code": sha256_file(Path(__file__)),
                    "disease_model_pipeline": sha256_file(Path(dmp.__file__))}
    for task in TASKS:
        input_hashes[f"task_frame/{task}"] = sha256_file(args.task_frame_root / task / "development_2019_2021.csv")
        for seed in SEEDS:
            base = args.fold_root / task / f"seed_{seed}"
            input_hashes[f"fold/{task}/{seed}/outer"] = sha256_file(base / "outer_fold_registry.csv")
            input_hashes[f"fold/{task}/{seed}/inner"] = sha256_file(base / "inner_fold_registry.csv")
    fingerprint = canonical_json_hash({"inputs": input_hashes, "grid": candidates})

    metric_rows = []
    expected_checkpoints = set()
    for task, label_col in TASKS.items():
        for seed in SEEDS:
            task_df, outer_df, inner_df = load_inputs(args.task_frame_root, args.fold_root, task, seed)
            for index, candidate in enumerate(candidates):
                checkpoint = args.work_root / task / f"seed_{seed}" / f"candidate_{index:02d}.json"
                expected_checkpoints.add(checkpoint.resolve())
                if checkpoint.exists():
                    metric_rows.extend(load_checkpoint(checkpoint, fingerprint, candidate, task, seed, index, len(task_df)))
                    continue
                started = time.time()
                rows = evaluate_base_candidate(task_df, outer_df, inner_df, label_col, candidate, task=task, seed=seed)
                atomic_json(
                    checkpoint,
                    {
                        "fingerprint": fingerprint,
                        "candidate_index": index,
                        "candidate": dmp.candidate_name(candidate),
                        "elapsed_sec": time.time() - started,
                        "metrics": rows,
                    },
                )
                metric_rows.extend(load_checkpoint(checkpoint, fingerprint, candidate, task, seed, index, len(task_df)))
                print(f"[checkpoint] task={task} seed={seed} candidate={index + 1}/46", flush=True)

    if {p.resolve() for p in args.work_root.glob("*/seed_*/candidate_*.json")} != expected_checkpoints:
        raise SelectionError("unexpected checkpoint paths")
    metrics = pd.DataFrame(metric_rows)
    if len(metrics) != EXPECTED_METRIC_ROWS:
        raise SelectionError(f"expected {EXPECTED_METRIC_ROWS} metric rows, found {len(metrics)}")
    ranking, selected = aggregate_and_select(metrics)

    args.output_root.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".d0_single_selection_", dir=args.output_root.parent) as tmp:
        stage = Path(tmp) / "payload"
        stage.mkdir()
        metrics.sort_values(["task", "candidate", "calibrator", "seed"]).to_csv(stage / "fixed_combination_metrics.csv", index=False)
        ranking.to_csv(stage / "combination_ranking.csv", index=False)
        atomic_json(stage / "selected_combinations.json", selected)
        atomic_json(
            stage / "validation_summary.json",
            {
                "status": "PASS_D0_ONLY_SINGLE_COMBINATION_SELECTION",
                "production_release_gate": "BLOCKED_REFIT_PACKAGING_AND_QA_REQUIRED",
                "metric_rows": len(metrics),
                "ranking_rows": len(ranking),
                "selected_task_count": len(selected),
                "fingerprint": fingerprint,
                "inputs": input_hashes,
                "prohibited_years_used": False,
                "row_level_outputs_written": False,
            },
        )
        output_hashes = {
            p.name: sha256_file(p)
            for p in sorted(stage.iterdir())
            if p.is_file()
        }
        atomic_json(stage / "manifest_sha256.json", output_hashes)
        for name, expected in output_hashes.items():
            if sha256_file(stage / name) != expected:
                raise SelectionError(f"staged output hash mismatch: {name}")
        validate_no_pii_text(stage)
        stage.rename(args.output_root)
    print("ALL_DONE", flush=True)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--task-frame-root", type=Path, required=True)
    parser.add_argument("--fold-root", type=Path, required=True)
    parser.add_argument("--work-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--contract", type=Path)
    return parser


def main() -> None:
    try:
        run(build_parser().parse_args())
    except (SelectionError, ValueError, OSError, KeyError, TypeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc


if __name__ == "__main__":
    main()
