"""Public vNext DTO serializer and isolated legacy path."""
import json
from . import SCHEMA_VERSION, DISPLAY_VERSION
from .inputs import normalize
from .percentile import percentile, display, finite
from .reference import group_key, validate_reference
from .registry import CORE, REGISTRY, composite, validate_policy, default_policy


def serialize(prediction, features, reference, policy, binding, *, client_schema=SCHEMA_VERSION):
    if client_schema != SCHEMA_VERSION:
        raise ValueError("UNSUPPORTED_CLIENT_SCHEMA")
    validate_policy(policy, REGISTRY)
    validate_reference(reference, policy, binding)
    if prediction.model_version != binding["modelVersion"]:
        raise ValueError("ADAPTER_REFERENCE_VERSION_MISMATCH")
    key = group_key(features.get("age_years"), features.get("sex_code"), policy["grouping"])
    components, percentiles = [], {}
    for k in CORE:
        score = prediction.absolute_scores.get(k)
        if score is not None and not finite(score):
            raise ValueError("INVALID_ADAPTER_OUTPUT")
        group = reference["components"][k].get(key)
        reason = prediction.unavailable_reason or "MISSING_REQUIRED_INPUT" if score is None else None
        if score is not None and key is None:
            reason = "PEER_DEMOGRAPHICS_UNAVAILABLE"
        elif score is not None and (group is None or group["n"] < policy["minimumN"] or group["effectiveN"] < policy["minimumEffectiveN"]):
            reason = "INSUFFICIENT_REFERENCE"
        elif k == "lifestyle" and score is not None and prediction.lifestyle_subcomponent_count != 2:
            reason = "LIFESTYLE_COMPLETENESS_REFERENCE_MISMATCH"
        result = percentile(score, group, policy["minimumN"], policy["minimumEffectiveN"]) if reason is None else None
        p = result["peerPercentile"] if result else None
        percentiles[k] = p
        components.append({"componentKey": k, "label": REGISTRY[k].label,
            "direction": "higher_is_healthier", "includedInComposite": True,
            "valueType": "health_direction_peer_percentile", "absoluteReferenceScore": score,
            "peerPercentile": p, "topPercentApprox": result["topPercentApprox"] if result else None,
            "percentileRange": result["percentileRange"] if result else None,
            "tieMassPercent": result["tieMassPercent"] if result else None,
            "referenceGroup": key, "referenceN": group["n"] if group else None,
            "referenceEffectiveN": group["effectiveN"] if group else None,
            "available": p is not None, "unavailableReason": reason,
            "modelVersion": prediction.model_version, "referenceVersion": reference["referenceVersion"],
            "formulaVersion": policy["formulaVersion"], "displayPolicyVersion": DISPLAY_VERSION,
            "source": "unavailable" if score is None else "questionnaire" if k == "lifestyle" and prediction.source != "synthetic" else prediction.source,
            "display": display(p) if p is not None else None})
    output = {"schemaVersion": SCHEMA_VERSION, "formulaVersion": policy["formulaVersion"],
        "displayPolicyVersion": DISPLAY_VERSION, "modelVersion": prediction.model_version,
        "referenceVersion": reference["referenceVersion"], "referenceYears": reference["provenance"]["years"],
        "referencePopulation": reference["provenance"]["populationLabel"],
        "components": components, **composite(percentiles, policy),
        "ageTopCoded": bool(features.get("age_years") is not None and features["age_years"] >= 80),
        "lifestyleAvailableSubcomponentCount": prediction.lifestyle_subcomponent_count,
        "comparisonKey": "|".join([policy["formulaVersion"], prediction.model_version, reference["referenceVersion"],
                                   key or "none", ",".join(k for k in CORE if percentiles[k] is not None)]),
        "isSynthetic": prediction.source == "synthetic", "releaseStatus": "LOCAL_REVIEW_CANDIDATE",
        "notice": "또래 순위는 건강확률이나 진단이 아닙니다. 부분점수와 서로 다른 비교 기준의 점수는 직접 비교하지 마세요."}
    validate_dto(output)
    return output


def validate_dto(payload):
    from .schema import RESPONSE_SCHEMA, validate_json
    validate_json(payload, RESPONSE_SCHEMA)
    actual = {c["componentKey"]: c["peerPercentile"] for c in payload["components"]}
    expected = composite(actual, {**default_policy(), "formulaVersion": payload["formulaVersion"]})
    if any(payload[k] != v for k, v in expected.items()):
        raise ValueError("DTO_COMPOSITE_INCONSISTENCY")
    keys = [c["componentKey"] for c in payload["components"]]
    if keys not in (list(CORE), [*CORE, "muscle"]):
        raise ValueError("DTO_COMPONENTS_MISMATCH")
    for c in payload["components"]:
        if c["includedInComposite"] != (c["componentKey"] in CORE):
            raise ValueError("DTO_UNAPPROVED_COMPOSITE_COMPONENT")
        if c["componentKey"] == "muscle" and (c["direction"] != "higher_is_more_not_healthier" or not payload["isSynthetic"]):
            raise ValueError("MUSCLE_ONLY_SYNTHETIC_DISPLAY_SUPPORTED")
        if c["componentKey"] in CORE and (c["direction"] != "higher_is_healthier" or c["valueType"] != "health_direction_peer_percentile"
                or c["modelVersion"] != payload["modelVersion"] or c["referenceVersion"] != payload["referenceVersion"]
                or c["formulaVersion"] != payload["formulaVersion"] or c["displayPolicyVersion"] != payload["displayPolicyVersion"]):
            raise ValueError("DTO_VERSION_OR_DIRECTION_MISMATCH")
        p = c["peerPercentile"]
        if c["available"] != (p is not None) or (c["unavailableReason"] is None) != c["available"]:
            raise ValueError("DTO_AVAILABILITY_INCONSISTENCY")
        if p is not None and (c["topPercentApprox"] != 100-p or c["display"] != display(p)
                              or c["referenceN"] is None or c["referenceEffectiveN"] is None
                              or c["percentileRange"] is None
                              or not c["percentileRange"][0] <= p <= c["percentileRange"][1]
                              or c["tieMassPercent"] != c["percentileRange"][1]-c["percentileRange"][0]):
            raise ValueError("DTO_PERCENTILE_INCONSISTENCY")
        if p is None and any(c[k] is not None for k in ("topPercentApprox", "percentileRange", "tieMassPercent", "display")):
            raise ValueError("DTO_UNAVAILABLE_VALUES")
    return json.loads(json.dumps(payload, allow_nan=False))


class PeerService:
    def __init__(self, adapter, reference, policy, binding):
        self.adapter, self.reference, self.policy, self.binding = adapter, reference, policy, binding

    def score(self, request, client_schema=SCHEMA_VERSION):
        if client_schema != SCHEMA_VERSION:
            raise ValueError("UNSUPPORTED_CLIENT_SCHEMA")
        normalized, reason = normalize(request)
        prediction = self.adapter.predict(normalized, reason)
        return serialize(prediction, normalized["features"], self.reference, self.policy, self.binding, client_schema=client_schema)

    def batch(self, requests, client_schema=SCHEMA_VERSION):
        return [self.score(r, client_schema) for r in requests]


def legacy_score(predictor, request):
    """Exact existing runtime response. No vNext fields or input remapping."""
    return predictor.score(**request)
