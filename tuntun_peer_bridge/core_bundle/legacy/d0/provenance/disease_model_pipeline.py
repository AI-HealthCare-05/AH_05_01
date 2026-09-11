"""D0 disease-model nested cross-fitting per
contracts/model_experiment_protocol_v0_2.yaml (disease_models section) and
contracts/d0_calibration_crossfit_addendum_v0_1.yaml (fold-nesting structure
for calibrated-Brier candidate+calibrator selection).

Scope of this module (development stage 9, D0 only):
  - approved candidate/hyperparameter grid (logistic_regression,
    random_forest_classifier)
  - fold-safe pipeline construction (scaler fit only inside a Pipeline)
  - fully-nested 3-layer cross-fitting (outer 5-fold evaluation / inner
    4-fold candidate+calibrator selection / an inner-train-only 3-fold
    cross-fit to fit Platt without ever touching the fold it calibrates)
  - per-fold two-class support validation at every fit boundary
    (fail-closed, no silent recovery)
  - raw + calibrated probability diagnostics: Brier, log loss, ROC-AUC,
    PR-AUC, calibration intercept/slope, frozen-quantile 10-bin ECE

`fixed_candidate_diagnostic_oof` (below) is a single-candidate, no-selection
scaffold retained only to test the outer/inner leakage-guard machinery in
isolation -- it is NOT the D0 selection pipeline. The actual candidate- and
calibrator-selecting pipeline is `run_nested_oof_d0`.
"""

from __future__ import annotations

import itertools
from dataclasses import dataclass

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

KEY_COLS = ["source_year", "participant_id"]
ALLOWED_YEARS = {2019, 2020, 2021}
SIMPLICITY_RANK = {"logistic_regression": 0, "random_forest_classifier": 1}


def candidate_grid() -> list[dict]:
    """Approved candidate/hyperparameter grid from
    contracts/model_experiment_protocol_v0_2.yaml disease_models.candidates.
    Do not edit without a new protocol version.
    """
    grid: list[dict] = []
    for c, class_weight in itertools.product([0.01, 0.1, 1.0, 10.0, 100.0], [None, "balanced"]):
        grid.append(
            {
                "family": "logistic_regression",
                "params": {
                    "penalty": "l2",
                    "C": c,
                    "solver": "lbfgs",
                    "max_iter": 5000,
                    "class_weight": class_weight,
                },
            }
        )
    for max_depth, min_samples_leaf, max_features, class_weight in itertools.product(
        [4, 8, None], [5, 20, 50], ["sqrt", 1.0], [None, "balanced"]
    ):
        grid.append(
            {
                "family": "random_forest_classifier",
                "params": {
                    "n_estimators": 500,
                    "criterion": "log_loss",
                    "max_depth": max_depth,
                    "min_samples_leaf": min_samples_leaf,
                    "max_features": max_features,
                    "class_weight": class_weight,
                    "bootstrap": True,
                    "random_state": 42,
                },
            }
        )
    return grid


def build_pipeline(family: str, params: dict) -> Pipeline:
    if family == "logistic_regression":
        return Pipeline([("scaler", StandardScaler()), ("model", LogisticRegression(**params))])
    if family == "random_forest_classifier":
        return Pipeline([("model", RandomForestClassifier(n_jobs=-1, **params))])
    raise ValueError(f"unknown candidate family: {family}")


def candidate_name(cand: dict) -> str:
    p = cand["params"]
    if cand["family"] == "logistic_regression":
        return f"logistic_regression(C={p['C']},class_weight={p['class_weight']})"
    return (
        f"random_forest_classifier(max_depth={p['max_depth']},"
        f"min_samples_leaf={p['min_samples_leaf']},max_features={p['max_features']},"
        f"class_weight={p['class_weight']})"
    )


def _assert_years_allowed(df: pd.DataFrame) -> None:
    bad = set(df["source_year"].unique()) - ALLOWED_YEARS
    if bad:
        raise ValueError(f"reserved/forbidden years present, aborting fail-closed: {sorted(bad)}")


def _assert_both_classes_present(y: pd.Series, context: str) -> None:
    classes = set(np.unique(y))
    if classes != {0, 1} and classes != {0.0, 1.0}:
        raise ValueError(
            f"fold does not contain both classes required for training ({context}); "
            f"found={sorted(classes)} -- aborting fail-closed, no arbitrary recovery"
        )


def _assert_valid_probabilities(p: np.ndarray, context: str) -> None:
    if not np.all(np.isfinite(p)):
        raise ValueError(f"non-finite predicted probability detected ({context}) -- aborting fail-closed")
    if np.any(p < 0.0) or np.any(p > 1.0):
        raise ValueError(f"predicted probability outside [0,1] detected ({context}) -- aborting fail-closed")


def brier_score(y_true: np.ndarray, p: np.ndarray) -> float:
    return float(np.mean((p - y_true) ** 2))


def log_loss_safe(y_true: np.ndarray, p: np.ndarray, eps: float = 1e-12) -> float:
    p_clipped = np.clip(p, eps, 1 - eps)
    return float(-np.mean(y_true * np.log(p_clipped) + (1 - y_true) * np.log(1 - p_clipped)))


def roc_auc(y_true: np.ndarray, p: np.ndarray) -> float:
    from sklearn.metrics import roc_auc_score

    return float(roc_auc_score(y_true, p))


def pr_auc(y_true: np.ndarray, p: np.ndarray) -> float:
    from sklearn.metrics import average_precision_score

    return float(average_precision_score(y_true, p))


def calibration_intercept_slope(y_true: np.ndarray, p: np.ndarray, eps: float = 1e-12) -> tuple[float, float]:
    """Calibration-in-the-large diagnostic: logistic regression of
    y ~ logit(p), fit on the same array passed in. This is a descriptive fit
    of how mis-calibrated `p` already is -- it is NOT a calibrator meant to
    be reused for prediction, and callers must ensure `p` here is itself an
    out-of-fold array before calling this for reporting purposes.
    """
    p_clipped = np.clip(p, eps, 1 - eps)
    logit_p = np.log(p_clipped / (1 - p_clipped))
    lr = LogisticRegression(penalty=None, solver="lbfgs", max_iter=5000)
    lr.fit(logit_p.reshape(-1, 1), y_true)
    return float(lr.intercept_[0]), float(lr.coef_[0][0])


def exploratory_equal_width_ece(y_true: np.ndarray, p: np.ndarray) -> float:
    """Equal-width 10-bin ECE. Exploratory diagnostic only -- NOT the
    contract-defined gate ECE (see `frozen_quantile_ece` for that)."""
    bins = np.linspace(0.0, 1.0, 11)
    bin_idx = np.clip(np.digitize(p, bins) - 1, 0, 9)
    n = len(p)
    ece = 0.0
    for b in range(10):
        mask = bin_idx == b
        if not np.any(mask):
            continue
        conf = np.mean(p[mask])
        acc = np.mean(y_true[mask])
        ece += (np.sum(mask) / n) * abs(acc - conf)
    return float(ece)


def compute_frozen_quantile_boundaries(p_primary_seed: np.ndarray, n_bins: int = 10) -> np.ndarray:
    """Compute and freeze quantile bin boundaries from the PRIMARY seed's
    development OOF calibrated probabilities. Callers must compute this once
    per task (from seed 42 only) and pass the resulting array into
    `frozen_quantile_ece` for every seed -- this function must not be called
    again per stability seed.
    """
    raw_boundaries = np.quantile(p_primary_seed, np.linspace(0.0, 1.0, n_bins + 1))
    return np.unique(raw_boundaries)


def frozen_quantile_ece(y_true: np.ndarray, p: np.ndarray, boundaries: np.ndarray) -> tuple[float, int]:
    """Quantile-bin ECE using externally supplied, already-frozen boundaries.
    This function never derives boundaries from (y_true, p) itself, so it is
    structurally incapable of refitting bin edges to a stability seed's data.
    Returns (ece, effective_bin_count) where effective_bin_count accounts for
    boundary collapse from duplicate cutpoints (no jitter is injected).
    """
    effective_bin_count = len(boundaries) - 1
    if effective_bin_count < 1:
        raise ValueError("frozen boundaries collapsed to fewer than 1 bin -- aborting fail-closed")
    bin_idx = np.clip(np.digitize(p, boundaries[1:-1], right=False), 0, effective_bin_count - 1)
    n = len(p)
    ece = 0.0
    for b in range(effective_bin_count):
        mask = bin_idx == b
        if not np.any(mask):
            continue
        conf = np.mean(p[mask])
        acc = np.mean(y_true[mask])
        ece += (np.sum(mask) / n) * abs(acc - conf)
    return float(ece), effective_bin_count


@dataclass
class FoldClassSupport:
    outer_fold: int
    inner_fold: int | None
    n: int
    n_events: int
    n_non_events: int


def fit_predict_raw(
    train_df: pd.DataFrame,
    predict_df: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    cand: dict,
    context: str,
) -> np.ndarray:
    """Fit one candidate on train_df only and predict raw probabilities for
    predict_df. Scaler (if any) is fit inside the Pipeline on train_df only.
    """
    y_train = train_df[label_col].to_numpy()
    _assert_both_classes_present(pd.Series(y_train), context)
    pipe = build_pipeline(cand["family"], cand["params"])
    pipe.fit(train_df[feature_cols], y_train)
    proba = pipe.predict_proba(predict_df[feature_cols])[:, 1]
    _assert_valid_probabilities(proba, context)
    return proba


def fixed_candidate_diagnostic_oof(
    task_df: pd.DataFrame,
    outer_df: pd.DataFrame,
    inner_df: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    cand: dict,
) -> tuple[pd.DataFrame, list[FoldClassSupport]]:
    """NOT the D0 pipeline. Fixed-candidate diagnostic scaffold only: produces
    raw (uncalibrated) nested OOF probabilities for a single caller-supplied
    candidate across all 5 outer folds, with the outer/inner leakage guards
    also used by the real pipeline. Exists to unit-test the leakage-guard
    machinery in isolation from candidate/calibrator selection. For the
    actual D0 selection pipeline (calibrated Brier, identity/Platt, fully
    nested 3-layer cross-fitting) use `run_nested_oof_d0`.
    """
    _assert_years_allowed(task_df)
    _assert_years_allowed(outer_df)
    _assert_years_allowed(inner_df)

    merged = task_df.merge(outer_df[KEY_COLS + ["outer_fold"]], on=KEY_COLS, how="inner", validate="one_to_one")
    if len(merged) != len(task_df):
        raise ValueError("task frame and outer registry keys do not align 1:1 -- aborting fail-closed")

    oof_rows = []
    supports: list[FoldClassSupport] = []
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

        supports.append(
            FoldClassSupport(
                outer_fold=int(outer_fold),
                inner_fold=None,
                n=len(train_df),
                n_events=int(train_df[label_col].sum()),
                n_non_events=int((train_df[label_col] == 0).sum()),
            )
        )

        proba = fit_predict_raw(train_df, holdout_df, feature_cols, label_col, cand, context=f"outer_fold={outer_fold}")
        out = holdout_df[KEY_COLS].copy()
        out["outer_fold"] = outer_fold
        out["oof_probability_raw"] = proba
        oof_rows.append(out)

    oof_df = pd.concat(oof_rows, ignore_index=True)
    dup = oof_df.duplicated(subset=KEY_COLS).sum()
    if dup:
        raise ValueError(f"duplicate OOF keys produced -- aborting fail-closed (n={dup})")
    if len(oof_df) != len(task_df):
        raise ValueError("OOF row count does not match task frame -- missing keys, aborting fail-closed")

    return oof_df, supports


def evaluate_raw_oof(oof_df: pd.DataFrame, task_df: pd.DataFrame, label_col: str) -> dict:
    merged = oof_df.merge(task_df[KEY_COLS + [label_col]], on=KEY_COLS, how="inner", validate="one_to_one")
    if len(merged) != len(oof_df):
        raise ValueError("OOF/label key alignment failed -- aborting fail-closed")
    y = merged[label_col].to_numpy()
    p = merged["oof_probability_raw"].to_numpy()
    intercept, slope = calibration_intercept_slope(y, p)
    return {
        "n": int(len(merged)),
        "n_events": int(merged[label_col].sum()),
        "prevalence": float(np.mean(y)),
        "brier_raw": brier_score(y, p),
        "log_loss_raw": log_loss_safe(y, p),
        "roc_auc": roc_auc(y, p),
        "pr_auc": pr_auc(y, p),
        "calibration_intercept_raw": intercept,
        "calibration_slope_raw": slope,
        "exploratory_equal_width_ece_raw": exploratory_equal_width_ece(y, p),
    }


# ---------------------------------------------------------------------------
# Fully-nested 3-layer cross-fitting per
# contracts/d0_calibration_crossfit_addendum_v0_1.yaml
# ---------------------------------------------------------------------------

PLATT_LOGIT_EPSILON = 1e-6


class PlattCalibrator:
    """Unregularized (unpenalized) binary logistic recalibration on
    logit(raw_probability). The epsilon is used ONLY to keep the logit
    computation finite -- the underlying raw probability array passed in or
    out is never clipped/stored in modified form.
    """

    def __init__(self, eps: float = PLATT_LOGIT_EPSILON):
        self.eps = eps
        self.lr: LogisticRegression | None = None

    def _logit(self, p: np.ndarray) -> np.ndarray:
        p_clipped = np.clip(p, self.eps, 1 - self.eps)
        return np.log(p_clipped / (1 - p_clipped))

    def fit(self, raw_p: np.ndarray, y: np.ndarray) -> "PlattCalibrator":
        _assert_both_classes_present(pd.Series(y), "platt_calibrator_fit")
        logit = self._logit(raw_p)
        self.lr = LogisticRegression(penalty=None, solver="lbfgs", max_iter=5000)
        self.lr.fit(logit.reshape(-1, 1), y)
        return self

    def predict(self, raw_p: np.ndarray) -> np.ndarray:
        logit = self._logit(raw_p)
        return self.lr.predict_proba(logit.reshape(-1, 1))[:, 1]


def _leave_one_group_out_raw(
    df: pd.DataFrame,
    group_col: str,
    feature_cols: list[str],
    label_col: str,
    cand: dict,
    context: str,
) -> np.ndarray:
    """Cross-fit `cand` over the groups in `group_col`: for each group, fit on
    every OTHER group and predict on the held-out group. Returns an array
    aligned to `df`'s row order, with 100% coverage and no row predicted by a
    model that saw that row.
    """
    groups = df[group_col].to_numpy()
    unique_groups = np.unique(groups)
    if len(unique_groups) < 2:
        raise ValueError(
            f"leave-one-group-out cross-fit requires >=2 groups ({context}); found {len(unique_groups)}"
        )
    preds = np.full(len(df), np.nan)
    for g in unique_groups:
        val_mask = groups == g
        fit_mask = ~val_mask
        y_fit = df.loc[fit_mask, label_col].to_numpy()
        _assert_both_classes_present(pd.Series(y_fit), f"{context}|group={g}|fit")
        pipe = build_pipeline(cand["family"], cand["params"])
        pipe.fit(df.loc[fit_mask, feature_cols], y_fit)
        p = pipe.predict_proba(df.loc[val_mask, feature_cols])[:, 1]
        _assert_valid_probabilities(p, f"{context}|group={g}|predict")
        preds[val_mask] = p
    if np.any(np.isnan(preds)):
        raise ValueError(f"leave-one-group-out cross-fit left unfilled predictions ({context}) -- aborting fail-closed")
    return preds


def inner_cross_fitted_predictions(
    outer_train_df: pd.DataFrame,
    inner_assignments: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    cand: dict,
    calibrator_type: str,
    context: str = "",
) -> pd.DataFrame:
    """Layer 2 of the addendum's nested structure. For each inner evaluation
    fold j: fit the base model on inner-train (fold j excluded) and predict
    raw probabilities on fold j. For the identity candidate, that raw value
    IS the calibrated value. For the Platt candidate, a calibrator is fit on
    a further 3-fold cross-fit of inner-train ONLY (never touching fold j),
    then applied to fold j's raw predictions. Fold j's label is never used in
    any fit step that produces fold j's own prediction.

    Returns one row per outer-train key with columns: KEY_COLS, inner_fold,
    label_col, raw, calibrated -- full coverage of outer_train_df.
    """
    if calibrator_type not in ("identity", "platt"):
        raise ValueError(f"unknown calibrator_type: {calibrator_type}")

    merged = outer_train_df.merge(inner_assignments, on=KEY_COLS, how="inner", validate="one_to_one")
    if len(merged) != len(outer_train_df):
        raise ValueError(f"outer-train and inner registry keys do not align 1:1 ({context}) -- aborting fail-closed")

    inner_fold_arr = merged["inner_fold"].to_numpy()
    inner_folds = np.unique(inner_fold_arr)
    if len(inner_folds) < 2:
        raise ValueError(f"inner registry has fewer than 2 folds ({context}) -- aborting fail-closed")

    raw_all = np.full(len(merged), np.nan)
    calibrated_all = np.full(len(merged), np.nan)

    for j in inner_folds:
        eval_mask = inner_fold_arr == j
        train_mask = ~eval_mask
        inner_train_j = merged.loc[train_mask].reset_index(drop=True)
        eval_df = merged.loc[eval_mask]

        y_train_j = inner_train_j[label_col].to_numpy()
        _assert_both_classes_present(pd.Series(y_train_j), f"{context}|inner_eval_fold={j}|inner_train")

        pipe = build_pipeline(cand["family"], cand["params"])
        pipe.fit(inner_train_j[feature_cols], y_train_j)
        raw_j = pipe.predict_proba(eval_df[feature_cols])[:, 1]
        _assert_valid_probabilities(raw_j, f"{context}|inner_eval_fold={j}|raw_predict")
        raw_all[eval_mask] = raw_j

        if calibrator_type == "identity":
            calibrated_j = raw_j
        else:
            raw_cv = _leave_one_group_out_raw(
                inner_train_j, "inner_fold", feature_cols, label_col, cand,
                context=f"{context}|inner_eval_fold={j}|platt_internal_cv",
            )
            platt = PlattCalibrator().fit(raw_cv, y_train_j)
            calibrated_j = platt.predict(raw_j)
            _assert_valid_probabilities(calibrated_j, f"{context}|inner_eval_fold={j}|platt_calibrated")
        calibrated_all[eval_mask] = calibrated_j

    out = merged[KEY_COLS + ["inner_fold", label_col]].copy()
    out["raw"] = raw_all
    out["calibrated"] = calibrated_all
    return out


def score_from_predictions(pred_df: pd.DataFrame, label_col: str) -> dict:
    y = pred_df[label_col].to_numpy()
    raw = pred_df["raw"].to_numpy()
    calibrated = pred_df["calibrated"].to_numpy()
    intercept, slope = calibration_intercept_slope(y, calibrated)
    return {
        "brier_calibrated": brier_score(y, calibrated),
        "log_loss_calibrated": log_loss_safe(y, calibrated),
        "calibration_intercept_calibrated": intercept,
        "calibration_slope_calibrated": slope,
        "pr_auc": pr_auc(y, calibrated),
        "roc_auc": roc_auc(y, calibrated),
        "brier_raw": brier_score(y, raw),
        "log_loss_raw": log_loss_safe(y, raw),
    }


def select_best_candidate_and_calibrator(
    outer_train_df: pd.DataFrame,
    inner_assignments: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    candidates: list[dict],
    context: str = "",
) -> tuple[dict, list[dict]]:
    """Selection order per addendum: calibrated Brier (asc), log loss (asc),
    |calibration slope - 1| (asc), |calibration intercept| (asc), PR-AUC
    (desc), ROC-AUC (desc), model simplicity (asc). identity and platt are
    compared together per base-model candidate.
    """
    scored = []
    for cand in candidates:
        for calibrator_type in ("identity", "platt"):
            pred_df = inner_cross_fitted_predictions(
                outer_train_df, inner_assignments, feature_cols, label_col, cand, calibrator_type, context=context
            )
            m = score_from_predictions(pred_df, label_col)
            simplicity = SIMPLICITY_RANK[cand["family"]]
            sort_key = (
                m["brier_calibrated"],
                m["log_loss_calibrated"],
                abs(m["calibration_slope_calibrated"] - 1.0),
                abs(m["calibration_intercept_calibrated"] - 0.0),
                -m["pr_auc"],
                -m["roc_auc"],
                simplicity,
            )
            scored.append({"cand": cand, "calibrator_type": calibrator_type, "metrics": m, "sort_key": sort_key})
    scored.sort(key=lambda s: s["sort_key"])
    return scored[0], scored


def final_outer_holdout_prediction(
    outer_train_df: pd.DataFrame,
    outer_holdout_df: pd.DataFrame,
    inner_assignments_for_fold: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    cand: dict,
    calibrator_type: str,
    context: str = "",
) -> tuple[np.ndarray, np.ndarray]:
    """Layer 3 of the addendum's nested structure: refit the selected
    candidate on the full outer-train partition and predict the outer
    holdout (raw). If Platt was selected, fit it on an inner-4-fold
    cross-fit of outer-train ONLY (never the holdout), then apply it to the
    holdout's raw predictions.
    """
    y_train = outer_train_df[label_col].to_numpy()
    _assert_both_classes_present(pd.Series(y_train), f"{context}|outer_train_final")
    pipe = build_pipeline(cand["family"], cand["params"])
    pipe.fit(outer_train_df[feature_cols], y_train)
    raw_holdout = pipe.predict_proba(outer_holdout_df[feature_cols])[:, 1]
    _assert_valid_probabilities(raw_holdout, f"{context}|outer_holdout_raw")

    if calibrator_type == "identity":
        return raw_holdout, raw_holdout.copy()

    merged = outer_train_df.merge(inner_assignments_for_fold, on=KEY_COLS, how="inner", validate="one_to_one")
    if len(merged) != len(outer_train_df):
        raise ValueError(
            f"outer-train and inner registry keys do not align for outer-level cross-fit ({context}) -- aborting fail-closed"
        )
    holdout_keys = set(outer_holdout_df[KEY_COLS].itertuples(index=False, name=None))
    merged_keys = set(merged[KEY_COLS].itertuples(index=False, name=None))
    if holdout_keys & merged_keys:
        raise ValueError(f"outer holdout keys leaked into outer-level Platt cross-fit ({context}) -- aborting fail-closed")

    raw_cv_outer = _leave_one_group_out_raw(
        merged, "inner_fold", feature_cols, label_col, cand, context=f"{context}|outer_level_platt_cv"
    )
    platt = PlattCalibrator().fit(raw_cv_outer, merged[label_col].to_numpy())
    calibrated_holdout = platt.predict(raw_holdout)
    _assert_valid_probabilities(calibrated_holdout, f"{context}|outer_holdout_calibrated")
    return raw_holdout, calibrated_holdout


def run_nested_oof_d0(
    task_df: pd.DataFrame,
    outer_df: pd.DataFrame,
    inner_df: pd.DataFrame,
    feature_cols: list[str],
    label_col: str,
    candidates: list[dict] | None = None,
    task_name: str = "",
    seed: int | None = None,
) -> tuple[pd.DataFrame, list[dict]]:
    """The D0 selection pipeline: for each outer fold, selects the
    calibrated-Brier-best (model, calibrator) combo using only that outer
    fold's training partition (fully nested per the addendum), then produces
    raw + calibrated OOF probabilities for the outer holdout from a model
    that never trained on it.
    """
    _assert_years_allowed(task_df)
    _assert_years_allowed(outer_df)
    _assert_years_allowed(inner_df)

    candidates = candidates or candidate_grid()
    merged = task_df.merge(outer_df[KEY_COLS + ["outer_fold"]], on=KEY_COLS, how="inner", validate="one_to_one")
    if len(merged) != len(task_df):
        raise ValueError("task frame and outer registry keys do not align 1:1 -- aborting fail-closed")

    oof_rows = []
    selections: list[dict] = []
    for outer_fold in sorted(merged["outer_fold"].unique()):
        train_mask = merged["outer_fold"] != outer_fold
        holdout_mask = merged["outer_fold"] == outer_fold
        outer_train_df = merged.loc[train_mask].reset_index(drop=True)
        outer_holdout_df = merged.loc[holdout_mask].reset_index(drop=True)

        holdout_keys = set(outer_holdout_df[KEY_COLS].itertuples(index=False, name=None))
        train_keys = set(outer_train_df[KEY_COLS].itertuples(index=False, name=None))
        if holdout_keys & train_keys:
            raise ValueError("outer holdout/train key overlap detected -- aborting fail-closed")

        inner_for_fold = inner_df.loc[inner_df["outer_fold"] == outer_fold, KEY_COLS + ["inner_fold"]]
        inner_keys = set(inner_for_fold[KEY_COLS].itertuples(index=False, name=None))
        if inner_keys & holdout_keys:
            raise ValueError("inner registry leaks outer holdout keys -- aborting fail-closed")

        context = f"task={task_name}|seed={seed}|outer_fold={outer_fold}"
        best, _all_scored = select_best_candidate_and_calibrator(
            outer_train_df, inner_for_fold, feature_cols, label_col, candidates, context=context
        )
        raw_holdout, calibrated_holdout = final_outer_holdout_prediction(
            outer_train_df, outer_holdout_df, inner_for_fold, feature_cols, label_col,
            best["cand"], best["calibrator_type"], context=context,
        )

        out = outer_holdout_df[KEY_COLS].copy()
        out["outer_fold"] = outer_fold
        out["oof_probability_raw"] = raw_holdout
        out["oof_probability_calibrated"] = calibrated_holdout
        out["selected_candidate"] = candidate_name(best["cand"])
        out["selected_calibrator"] = best["calibrator_type"]
        oof_rows.append(out)

        selections.append(
            {
                "outer_fold": int(outer_fold),
                "candidate": candidate_name(best["cand"]),
                "calibrator": best["calibrator_type"],
                "inner_selection_metrics": best["metrics"],
                "n_train": int(len(outer_train_df)),
                "n_events_train": int(outer_train_df[label_col].sum()),
            }
        )

    oof_df = pd.concat(oof_rows, ignore_index=True)
    dup = oof_df.duplicated(subset=KEY_COLS).sum()
    if dup:
        raise ValueError(f"duplicate OOF keys produced -- aborting fail-closed (n={dup})")
    if len(oof_df) != len(task_df):
        raise ValueError("OOF row count does not match task frame -- missing keys, aborting fail-closed")

    return oof_df, selections


def evaluate_oof_d0(
    oof_df: pd.DataFrame,
    task_df: pd.DataFrame,
    label_col: str,
    frozen_boundaries: np.ndarray | None = None,
) -> dict:
    merged = oof_df.merge(task_df[KEY_COLS + [label_col]], on=KEY_COLS, how="inner", validate="one_to_one")
    if len(merged) != len(oof_df):
        raise ValueError("OOF/label key alignment failed -- aborting fail-closed")
    y = merged[label_col].to_numpy()
    raw = merged["oof_probability_raw"].to_numpy()
    calibrated = merged["oof_probability_calibrated"].to_numpy()

    intercept_c, slope_c = calibration_intercept_slope(y, calibrated)
    intercept_r, slope_r = calibration_intercept_slope(y, raw)

    metrics = {
        "n": int(len(merged)),
        "n_events": int(merged[label_col].sum()),
        "prevalence": float(np.mean(y)),
        "brier_calibrated": brier_score(y, calibrated),
        "brier_raw": brier_score(y, raw),
        "log_loss_calibrated": log_loss_safe(y, calibrated),
        "log_loss_raw": log_loss_safe(y, raw),
        "roc_auc": roc_auc(y, calibrated),
        "pr_auc": pr_auc(y, calibrated),
        "calibration_intercept_calibrated": intercept_c,
        "calibration_slope_calibrated": slope_c,
        "calibration_intercept_raw": intercept_r,
        "calibration_slope_raw": slope_r,
        "exploratory_equal_width_ece_calibrated": exploratory_equal_width_ece(y, calibrated),
    }
    if frozen_boundaries is not None:
        ece, effective_bins = frozen_quantile_ece(y, calibrated, frozen_boundaries)
        metrics["frozen_quantile_ece_calibrated"] = ece
        metrics["frozen_quantile_ece_effective_bins"] = effective_bins
    return metrics
