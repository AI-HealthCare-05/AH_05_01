"""Standalone, server-side D0 runtime. Load only a trusted, hash-pinned package."""
from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import platform
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

FEATURES = ["age_years", "sex_code", "height_cm", "weight_kg",
            "leisure_aerobic_moderate_equivalent_min_week", "strength_days_week"]
TASKS = ("diabetes", "hypertension")
VERSION = "d0_refit_v0_1"
SCHEMA = {
    "version": "d0_canonical_six_v0_1", "feature_order": FEATURES,
    "age_years": {"min": 19, "max": 120, "integer": True, "model_top_code": 80},
    "sex_code": {"allowed": [1, 2], "meaning": "1=male,2=female"},
    "height_cm": {"min": 100, "max": 220},
    "weight_kg": {"min": 25, "max": 250},
    "leisure_aerobic_moderate_equivalent_min_week": {"min": 0, "unit": "minutes/week",
        "meaning": "canonical leisure moderate minutes + 2 * vigorous minutes; not raw sensor duration"},
    "strength_days_week": {"allowed": [0, 1, 2, 3, 4, 5], "meaning": "5 denotes 5+ days, not an exact 5-day count"},
    "missing": "unavailable_no_imputation", "pregnancy_status": "explicit nonpregnant required by score adapter",
    "scope": "backend canonical adapter; no API/DB field mapping is implied",
}


class InferenceError(ValueError):
    pass


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def environment() -> dict:
    return {"python": platform.python_version(), **{name: importlib.metadata.version(name)
        for name in ("numpy", "pandas", "scipy", "scikit-learn", "joblib", "threadpoolctl")}}


def prepare_features(frame: pd.DataFrame) -> pd.DataFrame:
    if not isinstance(frame, pd.DataFrame) or frame.empty or list(frame.columns) != FEATURES:
        raise InferenceError("exact ordered six-feature nonempty frame required")
    result = frame.copy()
    for column in FEATURES:
        values = result[column]
        if values.map(lambda x: isinstance(x, (bool, np.bool_))).any() or not pd.api.types.is_numeric_dtype(values):
            raise InferenceError("features must be numeric, not strings or booleans")
        values = values.to_numpy(dtype=float)
        if not np.isfinite(values).all():
            raise InferenceError("missing or nonfinite feature")
        rules = SCHEMA[column]
        if "allowed" in rules and not np.isin(values, rules["allowed"]).all():
            raise InferenceError("unsupported coded feature")
        if "min" in rules and (values < rules["min"]).any():
            raise InferenceError("feature below supported minimum")
        if "max" in rules and (values > rules["max"]).any():
            raise InferenceError("feature above supported maximum")
        if rules.get("integer") and (values != np.floor(values)).any():
            raise InferenceError("integer feature required")
        result[column] = np.minimum(values, 80) if column == "age_years" else values
    return result


def calibrated_probability(model, platt, frame: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
    raw = model.predict_proba(frame)[:, 1]
    clipped = np.clip(raw, 1e-6, 1 - 1e-6)
    calibrated = platt.predict_proba(np.log(clipped / (1 - clipped)).reshape(-1, 1))[:, 1]
    if any(not np.isfinite(x).all() or ((x < 0) | (x > 1)).any() for x in (raw, calibrated)):
        raise InferenceError("invalid model probability")
    return raw, calibrated


def verify_package(root: Path, expected_manifest_sha256: str) -> dict:
    root = root.resolve()
    if sha256_file(root / "manifest.json") != expected_manifest_sha256:
        raise InferenceError("manifest does not match trusted digest")
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    files = manifest["files"]
    required = {"metadata.json", "input_schema.json", "d0_inference.py", "smoke_cases.json"}
    required |= {f"{task}_{kind}.joblib" for task in TASKS for kind in ("model", "platt")}
    if not required <= set(files):
        raise InferenceError("required package file absent from manifest")
    for name, digest in files.items():
        path = (root / name).resolve()
        if not path.is_relative_to(root) or not path.is_file() or sha256_file(path) != digest:
            raise InferenceError("package file integrity failure")
    return manifest


class D0Predictor:
    def __init__(self, root: Path, expected_manifest_sha256: str):
        self.root = Path(root)
        verify_package(self.root, expected_manifest_sha256)
        self.metadata = json.loads((self.root / "metadata.json").read_text(encoding="utf-8"))
        if self.metadata["model_version"] != VERSION or self.metadata["environment"] != environment():
            raise InferenceError("model version or runtime environment mismatch")
        if json.loads((self.root / "input_schema.json").read_text(encoding="utf-8")) != SCHEMA:
            raise InferenceError("input schema mismatch")
        if sha256_file(Path(__file__)) != sha256_file(self.root / "d0_inference.py"):
            raise InferenceError("runtime code differs from packaged runtime")
        self.models = {task: (joblib.load(self.root / f"{task}_model.joblib"),
                              joblib.load(self.root / f"{task}_platt.joblib")) for task in TASKS}

    def predict_internal(self, frame: pd.DataFrame) -> dict:
        """Internal numeric parity interface; never return probabilities to the app."""
        prepared = prepare_features(frame)
        return {task: calibrated_probability(*self.models[task], prepared) for task in TASKS}

    def score(self, features: dict, *, pregnancy_status: str) -> dict:
        """Backend adapter. Only scores, availability and version metadata leave here."""
        result = {"model_version": VERSION, "preprocessor_version": SCHEMA["version"],
                  "calibration_version": "d0_platt_crossfit42_v0_1", "supported_input": False,
                  "unsupported_reason_code": None,
                  **{f"{t}_score": None for t in TASKS}, **{f"{t}_available": False for t in TASKS}}
        if pregnancy_status != "nonpregnant":
            result["unsupported_reason_code"] = "PREGNANCY_UNSUPPORTED_OR_UNKNOWN"
            return result
        if not isinstance(features, dict) or set(features) != set(FEATURES):
            result["unsupported_reason_code"] = "EXACT_SIX_FEATURES_REQUIRED"
            return result
        try:
            frame = pd.DataFrame([[features[k] for k in FEATURES]], columns=FEATURES)
            predictions = self.predict_internal(frame)
        except InferenceError:
            result["unsupported_reason_code"] = "INVALID_OR_MISSING_CANONICAL_INPUT"
            return result
        result["supported_input"] = True
        result["age_top_coded_80_plus"] = features["age_years"] >= 80
        for task, (_, probability) in predictions.items():
            result[f"{task}_score"] = float(100 * (1 - probability[0]))
            result[f"{task}_available"] = True
        return result


def smoke_check(root: Path, digest: str) -> dict:
    predictor = D0Predictor(root, digest)
    cases = json.loads((root / "smoke_cases.json").read_text(encoding="utf-8"))
    for case in cases:
        actual = predictor.score(case["features"], pregnancy_status=case["pregnancy_status"])
        for key, expected in case["expected"].items():
            if key.endswith("_score") and expected is not None:
                if not np.isclose(actual[key], expected, rtol=0, atol=1e-10):
                    raise InferenceError("fresh-process score parity failure")
            elif actual[key] != expected:
                raise InferenceError("fresh-process metadata parity failure")
    return {"status": "PASS", "synthetic_cases": len(cases), "production_release_gate": "BLOCKED"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--manifest-sha256", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(smoke_check(args.bundle, args.manifest_sha256)))
    except Exception:
        print(json.dumps({"status": "FAIL", "error": "package_or_inference_validation_failed"}))
        raise SystemExit(2)


if __name__ == "__main__":
    main()
