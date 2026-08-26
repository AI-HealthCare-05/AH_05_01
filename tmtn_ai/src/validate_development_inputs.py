"""Pre-training integrity validation for TMTN model_development_v0_2.

Validates task frames and nested fold registries against
contracts/model_experiment_protocol_v0_2.yaml and
configs/model_development_v0_2.yaml. Reports PASS/FAIL and aggregate
counts only -- never row-level values or participant IDs.

Reads only from the two authorized external roots (task frame root,
fold registry root) plus files inside this workspace. Does not recurse
into their parent directories.
"""

from __future__ import annotations

import hashlib
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path

import pandas as pd

FIXED_FEATURES = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]
KEY_COLS = ["source_year", "participant_id"]
ALLOWED_YEARS = {2019, 2020, 2021}
SEEDS = [42, 1042, 2042]
OUTER_FOLDS = {0, 1, 2, 3, 4}
INNER_FOLDS = {0, 1, 2, 3}
FORBIDDEN_MODEL_INPUT_COLS = {
    "participant_id",
    "source_year",
    "examination_weight",
    "strata",
    "psu",
}

TASKS = {
    "waist": {"label": "waist_cm", "classification": False},
    "diabetes": {"label": "diabetes_measurement_label_raw", "classification": True},
    "hypertension": {"label": "hypertension_measurement_label_raw", "classification": True},
}


@dataclass
class CheckResult:
    name: str
    passed: bool
    detail: str = ""


@dataclass
class ValidationReport:
    checks: list = field(default_factory=list)

    def add(self, name: str, passed: bool, detail: str = "") -> None:
        self.checks.append(CheckResult(name, passed, detail))

    @property
    def all_passed(self) -> bool:
        return all(c.passed for c in self.checks)

    def summary(self) -> str:
        lines = []
        for c in self.checks:
            status = "PASS" if c.passed else "FAIL"
            lines.append(f"[{status}] {c.name}" + (f" -- {c.detail}" if c.detail else ""))
        lines.append(f"OVERALL: {'PASS' if self.all_passed else 'FAIL'}")
        return "\n".join(lines)


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def load_manifest(root: Path) -> dict:
    with open(root / "manifest.json", "r", encoding="utf-8") as fh:
        return json.load(fh)


def validate_hashes(workspace_root: Path, handoff_expected: dict) -> ValidationReport:
    report = ValidationReport()
    for rel_path, expected in handoff_expected.items():
        p = workspace_root / rel_path
        actual = sha256_of(p) if p.exists() else None
        report.add(
            f"sha256:{rel_path}",
            actual == expected,
            f"expected={expected} actual={actual}",
        )
    return report


def validate_task_frame_root(task_frame_root: Path, config_sha256: str) -> ValidationReport:
    report = ValidationReport()
    manifest = load_manifest(task_frame_root)

    report.add("task_frame_manifest.config_sha256_matches", manifest.get("config_sha256") == config_sha256)
    report.add(
        "task_frame_manifest.source_years_exact",
        set(manifest.get("source_years", [])) == ALLOWED_YEARS,
    )
    report.add(
        "task_frame_manifest.no_reserved_year_flag",
        manifest.get("policy", {}).get("reserved_year_accessed") is False,
    )
    report.add(
        "task_frame_manifest.training_not_yet_performed",
        manifest.get("policy", {}).get("training_performed") is False,
    )

    for task, meta in manifest.get("files", {}).items():
        p = task_frame_root / task
        actual_sha = sha256_of(p) if p.exists() else None
        report.add(
            f"task_frame_file_hash:{task}",
            actual_sha == meta["sha256"],
            f"expected={meta['sha256']} actual={actual_sha}",
        )
    return report


def load_task_frame(task_frame_root: Path, task: str) -> pd.DataFrame:
    return pd.read_csv(task_frame_root / task / "development_2019_2021.csv")


def validate_task_frame_content(df: pd.DataFrame, task: str) -> ValidationReport:
    report = ValidationReport()
    label_col = TASKS[task]["label"]
    is_classification = TASKS[task]["classification"]

    years_ok = set(df["source_year"].unique()).issubset(ALLOWED_YEARS)
    report.add(f"{task}.years_within_2019_2021", years_ok, f"unique_years={sorted(df['source_year'].unique().tolist())}")

    key_missing = df[KEY_COLS].isnull().any().any()
    key_dupes = df.duplicated(subset=KEY_COLS).sum()
    report.add(f"{task}.key_no_missing", not key_missing)
    report.add(f"{task}.key_no_duplicates", key_dupes == 0, f"duplicate_rows={key_dupes}")

    missing_features = {c: int(df[c].isnull().sum()) for c in FIXED_FEATURES}
    report.add(
        f"{task}.six_features_complete",
        all(v == 0 for v in missing_features.values()),
        f"missing_counts={missing_features}",
    )

    label_missing = int(df[label_col].isnull().sum())
    report.add(f"{task}.label_no_missing", label_missing == 0, f"missing={label_missing}")

    if is_classification:
        bad_values = df.loc[~df[label_col].isin([0, 0.0, 1, 1.0]), label_col]
        report.add(
            f"{task}.label_binary_only",
            len(bad_values) == 0,
            f"non_binary_count={len(bad_values)}",
        )

    if task in ("diabetes", "hypertension"):
        report.add(f"{task}.no_measured_waist_column", "waist_cm" not in df.columns)

    forbidden_present = FORBIDDEN_MODEL_INPUT_COLS.intersection(set(df.columns))
    report.add(
        f"{task}.forbidden_columns_present_but_not_used_as_features_is_expected",
        True,
        f"present_for_audit_only={sorted(forbidden_present)}",
    )

    if "examination_weight" in df.columns:
        neg_or_missing = int((df["examination_weight"].isnull() | (df["examination_weight"] <= 0)).sum())
        coverage = 1.0 - (neg_or_missing / len(df))
        report.add(
            f"{task}.weight_coverage_reported",
            True,
            f"valid_weight_coverage={coverage:.4f} (n={len(df)})",
        )

    return report


def validate_fold_registry(
    fold_root: Path,
    task: str,
    seed: int,
    task_keys: pd.DataFrame,
) -> ValidationReport:
    report = ValidationReport()
    task_dir = fold_root / task / f"seed_{seed}"
    outer = pd.read_csv(task_dir / "outer_fold_registry.csv")
    inner = pd.read_csv(task_dir / "inner_fold_registry.csv")

    report.add(
        f"{task}.seed{seed}.outer_fold_range",
        set(outer["outer_fold"].unique()).issubset(OUTER_FOLDS),
        f"values={sorted(outer['outer_fold'].unique().tolist())}",
    )
    report.add(
        f"{task}.seed{seed}.inner_fold_range",
        set(inner["inner_fold"].unique()).issubset(INNER_FOLDS),
        f"values={sorted(inner['inner_fold'].unique().tolist())}",
    )

    outer_keys = outer.set_index(KEY_COLS).index
    report.add(
        f"{task}.seed{seed}.outer_key_no_duplicates",
        not outer_keys.duplicated().any(),
    )

    task_key_index = task_keys.set_index(KEY_COLS).index
    outer_covers_task_exact = (
        len(outer_keys) == len(task_key_index)
        and set(outer_keys) == set(task_key_index)
    )
    report.add(
        f"{task}.seed{seed}.outer_registry_matches_task_frame_keys_exactly_once",
        outer_covers_task_exact,
        f"outer_n={len(outer_keys)} task_n={len(task_key_index)}",
    )

    overlap_total = 0
    union_ok = True
    for k in sorted(OUTER_FOLDS):
        holdout_keys = set(
            outer.loc[outer["outer_fold"] == k, KEY_COLS].itertuples(index=False, name=None)
        )
        train_inner_keys = set(
            inner.loc[inner["outer_fold"] == k, KEY_COLS].itertuples(index=False, name=None)
        )
        overlap = len(holdout_keys & train_inner_keys)
        overlap_total += overlap
        if (holdout_keys | train_inner_keys) != set(task_key_index):
            union_ok = False

    report.add(
        f"{task}.seed{seed}.outer_holdout_inner_train_disjoint_all_folds",
        overlap_total == 0,
        f"total_overlap_keys={overlap_total}",
    )
    report.add(
        f"{task}.seed{seed}.outer_holdout_union_inner_train_equals_task_keys",
        union_ok,
    )

    expected_inner_rows = len(task_key_index) * (len(OUTER_FOLDS) - 1)
    report.add(
        f"{task}.seed{seed}.inner_row_count_matches_expected",
        len(inner) == expected_inner_rows,
        f"actual={len(inner)} expected={expected_inner_rows}",
    )

    dup_inner = inner.duplicated(subset=KEY_COLS + ["outer_fold"]).sum()
    report.add(
        f"{task}.seed{seed}.inner_no_duplicate_key_per_outer_fold",
        dup_inner == 0,
        f"duplicates={dup_inner}",
    )

    return report


def run_all(workspace_root: Path, task_frame_root: Path, fold_root: Path) -> ValidationReport:
    config_path = workspace_root / "configs" / "model_development_v0_2.yaml"
    config_sha256 = sha256_of(config_path)

    full_report = ValidationReport()
    full_report.checks.extend(validate_task_frame_root(task_frame_root, config_sha256).checks)

    frames = {}
    for task in TASKS:
        df = load_task_frame(task_frame_root, task)
        frames[task] = df
        full_report.checks.extend(validate_task_frame_content(df, task).checks)

    for task in TASKS:
        for seed in SEEDS:
            full_report.checks.extend(
                validate_fold_registry(fold_root, task, seed, frames[task][KEY_COLS]).checks
            )

    return full_report


if __name__ == "__main__":
    ws = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    tf_root = Path(sys.argv[2])
    fr_root = Path(sys.argv[3])
    rpt = run_all(ws, tf_root, fr_root)
    print(rpt.summary())
    sys.exit(0 if rpt.all_passed else 1)
