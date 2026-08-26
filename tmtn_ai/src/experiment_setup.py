"""Load and validate the jointly approved TMTN development configuration.

This module validates authorization and file snapshots.  It never reads health data,
fits preprocessing, trains a model, or evaluates performance.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping

import yaml

from .file_integrity import sha256_file


PROJECT_ROOT = Path(__file__).resolve().parents[1]
EXPECTED_FEATURES = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]
EXPECTED_TASKS = {
    "waist": ("waist_target_eligible", "waist_cm", "regression"),
    "diabetes": (
        "diabetes_target_eligible",
        "diabetes_measurement_label_raw",
        "classification",
    ),
    "hypertension": (
        "hypertension_target_eligible",
        "hypertension_measurement_label_raw",
        "classification",
    ),
}


class ExperimentConfigError(ValueError):
    """Raised when an experiment config or its frozen snapshots drift."""


def _mapping(value: Any, name: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ExperimentConfigError(f"{name} must be a YAML mapping.")
    return value


def _project_file(relative: str) -> Path:
    path = (PROJECT_ROOT / relative).resolve()
    try:
        path.relative_to(PROJECT_ROOT.resolve())
    except ValueError as exc:
        raise ExperimentConfigError(f"Snapshot path escapes the project root: {relative}") from exc
    return path


def load_authorized_config(path: Path) -> dict[str, Any]:
    """Load the config and fail closed on approval, year, or snapshot drift."""

    raw = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
    config = dict(_mapping(raw, "Experiment config"))
    if config.get("status") != "jointly_authorized":
        raise ExperimentConfigError("Experiment config is not jointly_authorized.")
    if config.get("do_not_train") is not False:
        raise ExperimentConfigError("Authorized config must explicitly set do_not_train: false.")

    authorization = _mapping(config.get("authorization"), "authorization")
    approvers = _mapping(authorization.get("approvers"), "authorization.approvers")
    for person in ("kangho", "byeonghak"):
        approval = _mapping(approvers.get(person), f"approver {person}")
        if approval.get("status") != "approved" or not approval.get("approved_at"):
            raise ExperimentConfigError(f"Missing approval or timestamp for {person}.")

    years = _mapping(config.get("year_access"), "year_access")
    if list(years.get("development_allowed", [])) != [2019, 2020, 2021]:
        raise ExperimentConfigError("Development years must be exactly 2019-2021.")
    if years.get("evaluation_2022") != "blocked_until_full_freeze":
        raise ExperimentConfigError("2022 must remain blocked until full freeze.")
    if years.get("opened_2023") != "custodian_only":
        raise ExperimentConfigError("2023 must remain custodian-only.")
    if years.get("forbidden_2024") != "fail_closed":
        raise ExperimentConfigError("2024 must remain fail-closed.")

    if list(config.get("features", [])) != EXPECTED_FEATURES:
        raise ExperimentConfigError("The frozen six-feature schema has drifted.")
    tasks = _mapping(config.get("tasks"), "tasks")
    if set(tasks) != set(EXPECTED_TASKS):
        raise ExperimentConfigError("Tasks must be exactly waist, diabetes, and hypertension.")
    for task, (eligibility, label, task_type) in EXPECTED_TASKS.items():
        item = _mapping(tasks[task], f"task {task}")
        if (item.get("eligibility"), item.get("label"), item.get("type")) != (
            eligibility,
            label,
            task_type,
        ):
            raise ExperimentConfigError(f"Task contract drift for {task}.")

    snapshots = _mapping(authorization.get("snapshots"), "authorization.snapshots")
    for name in ("protocol", "canonical", "canonical_audit", "environment_lock"):
        snapshot = _mapping(snapshots.get(name), f"snapshot {name}")
        snapshot_path = _project_file(str(snapshot.get("path", "")))
        if sha256_file(snapshot_path) != snapshot.get("sha256"):
            raise ExperimentConfigError(f"Frozen {name} snapshot hash mismatch.")
    code = _mapping(snapshots.get("implementation"), "snapshot implementation")
    files = _mapping(code.get("files"), "implementation files")
    if not files:
        raise ExperimentConfigError("No implementation file snapshots were declared.")
    for relative, expected_hash in files.items():
        if sha256_file(_project_file(str(relative))) != expected_hash:
            raise ExperimentConfigError(f"Implementation snapshot hash mismatch: {relative}")
    return config


def assert_development_years(frame_years: set[int], config: Mapping[str, Any]) -> None:
    """Reject a data frame containing anything outside the development window."""

    allowed = set(config["year_access"]["development_allowed"])
    forbidden = frame_years - allowed
    if forbidden:
        raise ExperimentConfigError(
            f"Input contains non-development years {sorted(forbidden)}; no rows were prepared."
        )
    missing = allowed - frame_years
    if missing:
        raise ExperimentConfigError(
            f"Input is missing required development years {sorted(missing)}."
        )
