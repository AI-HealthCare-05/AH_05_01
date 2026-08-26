"""Configuration, source-data, and split-leakage validation."""

from __future__ import annotations

import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

import pandas as pd
import yaml


REQUIRED_CONFIG_KEYS = {
    "task",
    "task_type",
    "dataset_version",
    "id_column",
    "year_column",
    "label_column",
    "feature_columns",
    "development_years",
    "temporal_validation_years",
    "locked_test_years",
    "forbidden_years",
    "random_validation_size",
    "random_seed",
    "classification_stratify",
    "regression_stratify",
    "regression_stratify_bins",
    "csv_encoding",
    "output_format",
    "do_not_split",
}

DIRECT_IDENTIFIER_PATTERN = re.compile(
    r"(^|_)(email|e_mail|phone|mobile|name|full_name|address|resident_number|ssn)(_|$)",
    flags=re.IGNORECASE,
)

FROZEN_YEAR_ROLES = {
    "development_years": {2019, 2020, 2021},
    "temporal_validation_years": {2022},
    "locked_test_years": {2023},
    "forbidden_years": {2024},
}


@dataclass(frozen=True)
class SplitConfig:
    """Validated split settings loaded from YAML."""

    task: str
    task_type: str
    dataset_version: str
    id_column: str
    year_column: str
    label_column: str
    feature_columns: tuple[str, ...]
    development_years: tuple[int, ...]
    temporal_validation_years: tuple[int, ...]
    locked_test_years: tuple[int, ...]
    forbidden_years: tuple[int, ...]
    random_validation_size: float
    random_seed: int
    classification_stratify: bool
    regression_stratify: str
    regression_stratify_bins: int
    csv_encoding: str
    output_format: str
    do_not_split: bool

    @property
    def selected_columns(self) -> list[str]:
        """Return the minimal ordered set of columns required for splitting."""
        return list(
            dict.fromkeys(
                [self.id_column, self.year_column, self.label_column, *self.feature_columns]
            )
        )

    @property
    def development_columns(self) -> list[str]:
        """Return development columns, including the target used for model fitting."""
        return self.selected_columns

    @property
    def locked_test_feature_columns(self) -> list[str]:
        """Return the blinded locked-test columns visible to model developers."""
        return list(
            dict.fromkeys([self.id_column, self.year_column, *self.feature_columns])
        )

    @property
    def locked_test_label_columns(self) -> list[str]:
        """Return the minimal columns stored in the separately controlled answer key."""
        return [self.id_column, self.year_column, self.label_column]


def _integer_years(value: Any, key: str) -> tuple[int, ...]:
    """Validate and normalize a non-empty YAML year sequence."""
    if not isinstance(value, list) or not value:
        raise ValueError(f"'{key}' must be a non-empty YAML list of integer years.")
    if any(isinstance(item, bool) or not isinstance(item, int) for item in value):
        raise ValueError(f"'{key}' must contain only integer years, for example [2023, 2024].")
    if len(value) != len(set(value)):
        raise ValueError(f"'{key}' contains duplicate years. Remove duplicates and rerun.")
    return tuple(value)


def load_config(path: Path) -> SplitConfig:
    """Load a YAML file and return a strictly validated split configuration."""
    if not path.is_file():
        raise FileNotFoundError(
            f"Configuration file does not exist: {path}. Supply an existing --config path."
        )
    try:
        raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (UnicodeError, yaml.YAMLError) as exc:
        raise ValueError(f"Could not read YAML configuration {path}: {exc}") from exc
    if not isinstance(raw, Mapping):
        raise ValueError("The YAML root must be a mapping of configuration keys to values.")
    missing = sorted(REQUIRED_CONFIG_KEYS - set(raw))
    if missing:
        raise ValueError(f"Configuration is missing required keys: {', '.join(missing)}.")

    task_type = str(raw["task_type"]).lower()
    if task_type not in {"classification", "regression"}:
        raise ValueError("'task_type' must be either 'classification' or 'regression'.")
    regression_stratify = str(raw["regression_stratify"]).lower()
    if regression_stratify not in {"none", "quantile"}:
        raise ValueError("'regression_stratify' must be 'none' or 'quantile'.")

    features = raw["feature_columns"]
    if not isinstance(features, list) or not features or not all(
        isinstance(item, str) and item.strip() for item in features
    ):
        raise ValueError("'feature_columns' must be a non-empty YAML list of column names.")
    if len(features) != len(set(features)):
        raise ValueError("'feature_columns' contains duplicates. Remove them and rerun.")
    sensitive = [column for column in features if DIRECT_IDENTIFIER_PATTERN.search(column)]
    if sensitive:
        raise ValueError(
            "Direct-identifier-like feature columns are blocked: "
            f"{', '.join(sensitive)}. Remove them from feature_columns."
        )

    development = _integer_years(raw["development_years"], "development_years")
    temporal_validation = _integer_years(
        raw["temporal_validation_years"], "temporal_validation_years"
    )
    locked_test = _integer_years(raw["locked_test_years"], "locked_test_years")
    forbidden = _integer_years(raw["forbidden_years"], "forbidden_years")
    role_sets = {
        "development_years": set(development),
        "temporal_validation_years": set(temporal_validation),
        "locked_test_years": set(locked_test),
        "forbidden_years": set(forbidden),
    }
    role_names = list(role_sets)
    for index, left_name in enumerate(role_names):
        for right_name in role_names[index + 1 :]:
            overlap = role_sets[left_name] & role_sets[right_name]
            if overlap:
                raise ValueError(
                    f"Year roles '{left_name}' and '{right_name}' overlap: {sorted(overlap)}. "
                    "Assign every year to exactly one role."
                )
    for role_name, expected in FROZEN_YEAR_ROLES.items():
        if role_sets[role_name] != expected:
            raise ValueError(
                f"'{role_name}' must equal {sorted(expected)} under TMTN A5 v0.1; "
                f"received {sorted(role_sets[role_name])}. Create a new versioned split "
                "contract before changing year roles."
            )

    validation_size = raw["random_validation_size"]
    if isinstance(validation_size, bool) or not isinstance(validation_size, (int, float)):
        raise ValueError("'random_validation_size' must be a number strictly between 0 and 1.")
    if not 0 < float(validation_size) < 1:
        raise ValueError("'random_validation_size' must be strictly between 0 and 1.")
    bins = raw["regression_stratify_bins"]
    if isinstance(bins, bool) or not isinstance(bins, int) or bins < 2:
        raise ValueError("'regression_stratify_bins' must be an integer of at least 2.")
    seed = raw["random_seed"]
    if isinstance(seed, bool) or not isinstance(seed, int) or seed < 0:
        raise ValueError("'random_seed' must be a non-negative integer.")
    if not isinstance(raw["classification_stratify"], bool):
        raise ValueError("'classification_stratify' must be true or false.")
    if not isinstance(raw["do_not_split"], bool):
        raise ValueError("'do_not_split' must be true or false.")
    output_format = str(raw["output_format"]).lower()
    if output_format != "csv":
        raise ValueError("Only output_format: csv is supported. Change the YAML value to 'csv'.")

    string_keys = ["task", "dataset_version", "id_column", "year_column", "label_column"]
    for key in string_keys:
        if not isinstance(raw[key], str) or not raw[key].strip():
            raise ValueError(f"'{key}' must be a non-empty string.")
    for key in ["task", "dataset_version"]:
        value = raw[key]
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", value):
            raise ValueError(
                f"'{key}' must use only ASCII letters, digits, dot, underscore, or hyphen, "
                "must start with a letter/digit, and must not contain path separators."
            )
    encoding = raw["csv_encoding"]
    if not isinstance(encoding, str) or encoding.lower() not in {"utf-8", "utf-8-sig"}:
        raise ValueError("'csv_encoding' must be 'utf-8' or 'utf-8-sig'.")

    return SplitConfig(
        task=raw["task"],
        task_type=task_type,
        dataset_version=raw["dataset_version"],
        id_column=raw["id_column"],
        year_column=raw["year_column"],
        label_column=raw["label_column"],
        feature_columns=tuple(features),
        development_years=development,
        temporal_validation_years=temporal_validation,
        locked_test_years=locked_test,
        forbidden_years=forbidden,
        random_validation_size=float(validation_size),
        random_seed=seed,
        classification_stratify=raw["classification_stratify"],
        regression_stratify=regression_stratify,
        regression_stratify_bins=bins,
        csv_encoding=encoding.lower(),
        output_format=output_format,
        do_not_split=raw["do_not_split"],
    )


def validate_source_data(frame: pd.DataFrame, config: SplitConfig) -> pd.DataFrame:
    """Validate source columns and values, then return a minimal defensive copy."""
    missing_columns = sorted(set(config.selected_columns) - set(frame.columns))
    if missing_columns:
        raise ValueError(
            f"Input is missing configured columns: {', '.join(missing_columns)}. "
            "Correct the YAML column names or provide the intended modeling table."
        )
    if frame[config.id_column].isna().any():
        raise ValueError(
            f"ID column '{config.id_column}' contains missing values. "
            "Create complete stable IDs before splitting."
        )
    numeric_year = pd.to_numeric(frame[config.year_column], errors="coerce")
    if numeric_year.isna().any() or not numeric_year.map(
        lambda value: math.isfinite(value) and float(value).is_integer()
    ).all():
        raise ValueError(
            f"Year column '{config.year_column}' must contain integer-like years only. "
            "Clean or map invalid year values before splitting."
        )
    year_values = numeric_year.astype("int64")
    record_keys = pd.DataFrame(
        {config.year_column: year_values, config.id_column: frame[config.id_column]}
    )
    if record_keys.duplicated().any():
        raise ValueError(
            f"Composite record key ('{config.year_column}', '{config.id_column}') contains "
            "duplicates. Resolve duplicate records within each survey year before splitting."
        )
    present_years = set(year_values.unique())
    forbidden_present = sorted(present_years & set(config.forbidden_years))
    if forbidden_present:
        raise ValueError(
            f"Input contains forbidden years: {forbidden_present}. Do not read, split, copy, "
            "or silently filter these rows in this pipeline."
        )
    allowed_years = (
        set(config.development_years)
        | set(config.temporal_validation_years)
        | set(config.locked_test_years)
    )
    unexpected = sorted(set(year_values.unique()) - allowed_years)
    if unexpected:
        raise ValueError(
            f"Year column contains years not allowed by this config: {unexpected}. "
            "Filter the task cohort or explicitly update the YAML year ranges."
        )
    missing_years = sorted(allowed_years - set(year_values.unique()))
    if missing_years:
        raise ValueError(
            f"Configured years have no rows in the input: {missing_years}. "
            "Check the input cohort or correct the YAML year lists."
        )
    if frame[config.label_column].isna().any():
        raise ValueError(
            f"Label column '{config.label_column}' contains missing values. "
            "Define a task-specific labeled cohort before splitting."
        )

    if config.task_type == "classification":
        labels = set(frame[config.label_column].dropna().unique().tolist())
        if not labels or not labels.issubset({0, 1, 0.0, 1.0, False, True}):
            raise ValueError(
                f"Classification label '{config.label_column}' must contain only 0 and 1. "
                "Map labels explicitly before running the split."
            )
        if labels != {0, 1}:
            raise ValueError(
                f"Classification label '{config.label_column}' must contain both classes 0 and 1. "
                "Check the labeled cohort before splitting."
            )
    else:
        numeric_label = pd.to_numeric(frame[config.label_column], errors="coerce")
        if numeric_label.isna().any() or not numeric_label.map(math.isfinite).all():
            raise ValueError(
                f"Regression label '{config.label_column}' must be finite numeric data. "
                "Clean invalid target values before splitting."
            )

    selected = frame.loc[:, config.selected_columns].copy()
    selected[config.year_column] = year_values
    return selected


def validate_split_integrity(
    random_train: pd.DataFrame,
    random_validation: pd.DataFrame,
    temporal_train: pd.DataFrame,
    temporal_validation: pd.DataFrame,
    locked_test: pd.DataFrame,
    config: SplitConfig,
) -> None:
    """Fail on ID leakage or any year assignment that violates the split contract."""
    id_column = config.id_column
    year_column = config.year_column

    def keys(frame: pd.DataFrame) -> set[tuple[object, object]]:
        return set(zip(frame[year_column], frame[id_column], strict=True))

    locked_test_ids = keys(locked_test)
    development_frames = [random_train, random_validation, temporal_train, temporal_validation]
    for frame in development_frames:
        overlap = keys(frame) & locked_test_ids
        if overlap:
            raise RuntimeError(
                "An ID appears in both development/temporal validation and locked test. "
                "Review source IDs and year assignments; outputs were not committed."
            )
        if frame[config.year_column].isin(config.locked_test_years).any():
            raise RuntimeError(
                "A locked-test-year row entered development. Review split logic and YAML years; "
                "outputs were not committed."
            )

    for left, right, label in [
        (random_train, random_validation, "random"),
        (temporal_train, temporal_validation, "temporal"),
    ]:
        if keys(left) & keys(right):
            raise RuntimeError(
                f"An ID overlaps {label} train and validation. Outputs were not committed."
            )

    random_years = set(pd.concat([random_train, random_validation])[config.year_column].unique())
    if not random_years.issubset(set(config.development_years)):
        raise RuntimeError("Random split contains non-development years. Outputs were not committed.")
    if set(temporal_train[config.year_column].unique()) != set(config.development_years):
        raise RuntimeError(
            "Temporal train years do not exactly match development_years. "
            "Check the cohort and configuration."
        )
    if set(temporal_validation[config.year_column].unique()) != set(
        config.temporal_validation_years
    ):
        raise RuntimeError(
            "Temporal validation years do not exactly match temporal_validation_years. "
            "Check the cohort and configuration."
        )
    if set(locked_test[config.year_column].unique()) != set(config.locked_test_years):
        raise RuntimeError(
            "Locked test years do not exactly match locked_test_years. Check the input cohort."
        )
