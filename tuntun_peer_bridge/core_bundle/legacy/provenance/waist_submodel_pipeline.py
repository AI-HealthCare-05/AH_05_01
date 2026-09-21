"""Nested cross-fitted waist-circumference submodel per
contracts/model_experiment_protocol_v0_2.yaml (waist_submodel section).

Produces, for each stability seed, an out-of-fold (OOF) `estimated_waist_cm`
for every row such that the model that produced a row's prediction never saw
that row (nor any other row from the same outer holdout) during fitting.
Hyperparameter/candidate selection for each outer fold uses only the inner
4-fold split of that outer fold's training partition -- never the outer
holdout -- so selection is nested inside the OOF generation.

All scalers/preprocessors are fit inside sklearn Pipelines so they only ever
see the training partition of whichever fold they are fit within.
"""

from __future__ import annotations

import itertools
from dataclasses import dataclass
from typing import Iterable

import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, RegressorMixin, clone
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import ElasticNet, LinearRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

KEY_COLS = ["source_year", "participant_id"]
ALLOWED_YEARS = {2019, 2020, 2021}

F0_FALLBACK = ["age_years", "sex_code", "height_cm", "weight_kg"]
F2_LEISURE = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]
INPUT_TIERS = {"W0": F0_FALLBACK, "W2": F2_LEISURE}

SIMPLICITY_RANK = {"linear_regression": 0, "elastic_net": 1, "random_forest_regressor": 2}


def candidate_grid() -> list[dict]:
    """Approved candidate/hyperparameter grid from
    contracts/model_experiment_protocol_v0_2.yaml waist_submodel.candidates.
    Do not edit without a new protocol version.
    """
    grid: list[dict] = [{"family": "linear_regression", "params": {"fit_intercept": True}}]

    for alpha, l1_ratio in itertools.product(
        [0.0001, 0.001, 0.01, 0.1, 1.0, 10.0], [0.1, 0.5, 0.9, 1.0]
    ):
        grid.append(
            {
                "family": "elastic_net",
                "params": {"alpha": alpha, "l1_ratio": l1_ratio, "max_iter": 20000, "tol": 0.0001},
            }
        )

    for max_depth, min_samples_leaf, max_features in itertools.product(
        [6, 12, None], [1, 5, 20], [0.7, 1.0]
    ):
        grid.append(
            {
                "family": "random_forest_regressor",
                "params": {
                    "n_estimators": 500,
                    "max_depth": max_depth,
                    "min_samples_leaf": min_samples_leaf,
                    "max_features": max_features,
                    "criterion": "squared_error",
                    "bootstrap": True,
                    "random_state": 42,
                },
            }
        )
    return grid


def build_pipeline(family: str, params: dict) -> Pipeline:
    if family == "linear_regression":
        return Pipeline([("model", LinearRegression(**params))])
    if family == "elastic_net":
        return Pipeline([("scaler", StandardScaler()), ("model", ElasticNet(**params))])
    if family == "random_forest_regressor":
        return Pipeline([("model", RandomForestRegressor(n_jobs=-1, **params))])
    raise ValueError(f"unknown candidate family: {family}")


def candidate_name(cand: dict) -> str:
    if cand["family"] == "elastic_net":
        return f"elastic_net(alpha={cand['params']['alpha']},l1_ratio={cand['params']['l1_ratio']})"
    if cand["family"] == "random_forest_regressor":
        p = cand["params"]
        return f"random_forest_regressor(max_depth={p['max_depth']},min_samples_leaf={p['min_samples_leaf']},max_features={p['max_features']})"
    return cand["family"]


def _mae(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    return float(np.mean(np.abs(y_true - y_pred)))


def _rmse(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    return float(np.sqrt(np.mean((y_true - y_pred) ** 2)))


def _worst_subgroup_mae(df: pd.DataFrame, y_true_col: str, y_pred_col: str, min_n: int = 100) -> float:
    subgroup_defs = {
        "sex_code": df["sex_code"],
        "age_band": pd.cut(
            df["age_years"], bins=[0, 39, 64, 200], labels=["19_39", "40_64", "65_plus"]
        ),
        "source_year": df["source_year"],
    }
    worst = -np.inf
    for _, groups in subgroup_defs.items():
        for _, sub in df.groupby(groups, observed=True):
            if len(sub) < min_n:
                continue
            m = _mae(sub[y_true_col].to_numpy(), sub[y_pred_col].to_numpy())
            worst = max(worst, m)
    return worst if worst != -np.inf else float("nan")


def _assert_years_allowed(df: pd.DataFrame) -> None:
    bad = set(df["source_year"].unique()) - ALLOWED_YEARS
    if bad:
        raise ValueError(f"reserved/forbidden years present, aborting fail-closed: {sorted(bad)}")


@dataclass
class OuterFoldSelection:
    outer_fold: int
    candidate: str
    inner_cv_mae: float
    inner_cv_rmse: float


def _inner_cv_score(
    train_df: pd.DataFrame,
    inner_assignments: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    cand: dict,
) -> tuple[float, float, pd.DataFrame]:
    """Score one candidate via the 4-fold inner split of one outer fold's
    training partition. Returns (mae, rmse, inner_oof_frame) where
    inner_oof_frame carries predictions made by a model that excluded that
    inner fold's rows -- used only for selection, never as the final OOF.
    """
    merged = train_df.merge(inner_assignments, on=KEY_COLS, how="inner")
    preds = np.full(len(merged), np.nan)
    for inner_fold in sorted(merged["inner_fold"].unique()):
        val_mask = merged["inner_fold"].to_numpy() == inner_fold
        fit_mask = ~val_mask
        pipe = build_pipeline(cand["family"], cand["params"])
        pipe.fit(merged.loc[fit_mask, feature_cols], merged.loc[fit_mask, label_col])
        preds[val_mask] = pipe.predict(merged.loc[val_mask, feature_cols])
    y_true = merged[label_col].to_numpy()
    mae = _mae(y_true, preds)
    rmse = _rmse(y_true, preds)
    out = merged[KEY_COLS].copy()
    out["inner_pred"] = preds
    out[label_col] = y_true
    return mae, rmse, out


def select_best_candidate(
    train_df: pd.DataFrame,
    inner_assignments: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    candidates: Iterable[dict],
) -> tuple[dict, OuterFoldSelection]:
    scored = []
    for cand in candidates:
        mae, rmse, inner_oof = _inner_cv_score(train_df, inner_assignments, feature_cols, label_col, cand)
        worst_sub = _worst_subgroup_mae(
            inner_oof.merge(train_df[KEY_COLS + ["sex_code", "age_years"]], on=KEY_COLS),
            label_col,
            "inner_pred",
        )
        simplicity = SIMPLICITY_RANK[cand["family"]]
        scored.append((mae, rmse, worst_sub, simplicity, cand))
    scored.sort(key=lambda t: (t[0], t[1], t[2], t[3]))
    best_mae, best_rmse, _, _, best_cand = scored[0]
    return best_cand, OuterFoldSelection(
        outer_fold=-1, candidate=candidate_name(best_cand), inner_cv_mae=best_mae, inner_cv_rmse=best_rmse
    )


def run_nested_oof(
    task_df: pd.DataFrame,
    outer_df: pd.DataFrame,
    inner_df: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    candidates: list[dict] | None = None,
) -> tuple[pd.DataFrame, list[OuterFoldSelection]]:
    """Full nested cross-fitting for one seed. Returns (oof_df, selections).

    oof_df has one row per input key with an `estimated_waist_cm` produced by
    a model that (a) never trained on that row and (b) never trained on any
    row from that row's outer holdout fold.
    """
    _assert_years_allowed(task_df)
    _assert_years_allowed(outer_df)
    _assert_years_allowed(inner_df)

    candidates = candidates or candidate_grid()
    merged = task_df.merge(outer_df[KEY_COLS + ["outer_fold"]], on=KEY_COLS, how="inner", validate="one_to_one")
    if len(merged) != len(task_df):
        raise ValueError("task frame and outer registry keys do not align 1:1 -- aborting fail-closed")

    oof_rows = []
    selections: list[OuterFoldSelection] = []
    for outer_fold in sorted(merged["outer_fold"].unique()):
        train_mask = merged["outer_fold"] != outer_fold
        holdout_mask = merged["outer_fold"] == outer_fold
        train_df = merged.loc[train_mask].reset_index(drop=True)
        holdout_df = merged.loc[holdout_mask].reset_index(drop=True)

        holdout_keys = set(holdout_df[KEY_COLS].itertuples(index=False, name=None))
        train_keys = set(train_df[KEY_COLS].itertuples(index=False, name=None))
        if holdout_keys & train_keys:
            raise ValueError("outer holdout/train key overlap detected -- aborting fail-closed")

        inner_for_fold = inner_df.loc[inner_df["outer_fold"] == outer_fold, KEY_COLS + ["inner_fold"]]
        inner_keys = set(inner_for_fold[KEY_COLS].itertuples(index=False, name=None))
        if inner_keys & holdout_keys:
            raise ValueError("inner registry leaks outer holdout keys -- aborting fail-closed")

        best_cand, sel = select_best_candidate(train_df, inner_for_fold, feature_cols, label_col, candidates)
        sel.outer_fold = int(outer_fold)
        selections.append(sel)

        final_pipe = build_pipeline(best_cand["family"], best_cand["params"])
        final_pipe.fit(train_df[feature_cols], train_df[label_col])
        holdout_pred = final_pipe.predict(holdout_df[feature_cols])

        out = holdout_df[KEY_COLS].copy()
        out["outer_fold"] = outer_fold
        out["estimated_waist_cm"] = holdout_pred
        out["selected_candidate"] = candidate_name(best_cand)
        oof_rows.append(out)

    oof_df = pd.concat(oof_rows, ignore_index=True)

    dup = oof_df.duplicated(subset=KEY_COLS).sum()
    if dup:
        raise ValueError(f"duplicate OOF keys produced -- aborting fail-closed (n={dup})")
    if len(oof_df) != len(task_df):
        raise ValueError("OOF row count does not match task frame -- missing keys, aborting fail-closed")

    return oof_df, selections


def evaluate_oof(oof_df: pd.DataFrame, task_df: pd.DataFrame, label_col: str) -> dict:
    merged = oof_df.merge(task_df[KEY_COLS + [label_col]], on=KEY_COLS, how="inner", validate="one_to_one")
    if len(merged) != len(oof_df):
        raise ValueError("OOF/label key alignment failed -- aborting fail-closed")
    y_true = merged[label_col].to_numpy()
    y_pred = merged["estimated_waist_cm"].to_numpy()
    resid = y_true - y_pred
    return {
        "n": int(len(merged)),
        "mae_cm": _mae(y_true, y_pred),
        "rmse_cm": _rmse(y_true, y_pred),
        "mean_residual_cm": float(np.mean(resid)),
        "abs_mean_residual_cm": float(abs(np.mean(resid))),
        "within_5cm_rate": float(np.mean(np.abs(resid) <= 5.0)),
        "within_10cm_rate": float(np.mean(np.abs(resid) <= 10.0)),
    }
