"""Fully synthetic fixtures; no clinical or production muscle model."""
from copy import deepcopy
from .adapters import Prediction
from .reference import build_reference
from .registry import CORE, default_policy
from .service import serialize, validate_dto
from .percentile import distribution, percentile, display


def synthetic_reference(n=600):
    policy = default_policy()
    binding = {"modelVersion": "synthetic-v1", "legacyManifestSha256": "synthetic-only"}
    rows = [{"age_years": age, "sex_code": sex,
             **{k: (100 if k == "lifestyle" else i%101) for k in CORE}} for age in (30,50,80) for sex in (1,2) for i in range(n)]
    ref = build_reference(rows, policy, binding, "synthetic-reference-v1",
                          {"predictionSource": "synthetic", "years": [2026], "populationLabel": "합성 표본·실제 환자 아님"})
    return ref, policy, binding


def add_synthetic_muscle(payload):
    result = deepcopy(payload)
    cell = distribution([15.,20.,25.]*200)
    rank = percentile(20., cell)
    muscle = {**result["components"][0], "componentKey": "muscle", "label": "합성 추정 근육량 또래 위치",
        "direction": "higher_is_more_not_healthier", "includedInComposite": False,
        "valueType": "quantity_peer_percentile", "absoluteReferenceScore": None,
        **rank, "referenceN": 600, "referenceEffectiveN": 600., "available": True, "unavailableReason": None,
        "referenceVersion": "synthetic-predicted-ALM-not-measured-v1", "source": "synthetic", "modelVersion": "synthetic-M1",
        "display": display(rank["peerPercentile"])}
    result["components"].append(muscle)
    result["isSynthetic"] = True
    validate_dto(result)
    return result


def examples():
    ref, policy, binding = synthetic_reference()
    normal = serialize(Prediction(dict(zip(CORE,[80.,60.,40.,100.])), "synthetic-v1", source="synthetic"),
                       {"age_years":45,"sex_code":1},ref,policy,binding)
    result = {"normal_four": normal, "muscle_display_only": add_synthetic_muscle(normal)}
    result["lifestyle_only"] = serialize(Prediction({"lifestyle":100.}, "synthetic-v1", source="synthetic"),
                                         {"age_years":45,"sex_code":1},ref,policy,binding)
    result["many_ties"] = deepcopy(normal)  # lifestyle is all tied at 100 -> percentile 50.
    tiny, _, _ = synthetic_reference(10)
    result["small_group"] = serialize(Prediction(dict.fromkeys(CORE,80.), "synthetic-v1", source="synthetic"),
                                      {"age_years":45,"sex_code":1},tiny,policy,binding)
    result["age_90"] = serialize(Prediction(dict.fromkeys(CORE,80.), "synthetic-v1", source="synthetic"),
                                 {"age_years":90,"sex_code":1},ref,policy,binding)
    try:
        serialize(Prediction(dict.fromkeys(CORE,80.), "synthetic-v2", source="synthetic"),
                  {"age_years":45,"sex_code":1},ref,policy,binding)
    except ValueError as exc:
        result["new_model_old_reference_failure"] = {"expectedFailure": str(exc)}
    return result
