"""Synthetic-only tests for the secure split pipeline."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pandas as pd
import pytest
import yaml

from src.file_integrity import sha256_file
from src.split_dataset import build_splits, run_split_pipeline
from src.split_validation import load_config, validate_source_data


APP_INPUT_FEATURES = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]


@pytest.fixture
def classification_frame() -> pd.DataFrame:
    """Return balanced synthetic classification rows for allowed 2019 through 2023."""
    rows: list[dict[str, object]] = []
    for year in range(2019, 2024):
        for index in range(60):
            rows.append(
                {
                    "record_key": f"synthetic-{year}-{index:03d}",
                    "source_survey_year": year,
                    "diabetes_label": int(index % 4 == 0),
                    "age": 20 + index % 55,
                    "bmi": 18.0 + index / 20,
                }
            )
    return pd.DataFrame(rows)


@pytest.fixture
def regression_frame() -> pd.DataFrame:
    """Return synthetic continuous-target rows for allowed 2019 through 2023."""
    rows: list[dict[str, object]] = []
    for year in range(2019, 2024):
        for index in range(80):
            rows.append(
                {
                    "record_key": f"regression-{year}-{index:03d}",
                    "source_survey_year": year,
                    "waist_cm": 60.0 + (year - 2019) * 0.2 + index * 0.35,
                    "age": 20 + index % 60,
                    "bmi": 17.0 + index / 25,
                }
            )
    return pd.DataFrame(rows)


def _config_payload(task_type: str = "classification") -> dict[str, object]:
    """Return a complete synthetic split configuration mapping."""
    classification = task_type == "classification"
    return {
        "task": "diabetes" if classification else "waist",
        "task_type": task_type,
        "dataset_version": "synthetic_v1",
        "id_column": "record_key",
        "year_column": "source_survey_year",
        "label_column": "diabetes_label" if classification else "waist_cm",
        "feature_columns": ["age", "bmi"],
        "development_years": [2019, 2020, 2021],
        "temporal_validation_years": [2022],
        "locked_test_years": [2023],
        "forbidden_years": [2024],
        "random_validation_size": 0.2,
        "random_seed": 42,
        "classification_stratify": classification,
        "regression_stratify": "none" if classification else "quantile",
        "regression_stratify_bins": 5,
        "csv_encoding": "utf-8-sig",
        "output_format": "csv",
        "do_not_split": False,
    }


def _write_config(path: Path, task_type: str = "classification") -> Path:
    """Write a synthetic YAML configuration and return its path."""
    path.write_text(
        yaml.safe_dump(_config_payload(task_type), sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    return path


def _validated_splits(
    tmp_path: Path, frame: pd.DataFrame, task_type: str = "classification"
) -> tuple[object, dict[str, pd.DataFrame]]:
    """Load a temporary config, validate synthetic data, and build splits."""
    config = load_config(_write_config(tmp_path / f"{task_type}.yaml", task_type))
    validated = validate_source_data(frame, config)
    return config, build_splits(validated, config)


def test_production_configs_share_years_and_six_input_contract() -> None:
    """Every shipped task config must use the agreed cohort and app-input contract."""
    config_dir = Path(__file__).resolve().parents[1] / "configs"
    for config_path in sorted(config_dir.glob("*_split.yaml")):
        config = load_config(config_path)
        assert list(config.feature_columns) == APP_INPUT_FEATURES
        assert config.development_years == (2019, 2020, 2021)
        assert config.temporal_validation_years == (2022,)
        assert config.locked_test_years == (2023,)
        assert config.forbidden_years == (2024,)
        assert config.do_not_split is True


def test_same_seed_produces_identical_splits(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """The same frame, settings, and seed must select the same ordered IDs."""
    config_path = _write_config(tmp_path / "config.yaml")
    config = load_config(config_path)
    validated = validate_source_data(classification_frame, config)
    first = build_splits(validated, config)
    second = build_splits(validated, config)
    for name in first:
        assert first[name][config.id_column].tolist() == second[name][config.id_column].tolist()


def test_frozen_year_roles_cannot_be_changed_in_yaml(tmp_path: Path) -> None:
    """Changing a role requires a versioned contract, not an ad-hoc YAML edit."""
    payload = _config_payload()
    payload["temporal_validation_years"] = [2023]
    payload["locked_test_years"] = [2022]
    path = tmp_path / "wrong_roles.yaml"
    path.write_text(yaml.safe_dump(payload, sort_keys=False), encoding="utf-8")
    with pytest.raises(ValueError, match="TMTN A5 v0.1"):
        load_config(path)


def test_train_validation_locked_test_ids_do_not_overlap(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """Random/temporal subsets must never overlap locked internal-test IDs."""
    config, splits = _validated_splits(tmp_path, classification_frame)
    locked_ids = set(splits["locked_test"][config.id_column])
    for prefix in ["random", "temporal"]:
        train_ids = set(splits[f"{prefix}_train"][config.id_column])
        validation_ids = set(splits[f"{prefix}_validation"][config.id_column])
        assert train_ids.isdisjoint(validation_ids)
        assert train_ids.isdisjoint(locked_ids)
        assert validation_ids.isdisjoint(locked_ids)


def test_2023_is_locked_and_absent_from_development(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """No development or temporal-validation artifact may contain a 2023 row."""
    config, splits = _validated_splits(tmp_path, classification_frame)
    for name in ["random_train", "random_validation", "temporal_train", "temporal_validation"]:
        assert not splits[name][config.year_column].eq(2023).any()
    assert set(splits["locked_test"][config.year_column]) == {2023}


def test_2024_is_rejected_as_forbidden(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """A forbidden 2024 row must fail instead of being filtered or assigned."""
    config = load_config(_write_config(tmp_path / "config.yaml"))
    forbidden = classification_frame.iloc[[0]].copy()
    forbidden.loc[:, config.id_column] = "forbidden-2024"
    forbidden.loc[:, config.year_column] = 2024
    damaged = pd.concat([classification_frame, forbidden], ignore_index=True)
    with pytest.raises(ValueError, match="forbidden years: \\[2024\\]"):
        validate_source_data(damaged, config)


def test_temporal_years_are_exact(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """Temporal subsets must exactly implement their configured year sets."""
    config, splits = _validated_splits(tmp_path, classification_frame)
    assert set(splits["temporal_train"][config.year_column]) == {2019, 2020, 2021}
    assert set(splits["temporal_validation"][config.year_column]) == {2022}


def test_random_split_uses_only_2019_through_2021(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """Both random subsets must come only from configured development years."""
    config, splits = _validated_splits(tmp_path, classification_frame)
    random_years = set(
        pd.concat([splits["random_train"], splits["random_validation"]])[config.year_column]
    )
    assert random_years == {2019, 2020, 2021}


def test_classification_stratification_preserves_ratio(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """Random train and validation class rates must stay near the development rate."""
    config, splits = _validated_splits(tmp_path, classification_frame)
    development_rate = classification_frame.query("source_survey_year <= 2021")[
        config.label_column
    ].mean()
    assert abs(splits["random_train"][config.label_column].mean() - development_rate) <= 0.02
    assert (
        abs(splits["random_validation"][config.label_column].mean() - development_rate) <= 0.02
    )


def test_regression_quantile_stratification_works(
    tmp_path: Path, regression_frame: pd.DataFrame
) -> None:
    """Every global development quantile must be represented at a similar split rate."""
    config, splits = _validated_splits(tmp_path, regression_frame, "regression")
    development = regression_frame.query("source_survey_year <= 2021").copy()
    development["bin"] = pd.qcut(development[config.label_column], q=5, labels=False)
    id_to_bin = development.set_index(config.id_column)["bin"]
    validation_bins = splits["random_validation"][config.id_column].map(id_to_bin)
    counts = validation_bins.value_counts().sort_index()
    assert set(counts.index) == {0, 1, 2, 3, 4}
    expected_per_bin = len(splits["random_validation"]) / 5
    assert np.all(np.abs(counts.to_numpy() - expected_per_bin) <= 1)


@pytest.mark.parametrize("problem", ["duplicate", "missing"])
def test_invalid_ids_fail(
    tmp_path: Path, classification_frame: pd.DataFrame, problem: str
) -> None:
    """Duplicate and missing IDs must each stop validation."""
    config = load_config(_write_config(tmp_path / "config.yaml"))
    damaged = classification_frame.copy()
    if problem == "duplicate":
        damaged.loc[1, config.id_column] = damaged.loc[0, config.id_column]
    else:
        damaged.loc[0, config.id_column] = None
    with pytest.raises(ValueError, match="ID column|Composite record key"):
        validate_source_data(damaged, config)


def test_same_id_string_in_different_years_is_a_distinct_record(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """Cross-sectional IDs may repeat across years; year+ID remains unique."""
    config = load_config(_write_config(tmp_path / "config.yaml"))
    adjusted = classification_frame.copy()
    first_2019 = adjusted.index[adjusted[config.year_column].eq(2019)][0]
    first_2020 = adjusted.index[adjusted[config.year_column].eq(2020)][0]
    adjusted.loc[first_2020, config.id_column] = adjusted.loc[first_2019, config.id_column]
    validated = validate_source_data(adjusted, config)
    assert len(validated) == len(adjusted)


def test_non_binary_classification_label_fails(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """A classification value outside 0/1 must stop validation."""
    config = load_config(_write_config(tmp_path / "config.yaml"))
    damaged = classification_frame.copy()
    damaged.loc[0, config.label_column] = 2
    with pytest.raises(ValueError, match="only 0 and 1"):
        validate_source_data(damaged, config)


def test_existing_output_is_blocked_without_force(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """A second run must not replace an existing task/version by default."""
    config_path = _write_config(tmp_path / "config.yaml")
    input_path = tmp_path / "synthetic.csv"
    classification_frame.to_csv(input_path, index=False, encoding="utf-8-sig")
    output_root = tmp_path / "outputs"
    sealed_root = tmp_path / "restricted_labels"
    run_split_pipeline(config_path, input_path, output_root, sealed_root)
    with pytest.raises(FileExistsError, match="--force"):
        run_split_pipeline(config_path, input_path, output_root, sealed_root)


def test_production_do_not_split_gate_blocks_before_input_read(tmp_path: Path) -> None:
    """An unapproved production config must stop before trying to read source data."""
    config_path = tmp_path / "blocked.yaml"
    payload = _config_payload()
    payload["do_not_split"] = True
    config_path.write_text(
        yaml.safe_dump(payload, sort_keys=False), encoding="utf-8"
    )
    missing_input = tmp_path / "must_not_be_read.csv"
    with pytest.raises(RuntimeError, match="do_not_split: true"):
        run_split_pipeline(
            config_path,
            missing_input,
            tmp_path / "outputs",
            tmp_path / "restricted",
        )


def test_input_file_is_unchanged(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """A successful pipeline run must preserve the exact source CSV bytes."""
    config_path = _write_config(tmp_path / "config.yaml")
    input_path = tmp_path / "합성_입력.csv"
    classification_frame.to_csv(input_path, index=False, encoding="utf-8-sig")
    before = sha256_file(input_path)
    run_split_pipeline(
        config_path,
        input_path,
        tmp_path / "한글_출력",
        tmp_path / "제한된_정답",
    )
    assert sha256_file(input_path) == before


def test_manifests_do_not_expose_locked_test_label_statistics(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """Manifests may contain row counts/hashes but no class counts or target statistics."""
    config_path = _write_config(tmp_path / "config.yaml")
    input_path = tmp_path / "synthetic.csv"
    classification_frame.to_csv(input_path, index=False, encoding="utf-8-sig")
    final_root = run_split_pipeline(
        config_path,
        input_path,
        tmp_path / "outputs",
        tmp_path / "restricted_labels",
    )
    manifests = [
        json.loads((final_root / "manifests/split_manifest.json").read_text(encoding="utf-8")),
        json.loads((final_root / "manifests/integrity_manifest.json").read_text(encoding="utf-8")),
    ]
    forbidden_keys = {
        "label_distribution",
        "label_counts",
        "class_counts",
        "target_mean",
        "target_statistics",
        "examples",
        "sample_rows",
    }

    def all_keys(value: object) -> set[str]:
        """Recursively collect mapping keys from parsed JSON."""
        if isinstance(value, dict):
            return set(value) | set().union(*(all_keys(item) for item in value.values()))
        if isinstance(value, list):
            return set().union(*(all_keys(item) for item in value), set())
        return set()

    for manifest in manifests:
        assert all_keys(manifest).isdisjoint(forbidden_keys)
    split_manifest = manifests[0]
    assert split_manifest["privacy"]["locked_test_label_statistics_included"] is False
    assert split_manifest["privacy"]["row_examples_included"] is False


def test_locked_test_is_blinded_and_answer_key_is_separate(
    tmp_path: Path, classification_frame: pd.DataFrame
) -> None:
    """Developer locked test must exclude labels while its answer key stays minimal."""
    config_path = _write_config(tmp_path / "config.yaml")
    input_path = tmp_path / "synthetic.csv"
    classification_frame.to_csv(input_path, index=False, encoding="utf-8-sig")
    output_root = tmp_path / "outputs"
    sealed_root = tmp_path / "restricted_labels"

    final_root = run_split_pipeline(config_path, input_path, output_root, sealed_root)
    blinded = pd.read_csv(
        final_root / "locked_internal_test/test_2023_features.csv",
        encoding="utf-8-sig",
    )
    answer_key = pd.read_csv(
        sealed_root / "diabetes/synthetic_v1/test_2023_labels.csv",
        encoding="utf-8-sig",
    )

    assert "diabetes_label" not in blinded.columns
    assert list(answer_key.columns) == [
        "record_key",
        "source_survey_year",
        "diabetes_label",
    ]
    assert set(blinded["record_key"]) == set(answer_key["record_key"])


@pytest.mark.parametrize(
    "sealed_root_factory",
    [
        lambda output: output,
        lambda output: output / "nested",
        lambda output: output.parent,
    ],
)
def test_locked_label_root_cannot_overlap_development_output(
    tmp_path: Path,
    classification_frame: pd.DataFrame,
    sealed_root_factory: object,
) -> None:
    """The answer-key root must not equal, contain, or sit inside the developer root."""
    config_path = _write_config(tmp_path / "config.yaml")
    input_path = tmp_path / "synthetic.csv"
    classification_frame.to_csv(input_path, index=False, encoding="utf-8-sig")
    output_root = tmp_path / "outputs"
    sealed_root = sealed_root_factory(output_root)

    with pytest.raises(ValueError, match="separate, non-nested root"):
        run_split_pipeline(config_path, input_path, output_root, sealed_root)
