"""Personal exact SHAP against the existing, hash-pinned release. No training.

Run in the supplied Python 3.14 model environment, separately from FastAPI 3.13.
An output explains the internal calibrated score, never a percentile or outcome.
"""

import hashlib
import json
import sys
import time
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

if __package__:
    from .activity_reference import build_comparison
else:
    from activity_reference import build_comparison

RELEASE_PIN = "bdca54917705cde75fc2d1b275c49a91ebfef05f56f45472d29e4b97ba77c2e9"
WRAPPER_PIN = "fe6cb73572d5927c62f54659e64261ee6ca382e9a58a7c0a2da1b80173b76c58"
BACKGROUND_PIN = "62407693c4570a4e0bd15a6a8122cdcad59c4a180a127996b7520bcc97df0fb9"
TARGET = "calibrated_reference_score_before_peer_percentile"
FEATURES = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]
LABELS = ["나이", "성별", "키", "몸무게", "유산소 활동 시간", "근력운동 일수"]


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class PersonalShapEngine:
    def __init__(self, payload):
        self.payload = Path(payload).resolve()
        if (
            digest(self.payload / "waist_cm.py") != WRAPPER_PIN
            or digest(self.payload / "02_background/background_300.csv") != BACKGROUND_PIN
        ):
            raise ValueError("EXPLANATION_ASSET_MISMATCH")
        sys.dont_write_bytecode = True
        sys.path.insert(0, str(self.payload))
        import numpy as np
        import pandas as pd
        import shap
        from waist_cm import WaistCM

        if shap.__version__ != "0.49.1":
            raise ValueError("UNSUPPORTED_SHAP_VERSION")
        self.np, self.pd, self.shap = np, pd, shap
        self.wrapper = WaistCM(self.payload / "01_inference")  # Verifies all nested assets before joblib.
        from src.tuntun_app_vnext.inputs import normalize

        self.normalize = normalize
        self.background = self.wrapper.prepare(pd.read_csv(self.payload / "02_background/background_300.csv"))
        if len(self.background) != 300 or list(self.background.columns) != FEATURES:
            raise ValueError("BACKGROUND_CONTRACT_MISMATCH")
        masker = shap.maskers.Independent(self.background, max_samples=300)
        self.explainer = shap.ExactExplainer(self.predict, masker, feature_names=FEATURES)
        self.baseline = self.predict(self.background).mean(axis=0)

    def predict(self, array):
        frame = self.pd.DataFrame(array, columns=FEATURES)
        result = self.wrapper.predictor.disease.predict_internal(frame)
        return self.np.column_stack([100 * (1 - result[domain][1]) for domain in ("diabetes", "hypertension")])

    def compute(self, request):
        started = time.perf_counter()
        normalized = self.normalize(request)
        # Score, ranks and SHAP share this exact normalization and release instance.
        scored = self.wrapper.app.score(request)
        components = {item["componentKey"]: item for item in scored["components"]}
        common = {
            "schema_version": "tmtn-personal-xai-v1",
            "snapshot_id": str(uuid4()),
            "input_revision": request["inputRevision"],
            "computed_at": datetime.now(UTC).isoformat(),
            "release_sha256": RELEASE_PIN,
            "model_version": scored["modelVersion"],
            "reference_version": scored["referenceVersion"],
            "formula_version": scored["formulaVersion"],
            "input_mapping_version": scored["inputMappingVersion"],
            "display_policy_version": scored["displayPolicyVersion"],
            "reference_date": request["referenceDate"],
            "age_notice": scored["ageContext"]["notice"],
            "release_stage": "candidate",
            "is_mock": False,
            "composite_score": scored["compositeDisplay"]["score"],
            "domains": [],
        }
        if not all(components[domain]["available"] for domain in ("diabetes", "hypertension")):
            return {
                **common,
                "status": "unavailable",
                "reason": next(
                    (
                        components[d]["unavailableReason"]
                        for d in ("diabetes", "hypertension")
                        if not components[d]["available"]
                    ),
                    "INPUT_UNAVAILABLE",
                ),
            }
        frame = self.wrapper.prepare(self.pd.DataFrame([normalized["canonical"]["features"]], columns=FEATURES))
        explanation = self.explainer(frame, max_evals=64, batch_size=19200, silent=True)
        values = self.np.asarray(explanation.values, dtype=float)
        base = self.np.asarray(explanation.base_values, dtype=float)
        output = self.predict(frame)
        if values.shape != (1, 6, 2) or not all(self.np.isfinite(v).all() for v in (values, base, output)):
            raise ValueError("INVALID_EXPLANATION")
        self.np.testing.assert_allclose(base + values.sum(axis=1), output, rtol=0, atol=1e-9)
        self.np.testing.assert_allclose(base[0], self.baseline, rtol=0, atol=1e-9)
        for j, domain in enumerate(("diabetes", "hypertension")):
            component = components[domain]
            self.np.testing.assert_allclose(output[0, j], component["absoluteReferenceScore"], rtol=0, atol=1e-9)
            common["domains"].append(
                {
                    "domain": domain,
                    "rank": component["rankDisplay"]["rankApprox"],
                    "rank_range": component["rankDisplay"]["rankRange"],
                    "reference_group": component["referenceGroup"],
                    "reference_n": component["referenceN"],
                    "output_target": TARGET,
                    "unit": "internal_reference_score_point",
                    "base_value": float(base[0, j]),
                    "output_value": float(output[0, j]),
                    "feature_order": FEATURES,
                    "contributions": [
                        {"key": key, "label": LABELS[i], "value": float(values[0, i, j])}
                        for i, key in enumerate(FEATURES)
                    ],
                    "background_sha256": BACKGROUND_PIN,
                    "background_rows": 300,
                    "explainer": "ExactExplainer",
                    "masker": "Independent",
                    "link": "identity",
                    "shap_version": self.shap.__version__,
                    "additivity_error": float(abs(base[0, j] + values[0, :, j].sum() - output[0, j])),
                }
            )
        # The comparison is descriptive survey evidence, never a SHAP attribution.
        # A missing/corrupted reference must not take the verified health result down.
        try:
            common["activity_comparison"] = build_comparison(
                normalized["canonical"]["features"],
                input_revision=request["inputRevision"],
                reference_date=request["referenceDate"],
                expected_group=common["domains"][0]["reference_group"],
            )
        except (OSError, ValueError, KeyError, TypeError):
            common["activity_comparison"] = None
        return {**common, "status": "ready", "reason": None, "elapsed_seconds": round(time.perf_counter() - started, 3)}


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload", type=Path, required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = PersonalShapEngine(args.payload).compute(json.loads(args.input.read_text(encoding="utf-8-sig")))
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "status": result["status"],
                "domains": len(result["domains"]),
                "elapsed_seconds": result.get("elapsed_seconds"),
            }
        )
    )
