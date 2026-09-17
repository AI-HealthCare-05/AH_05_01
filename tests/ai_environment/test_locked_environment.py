"""Dependency smoke tests only: synthetic inputs, no D0 artifacts or health data."""

import sys
import tomllib
from importlib.metadata import version
from pathlib import Path

import numpy as np
import pandas as pd
import pytest
import yaml
from sklearn.base import clone
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

ROOT = Path(__file__).resolve().parents[2]
# Only environment tests reject warnings; legacy D0 checks remain separate.
pytestmark = pytest.mark.filterwarnings("error")


def test_python_matches_team_pin() -> None:
    assert ".".join(map(str, sys.version_info[:3])) == (ROOT / ".python-version").read_text().strip()


@pytest.mark.parametrize("package", ["numpy", "pandas", "scipy", "scikit-learn", "pyyaml", "pytest"])
def test_installed_package_matches_lock(package: str) -> None:
    with (ROOT / "uv.lock").open("rb") as stream:
        locked = tomllib.load(stream)
    versions = {entry["version"] for entry in locked["package"] if entry["name"] == package}
    assert versions == {version(package)}


def test_yaml_and_dataframe_nullable_types() -> None:
    config = yaml.safe_load("features: [x1, x2]\neligible: true\n")
    frame = pd.DataFrame({"x1": [1.0, 2.0, None], "x2": [2.0, 4.0, 6.0]})
    assert config["eligible"] is True
    assert frame[config["features"]].isna().sum().to_dict() == {"x1": 1, "x2": 0}
    assert frame.iloc[:2].to_numpy(dtype=float).shape == (2, 2)


def test_scaler_clone_keeps_training_state_separate() -> None:
    train = np.array([[-2.0, 1.0], [-1.0, 0.0], [1.0, 0.0], [2.0, 1.0]])
    # Finite-C L2 is an API smoke check, not the unpenalized D0/Platt model.
    model = make_pipeline(StandardScaler(), LogisticRegression(C=1.0, l1_ratio=0.0, max_iter=1000))
    model.fit(train, np.array([0, 1, 0, 1]))
    scaler = model.named_steps["standardscaler"]
    expected = scaler.mean_.copy()
    model.predict_proba(np.array([[100.0, 100.0]]))
    np.testing.assert_array_equal(scaler.mean_, expected)
    assert not hasattr(clone(model).named_steps["standardscaler"], "mean_")


@pytest.mark.parametrize("kind", ["logistic", "forest"])
def test_synthetic_probability_metrics_and_reproducibility(kind: str) -> None:
    rng = np.random.default_rng(42)
    features = rng.normal(size=(60, 3))
    target = (features[:, 0] + rng.normal(size=60) > 0).astype(int)
    estimator = (
        make_pipeline(StandardScaler(), LogisticRegression(C=1.0, l1_ratio=0.0, max_iter=1000))
        if kind == "logistic"
        else RandomForestClassifier(n_estimators=8, max_depth=3, random_state=42, n_jobs=1)
    )
    first = clone(estimator).fit(features[:40], target[:40]).predict_proba(features[40:])[:, 1]
    second = clone(estimator).fit(features[:40], target[:40]).predict_proba(features[40:])[:, 1]
    np.testing.assert_array_equal(first, second)
    assert np.isfinite(first).all() and ((0 <= first) & (first <= 1)).all()
    for metric in (brier_score_loss, roc_auc_score, average_precision_score):
        assert 0 <= metric(target[40:], first) <= 1
