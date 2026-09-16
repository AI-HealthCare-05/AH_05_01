"""Registry is independent of the explicit composite policy."""
from dataclasses import dataclass
from . import FORMULA_VERSION
from .percentile import finite

@dataclass(frozen=True)
class Component:
    key: str
    label: str
    direction: str = "higher_is_healthier"
    value_type: str = "absolute_reference_score"
    unit: str = "points"

CORE = ("physical", "diabetes", "hypertension", "lifestyle")
REGISTRY = {k: Component(k, label) for k, label in zip(CORE, ("신체 참고지표", "당뇨 참고지표", "고혈압 참고지표", "생활습관"))}
MUSCLE = Component("muscle", "추정 근육량 또래 위치", "higher_is_more_not_healthier", "estimated_alm", "kg")


def risk_to_score(probability):
    if not finite(probability, 0, 1):
        raise ValueError("INVALID_RISK_PROBABILITY")
    return 100*(1-probability)


def default_policy():
    return {"formulaVersion": FORMULA_VERSION, "weights": dict.fromkeys(CORE, 1.),
            "minimumN": 500, "minimumEffectiveN": 300,
            "grouping": "broad", "weighting": "unweighted", "cohort": "component_available",
            "status": "LOCAL_REVIEW_CANDIDATE", "approvedForProduction": False}


def validate_policy(policy, registry):
    weights = policy["weights"]
    if not weights or not set(weights) <= set(registry) or any(not finite(w, 0, 1e6) or w == 0 for w in weights.values()):
        raise ValueError("INVALID_COMPOSITE_POLICY")
    if any(registry[k].direction != "higher_is_healthier" for k in weights):
        raise ValueError("HEALTH_DIRECTION_REQUIRED_FOR_COMPOSITE")
    if policy["formulaVersion"] == FORMULA_VERSION and weights != dict.fromkeys(CORE, 1.):
        raise ValueError("FORMULA_VERSION_CHANGE_REQUIRED")
    if (type(policy["minimumN"]) is not int or policy["minimumN"] < 2
            or not finite(policy["minimumEffectiveN"], 2, 1e9)
            or policy["grouping"] not in ("broad", "five_year")
            or policy["weighting"] not in ("unweighted", "examination_weight")
            or policy["cohort"] not in ("component_available", "common")):
        raise ValueError("INVALID_REFERENCE_POLICY")


def composite(percentiles, policy, registry=None):
    registry = registry or REGISTRY
    validate_policy(policy, registry)
    active = [k for k in registry if k in policy["weights"] and percentiles.get(k) is not None]
    if any(not finite(percentiles[k]) for k in active):
        raise ValueError("INVALID_PERCENTILE")
    denominator = sum(policy["weights"][k] for k in active)
    score = sum(percentiles[k]*policy["weights"][k] for k in active)/denominator if active else None
    return {"peerCompositeScore": score, "availableComponents": active,
            "availableComponentCount": len(active), "isPartialScore": len(active) < len(policy["weights"]),
            "scoreAvailable": bool(active)}
