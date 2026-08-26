import numpy as np
import pandas as pd
import pytest

from src.waist_submodel_pipeline import (
    KEY_COLS,
    build_pipeline,
    evaluate_oof,
    run_nested_oof,
)

FEATURES = ["age_years", "sex_code", "height_cm", "weight_kg"]
LABEL = "waist_cm"

SMALL_GRID = [
    {"family": "linear_regression", "params": {"fit_intercept": True}},
    {"family": "elastic_net", "params": {"alpha": 0.1, "l1_ratio": 0.5, "max_iter": 2000, "tol": 1e-4}},
    {
        "family": "random_forest_regressor",
        "params": {
            "n_estimators": 20,
            "max_depth": 4,
            "min_samples_leaf": 5,
            "max_features": 1.0,
            "criterion": "squared_error",
            "bootstrap": True,
            "random_state": 42,
        },
    },
]


def make_synthetic(n_per_year=120, years=(2019, 2020, 2021), seed=0):
    rng = np.random.default_rng(seed)
    rows = []
    pid = 0
    for year in years:
        for _ in range(n_per_year):
            pid += 1
            age = rng.integers(19, 80)
            sex = rng.choice([1, 2])
            height = rng.normal(165, 8)
            weight = rng.normal(65, 12)
            waist = 40 + 0.3 * weight + 0.1 * age + rng.normal(0, 2)
            rows.append(
                {
                    "source_year": year,
                    "participant_id": f"P{pid:05d}",
                    "age_years": age,
                    "sex_code": sex,
                    "height_cm": height,
                    "weight_kg": weight,
                    "waist_cm": waist,
                }
            )
    return pd.DataFrame(rows)


def make_fold_registries(task_df, seed, outer_folds=5, inner_folds=4):
    rng = np.random.default_rng(seed)
    keys = task_df[KEY_COLS].copy()
    keys["outer_fold"] = rng.integers(0, outer_folds, size=len(keys))
    outer_df = keys.copy()

    inner_rows = []
    for outer_fold in range(outer_folds):
        train_keys = outer_df.loc[outer_df["outer_fold"] != outer_fold, KEY_COLS].reset_index(drop=True)
        inner_assignment = rng.integers(0, inner_folds, size=len(train_keys))
        block = train_keys.copy()
        block["outer_fold"] = outer_fold
        block["inner_fold"] = inner_assignment
        inner_rows.append(block)
    inner_df = pd.concat(inner_rows, ignore_index=True)
    return outer_df, inner_df


@pytest.fixture(scope="module")
def synthetic_bundle():
    task_df = make_synthetic()
    outer_df, inner_df = make_fold_registries(task_df, seed=42)
    return task_df, outer_df, inner_df


def test_no_outer_holdout_leakage_into_training(synthetic_bundle):
    task_df, outer_df, inner_df = synthetic_bundle
    oof_df, _ = run_nested_oof(task_df, outer_df, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)
    assert len(oof_df) == len(task_df)


def test_inner_registry_leaking_holdout_key_is_blocked(synthetic_bundle):
    task_df, outer_df, inner_df = synthetic_bundle
    bad_inner = inner_df.copy()
    holdout_row = outer_df[outer_df["outer_fold"] == 0].iloc[[0]][KEY_COLS].copy()
    holdout_row["outer_fold"] = 0
    holdout_row["inner_fold"] = 0
    bad_inner = pd.concat([bad_inner, holdout_row], ignore_index=True)
    with pytest.raises(ValueError, match="leaks outer holdout"):
        run_nested_oof(task_df, outer_df, bad_inner, FEATURES, LABEL, candidates=SMALL_GRID)


def test_duplicate_oof_key_blocked(synthetic_bundle, monkeypatch):
    task_df, outer_df, inner_df = synthetic_bundle
    dup_task_df = pd.concat([task_df, task_df.iloc[[0]]], ignore_index=True)
    dup_outer_df = pd.concat([outer_df, outer_df.iloc[[0]]], ignore_index=True)
    with pytest.raises(ValueError):
        run_nested_oof(dup_task_df, dup_outer_df, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)


def test_task_frame_outer_registry_key_misalignment_blocked(synthetic_bundle):
    task_df, outer_df, inner_df = synthetic_bundle
    truncated_outer = outer_df.iloc[:-5].reset_index(drop=True)
    with pytest.raises(ValueError, match="do not align"):
        run_nested_oof(task_df, truncated_outer, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)


def test_same_seed_reproducibility(synthetic_bundle):
    task_df, outer_df, inner_df = synthetic_bundle
    oof_1, _ = run_nested_oof(task_df, outer_df, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)
    oof_2, _ = run_nested_oof(task_df, outer_df, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)
    merged = oof_1.merge(oof_2, on=KEY_COLS, suffixes=("_1", "_2"))
    assert np.allclose(merged["estimated_waist_cm_1"], merged["estimated_waist_cm_2"])


def test_forbidden_year_fails_closed(synthetic_bundle):
    task_df, outer_df, inner_df = synthetic_bundle
    contaminated = task_df.copy()
    contaminated.loc[0, "source_year"] = 2022
    with pytest.raises(ValueError, match="reserved/forbidden years"):
        run_nested_oof(contaminated, outer_df, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)

    contaminated2 = task_df.copy()
    contaminated2.loc[0, "source_year"] = 2024
    with pytest.raises(ValueError, match="reserved/forbidden years"):
        run_nested_oof(contaminated2, outer_df, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)


def test_oof_label_key_alignment_validated(synthetic_bundle):
    task_df, outer_df, inner_df = synthetic_bundle
    oof_df, _ = run_nested_oof(task_df, outer_df, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)
    metrics = evaluate_oof(oof_df, task_df, LABEL)
    assert metrics["n"] == len(task_df)
    assert metrics["mae_cm"] >= 0

    shuffled_oof = oof_df.copy()
    shuffled_oof["participant_id"] = shuffled_oof["participant_id"].sample(frac=1, random_state=1).to_numpy()
    with pytest.raises(ValueError, match="key alignment failed"):
        evaluate_oof(shuffled_oof, task_df, LABEL)


def test_scaler_fit_only_within_given_training_data():
    pipe = build_pipeline("elastic_net", {"alpha": 0.1, "l1_ratio": 0.5, "max_iter": 2000, "tol": 1e-4})
    X_a = pd.DataFrame({"x": [1.0, 2.0, 3.0]})
    y_a = pd.Series([1.0, 2.0, 3.0])
    pipe.fit(X_a, y_a)
    mean_a = pipe.named_steps["scaler"].mean_.copy()

    X_b = pd.DataFrame({"x": [100.0, 200.0, 300.0]})
    y_b = pd.Series([1.0, 2.0, 3.0])
    pipe.fit(X_b, y_b)
    mean_b = pipe.named_steps["scaler"].mean_.copy()

    assert not np.allclose(mean_a, mean_b)
    assert np.isclose(mean_b[0], 200.0)


def test_self_row_not_used_for_its_own_prediction(synthetic_bundle):
    task_df, outer_df, inner_df = synthetic_bundle
    oof_df, _ = run_nested_oof(task_df, outer_df, inner_df, FEATURES, LABEL, candidates=SMALL_GRID)
    merged = oof_df.merge(outer_df, on=KEY_COLS, suffixes=("_oof", "_registry"))
    assert (merged["outer_fold_oof"] == merged["outer_fold_registry"]).all()
