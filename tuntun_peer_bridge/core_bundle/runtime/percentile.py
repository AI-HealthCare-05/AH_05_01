"""Weighted empirical midrank, preserving exact ties and internal precision."""
from bisect import bisect_left, bisect_right
from decimal import Decimal, ROUND_HALF_UP
import math


def finite(value, low=0, high=100):
    return (not isinstance(value, bool) and isinstance(value, (int, float))
            and math.isfinite(value) and low <= value <= high)


def distribution(values, weights=None):
    values = list(values)
    weights = [1.] * len(values) if weights is None else list(weights)
    if len(values) != len(weights) or not values:
        raise ValueError("EMPTY_OR_UNALIGNED_REFERENCE")
    counts = {}
    for value, weight in zip(values, weights):
        if not finite(value) or not finite(weight, 0, math.inf) or weight <= 0:
            raise ValueError("INVALID_REFERENCE_VALUE_OR_WEIGHT")
        counts[value] = counts.get(value, 0.) + weight
    total = math.fsum(weights)
    return {"values": sorted(counts), "weights": [counts[v] for v in sorted(counts)],
            "n": len(values), "effectiveN": total ** 2 / math.fsum(w*w for w in weights)}


def validate_distribution(group):
    values, weights = group["values"], group["weights"]
    if (not values or len(values) != len(weights) or values != sorted(set(values))
            or any(not finite(v) for v in values)
            or any(not finite(w, 0, math.inf) or w <= 0 for w in weights)
            or type(group["n"]) is not int or group["n"] < len(values)
            or not finite(group["effectiveN"], 1-1e-8, group["n"]+1e-8)):
        raise ValueError("INVALID_DISTRIBUTION")


def percentile(value, group, minimum_n=500, minimum_effective_n=300):
    if not finite(value):
        raise ValueError("INVALID_ABSOLUTE_SCORE")
    validate_distribution(group)
    if group["n"] < minimum_n or group["effectiveN"] < minimum_effective_n:
        return None
    values, weights = group["values"], group["weights"]
    left, right = bisect_left(values, value), bisect_right(values, value)
    total = math.fsum(weights)
    strict = 100 * math.fsum(weights[:left]) / total
    inclusive = 100 * math.fsum(weights[:right]) / total
    p = (strict + inclusive) / 2
    return {"peerPercentile": p, "topPercentApprox": 100-p,
            "percentileRange": [strict, inclusive], "tieMassPercent": inclusive-strict}


def rounded(value, digits=1):
    return float(Decimal(str(value)).quantize(Decimal(1).scaleb(-digits), rounding=ROUND_HALF_UP))


def display(p):
    # Derive both rounded numbers from the same rounded percentile.
    rp = rounded(p)
    top = rounded(100-rp)
    text = "상위 약 1% 이하" if top <= 1 else "상위 약 99% 이상" if top >= 99 else f"상위 약 {top:.1f}%"
    return {"percentile": rp, "topPercentApprox": top, "text": text}

