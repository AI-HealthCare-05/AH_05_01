"""Integrated real-model candidate; standalone runtime with V2-compatible response."""
from __future__ import annotations

import argparse
import json
import math
from datetime import date, timedelta
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

try:
    from . import d0_inference as d0
except ImportError:
    import d0_inference as d0

VERSION = "tuntun_integrated_candidate_v0_1"
D0_DIGEST = "1c64e17378da8f82f90fcaf4b20bdcb6797aa5e6101b9520e83347a18107f7c3"
COMPONENTS = ("physical", "diabetes", "hypertension", "lifestyle")
LABELS = ("신체", "당뇨", "고혈압", "생활습관")
NOTICE = "튼튼지수는 입력 정보와 통계 모델을 바탕으로 산출한 생활습관 개선 참고용 보정 점수이며, 의료진의 진단이나 치료를 대신하지 않습니다."
OLDER_NOTICE = "65세 이상에서는 모델의 예측 불확실성이 상대적으로 클 수 있어 참고용으로 활용해 주세요."
GROUPS = tuple(f"{age}:{sex}" for age in ("19-39", "40-64", "65+") for sex in (1, 2))


def group_key(age, sex):
    return f"{'19-39' if age < 40 else '40-64' if age < 65 else '65+'}:{int(sex)}"


def validate_reference(reference):
    if set(reference) != set(GROUPS):
        raise d0.InferenceError("exact six reference groups required")
    for group in reference.values():
        values = np.asarray(group["values"], dtype=float)
        counts = group["counts"]
        if (values.ndim != 1 or len(values) == 0 or len(values) != len(counts)
                or not np.isfinite(values).all() or not (np.diff(values) > 0).all()
                or any(type(n) is not int or n <= 0 for n in counts)
                or type(group["n"]) is not int or group["n"] != sum(counts) or group["n"] < 500):
            raise d0.InferenceError("invalid or undersized empirical reference")


def physical_probability(estimated_cm, age, sex, reference):
    if not math.isfinite(estimated_cm):
        raise d0.InferenceError("nonfinite waist prediction")
    group = reference[group_key(age, sex)]
    threshold = (90 if sex == 1 else 85) - estimated_cm
    position = np.searchsorted(group["values"], threshold, side="left")
    return (sum(group["counts"][position:]) + 0.5) / (group["n"] + 1)


def numeric(value, minimum=0, maximum=None):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        return None
    return value if value >= minimum and (maximum is None or value <= maximum) else None


def mean_available(values):
    available = [v for v in values if v is not None]
    return float(sum(available) / len(available)) if available else None


def band(score):
    if score is None:
        return None
    # Match existing V2 display rounding; bands are UI labels, not clinical thresholds.
    return "관심" if round(score) < 40 else "보통" if round(score) < 70 else "양호"


def build_response(features, health_scores, end_date, recorded_days):
    if type(recorded_days) is not int or not 0 <= recorded_days <= 7:
        raise d0.InferenceError("recorded_days must be integer 0..7")
    try:
        end = date.fromisoformat(end_date)
    except (ValueError, TypeError):
        raise d0.InferenceError("ISO end date required") from None
    aerobic = numeric(features.get(d0.FEATURES[4]))
    strength = numeric(features.get(d0.FEATURES[5]), maximum=7)
    aerobic = min(100., aerobic / 150 * 100) if aerobic is not None else None
    strength = min(100., strength / 2 * 100) if strength is not None else None
    lifestyle = mean_available([aerobic, strength])
    scores = [health_scores.get(k) for k in COMPONENTS[:3]] + [lifestyle]
    available = [k for k, v in zip(COMPONENTS, scores) if v is not None]
    components = []
    for key, label, value in zip(COMPONENTS, LABELS, scores):
        components.append({"key": key, "label": label, "score": value, "available": value is not None,
            "bandLabel": band(value), "guidance": "입력·생활습관을 함께 살펴보는 참고 점수입니다." if value is not None else "유효한 입력이 부족하거나 지원 대상이 아니어서 이번 종합점수에서 제외했습니다.",
            "source": ("questionnaire" if key == "lifestyle" else "model_inference") if value is not None else "unavailable"})
    age = numeric(features.get("age_years"), 19, 120)
    response = {"tuntunIndex": mean_available(scores), "physicalScore": scores[0],
        "diabetesScore": scores[1], "hypertensionScore": scores[2], "lifestyleScore": lifestyle,
        "aerobicScore": aerobic, "strengthScore": strength,
        "lifestyleAvailableSubcomponentCount": sum(x is not None for x in (aerobic, strength)),
        "lifestyleScoreSource": "questionnaire" if lifestyle is not None else "unavailable",
        "componentScores": components, "availableComponentCount": len(available), "availableComponents": available,
        "unavailableComponents": [k for k in COMPONENTS if k not in available],
        "isPartialScore": len(available) < 4, "scoreAvailable": bool(available),
        "activityWindowStart": (end - timedelta(days=6)).isoformat(), "activityWindowEnd": end.isoformat(),
        "recordedDays": recorded_days, "missionIntegrationStatus": "pending_evidence",
        "scoreContractVersion": "v0.2-empirical-cdf-owner-approved", "modelVersion": VERSION,
        "calibrationVersion": "d0-platt42_waist-empirical42-v0.1", "notice": NOTICE,
        "olderAdultNotice": OLDER_NOTICE if age is not None and age >= 65 else None, "isMock": False}
    validate_response(response)
    return response


def validate_response(response):
    entries = response["componentScores"]
    if [v["key"] for v in entries] != list(COMPONENTS):
        raise d0.InferenceError("component order mismatch")
    available = []
    for entry, key in zip(entries, COMPONENTS):
        value = entry["score"]
        if value is not None and numeric(value, 0, 100) is None:
            raise d0.InferenceError("invalid score")
        if response[key + "Score"] != value or entry["available"] is not (value is not None):
            raise d0.InferenceError("score/availability inconsistency")
        if value is not None:
            available.append(key)
    lifestyle = mean_available([response["aerobicScore"], response["strengthScore"]])
    total = mean_available([v["score"] for v in entries])
    if (response["lifestyleScore"] != lifestyle or response["tuntunIndex"] != total
            or response["availableComponents"] != available
            or response["unavailableComponents"] != [k for k in COMPONENTS if k not in available]
            or response["availableComponentCount"] != len(available)
            or response["isPartialScore"] is not (len(available) < 4)
            or response["scoreAvailable"] is not bool(available)):
        raise d0.InferenceError("derived response mismatch")


def verify_bundle(root, digest):
    root = Path(root).resolve()
    if d0.sha256_file(root / "manifest.json") != digest:
        raise d0.InferenceError("untrusted integrated manifest")
    files = json.loads((root / "manifest.json").read_text(encoding="utf-8"))["files"]
    required = {"waist_model.joblib", "empirical_reference.json", "metadata.json", "tuntun_inference.py", "d0_inference.py", "d0/manifest.json"}
    if not required <= set(files):
        raise d0.InferenceError("missing required bundle entries")
    for name, expected in files.items():
        path = (root / name).resolve()
        if not path.is_relative_to(root) or not path.is_file() or d0.sha256_file(path) != expected:
            raise d0.InferenceError("integrated file integrity failure")


class TuntunPredictor:
    def __init__(self, root, digest):
        self.root = Path(root)
        verify_bundle(self.root, digest)
        metadata = json.loads((self.root / "metadata.json").read_text(encoding="utf-8"))
        if metadata["environment"] != d0.environment() or metadata["model_version"] != VERSION:
            raise d0.InferenceError("integrated environment/version mismatch")
        if d0.sha256_file(Path(__file__)) != d0.sha256_file(self.root / "tuntun_inference.py"):
            raise d0.InferenceError("integrated runtime differs")
        self.reference = json.loads((self.root / "empirical_reference.json").read_text(encoding="utf-8"))
        validate_reference(self.reference)
        self.disease = d0.D0Predictor(self.root / "d0", D0_DIGEST)
        self.waist = joblib.load(self.root / "waist_model.joblib")

    def score(self, features, *, pregnancy_status, activity_window_end, recorded_days=0):
        if not isinstance(features, dict) or set(features) - set(d0.FEATURES):
            raise d0.InferenceError("unknown canonical feature")
        complete = {key: features.get(key) for key in d0.FEATURES}
        health = {key: None for key in COMPONENTS[:3]}
        result = self.disease.score(complete, pregnancy_status=pregnancy_status)
        if result["supported_input"]:
            frame = d0.prepare_features(pd.DataFrame([complete], columns=d0.FEATURES))
            estimated = float(self.waist.predict(frame)[0])
            probability = physical_probability(estimated, complete["age_years"], complete["sex_code"], self.reference)
            health = {"physical": float(100 * (1 - probability)),
                      "diabetes": result["diabetes_score"], "hypertension": result["hypertension_score"]}
        return build_response(complete, health, activity_window_end, recorded_days)


def smoke_check(root, digest):
    predictor = TuntunPredictor(root, digest)
    cases = json.loads((Path(root) / "smoke_cases.json").read_text(encoding="utf-8"))
    for case in cases:
        actual = predictor.score(**case["request"])
        if actual != case["expected"]:
            raise d0.InferenceError("standalone smoke parity mismatch")
    return {"status": "PASS", "cases": len(cases), "production_release_gate": "BLOCKED"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--manifest-sha256", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(smoke_check(args.bundle, args.manifest_sha256)))
    except Exception:
        print('{"status":"FAIL","error":"integrated_validation_failed"}')
        raise SystemExit(2)
