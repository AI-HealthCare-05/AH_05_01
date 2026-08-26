from __future__ import annotations

from pathlib import Path
from copy import deepcopy

import numpy as np
import pandas as pd
import pytest
import yaml

from src.build_nested_fold_registry import build_nested_registries
from src.experiment_setup import ExperimentConfigError, load_authorized_config
from src.file_integrity import sha256_file
from src.prepare_development_frames import (
    DESIGN_COLUMNS,
    KEY_COLUMNS,
    build_task_frame,
    materialize_development_frames,
)


PROJECT_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = PROJECT_ROOT / "configs" / "model_development_v0_2.yaml"
FEATURES = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]


def _canonical(rows_per_year: int = 120) -> pd.DataFrame:
    rows: list[dict[str, object]] = []
    for year in (2019, 2020, 2021):
        for index in range(rows_per_year):
            diabetes = index % 2
            hypertension = (index // 2) % 2
            rows.append(
                {
                    "source_year": year,
                    "participant_id": f"{year}-{index:04d}",
                    "age_years": 19 + index % 62,
                    "sex_code": 1 + index % 2,
                    "height_cm": 150.0 + index % 35,
                    "weight_kg": 48.0 + index % 60,
                    "leisure_aerobic_moderate_equivalent_min_week": float(index % 301),
                    "strength_days_week": index % 6,
                    "examination_weight": 1.0 + index / 100,
                    "strata": f"S{index % 6}",
                    "psu": f"P{index % 18}",
                    "waist_target_eligible": True,
                    "waist_cm": 65.0 + index % 40 + (index % 3) / 10,
                    "diabetes_target_eligible": True,
                    "diabetes_measurement_label_raw": diabetes,
                    "hypertension_target_eligible": True,
                    "hypertension_measurement_label_raw": hypertension,
                }
            )
    return pd.DataFrame(rows)


def _synthetic_config() -> dict:
    config = deepcopy(load_authorized_config(CONFIG_PATH))
    config["canonical_materialization"]["accepted_snapshot"][
        "eligibility_missing_expected"
    ] = {
        "waist_target_eligible": 0,
        "diabetes_target_eligible": 0,
        "hypertension_target_eligible": 0,
    }
    return config


def test_jointly_authorized_config_and_snapshots_are_valid() -> None:
    config = load_authorized_config(CONFIG_PATH)

    assert config["do_not_train"] is False
    assert config["authorization"]["approvers"]["byeonghak"]["status"] == "approved"
    assert config["year_access"]["evaluation_2022"] == "blocked_until_full_freeze"


@pytest.mark.parametrize("reserved_year", [2022, 2023, 2024])
def test_reserved_year_is_rejected_before_task_filtering(reserved_year: int) -> None:
    config = _synthetic_config()
    frame = _canonical()
    reserved = frame.iloc[[0]].copy()
    reserved["source_year"] = reserved_year
    reserved["participant_id"] = f"reserved-{reserved_year}"
    reserved["waist_target_eligible"] = False
    reserved["waist_cm"] = np.nan

    with pytest.raises(ExperimentConfigError, match="non-development years"):
        build_task_frame(pd.concat([frame, reserved], ignore_index=True), "waist", config)


def test_task_frame_is_complete_case_without_imputation_or_source_mutation() -> None:
    config = _synthetic_config()
    frame = _canonical()
    original = frame.copy(deep=True)
    missing_key = tuple(frame.loc[0, KEY_COLUMNS])
    frame.loc[0, "height_cm"] = np.nan
    original = frame.copy(deep=True)

    result = build_task_frame(frame, "diabetes", config)

    assert missing_key not in set(map(tuple, result[KEY_COLUMNS].to_numpy()))
    assert not result[FEATURES].isna().any().any()
    assert result.columns.tolist() == [
        *KEY_COLUMNS,
        *FEATURES,
        *DESIGN_COLUMNS,
        "diabetes_measurement_label_raw",
    ]
    pd.testing.assert_frame_equal(frame, original)


def test_classification_label_outside_binary_fails_closed() -> None:
    config = _synthetic_config()
    frame = _canonical()
    frame.loc[0, "diabetes_measurement_label_raw"] = 2

    with pytest.raises(ValueError, match="outside 0/1"):
        build_task_frame(frame, "diabetes", config)


def test_observed_waist_outside_eligibility_is_preserved_upstream_but_excluded() -> None:
    config = _synthetic_config()
    frame = _canonical()
    excluded_key = tuple(frame.loc[0, KEY_COLUMNS])
    frame.loc[0, "waist_target_eligible"] = False
    assert pd.notna(frame.loc[0, "waist_cm"])

    result = build_task_frame(frame, "waist", config)

    assert excluded_key not in set(map(tuple, result[KEY_COLUMNS].to_numpy()))


def test_disease_label_outside_eligibility_fails_closed() -> None:
    config = _synthetic_config()
    frame = _canonical()
    frame.loc[0, "diabetes_target_eligible"] = False

    with pytest.raises(ValueError, match="outside the eligibility"):
        build_task_frame(frame, "diabetes", config)


def test_only_predeclared_missing_eligibility_is_mapped_to_ineligible() -> None:
    config = load_authorized_config(CONFIG_PATH)
    frame = _canonical()
    excluded_key = tuple(frame.loc[0, KEY_COLUMNS])
    frame["diabetes_target_eligible"] = frame["diabetes_target_eligible"].astype("boolean")
    frame.loc[0, "diabetes_target_eligible"] = pd.NA
    frame.loc[0, "diabetes_measurement_label_raw"] = pd.NA

    result = build_task_frame(frame, "diabetes", config)

    assert excluded_key not in set(map(tuple, result[KEY_COLUMNS].to_numpy()))

    undeclared = deepcopy(config)
    undeclared["canonical_materialization"]["accepted_snapshot"][
        "eligibility_missing_expected"
    ]["diabetes_target_eligible"] = 0
    with pytest.raises(ValueError, match="expected exactly 0"):
        build_task_frame(frame, "diabetes", undeclared)


@pytest.mark.parametrize(
    ("task", "label", "task_type"),
    [
        ("waist", "waist_cm", "regression"),
        ("diabetes", "diabetes_measurement_label_raw", "classification"),
    ],
)
def test_nested_registries_are_deterministic_disjoint_and_exhaustive(
    task: str, label: str, task_type: str
) -> None:
    config = _synthetic_config()
    frame = build_task_frame(_canonical(), task, config)

    first_outer, first_inner = build_nested_registries(
        frame,
        label=label,
        task_type=task_type,
        outer_folds=5,
        inner_folds=4,
        seed=42,
    )
    second_outer, second_inner = build_nested_registries(
        frame,
        label=label,
        task_type=task_type,
        outer_folds=5,
        inner_folds=4,
        seed=42,
    )

    pd.testing.assert_frame_equal(first_outer, second_outer)
    pd.testing.assert_frame_equal(first_inner, second_inner)
    shuffled_outer, shuffled_inner = build_nested_registries(
        frame.sample(frac=1, random_state=999).reset_index(drop=True),
        label=label,
        task_type=task_type,
        outer_folds=5,
        inner_folds=4,
        seed=42,
    )
    pd.testing.assert_frame_equal(first_outer, shuffled_outer)
    pd.testing.assert_frame_equal(first_inner, shuffled_inner)
    assert len(first_outer) == len(frame)
    assert len(first_inner) == len(frame) * 4
    assert first_outer[KEY_COLUMNS].duplicated().sum() == 0
    for outer_fold in range(5):
        holdout = set(
            map(
                tuple,
                first_outer.loc[first_outer["outer_fold"].eq(outer_fold), KEY_COLUMNS].to_numpy(),
            )
        )
        inner = set(
            map(
                tuple,
                first_inner.loc[first_inner["outer_fold"].eq(outer_fold), KEY_COLUMNS].to_numpy(),
            )
        )
        assert holdout.isdisjoint(inner)
        assert holdout | inner == set(map(tuple, frame[KEY_COLUMNS].to_numpy()))


def test_materialization_is_atomic_and_refuses_overwrite(tmp_path: Path) -> None:
    canonical_path = tmp_path / "canonical.csv"
    output_root = tmp_path / "outputs"
    _canonical().to_csv(canonical_path, index=False)
    synthetic_config = yaml.safe_load(CONFIG_PATH.read_text(encoding="utf-8"))
    synthetic_config["canonical_materialization"]["accepted_snapshot"] = {
        "filename": canonical_path.name,
        "sha256": sha256_file(canonical_path),
        "rows": len(_canonical()),
        "materialization_config_sha256": "synthetic-test-only",
        "eligibility_missing_expected": {
            "waist_target_eligible": 0,
            "diabetes_target_eligible": 0,
            "hypertension_target_eligible": 0,
        },
    }
    synthetic_config_path = tmp_path / "synthetic_config.yaml"
    synthetic_config_path.write_text(
        yaml.safe_dump(synthetic_config, sort_keys=False), encoding="utf-8"
    )

    final = materialize_development_frames(synthetic_config_path, canonical_path, output_root)

    assert (final / "manifest.json").is_file()
    assert (final / "waist" / "development_2019_2021.csv").is_file()
    assert (final / "diabetes" / "development_2019_2021.csv").is_file()
    assert (final / "hypertension" / "development_2019_2021.csv").is_file()
    with pytest.raises(FileExistsError, match="overwrite is prohibited"):
        materialize_development_frames(synthetic_config_path, canonical_path, output_root)
