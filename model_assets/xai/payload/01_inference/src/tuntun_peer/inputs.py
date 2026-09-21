"""Canonical six features; dates must be backend KST calendar dates."""
from datetime import date
from .. import d0_inference as d0
from .percentile import finite


def age_at(birth_date, as_of_date):
    birth, today = date.fromisoformat(birth_date), date.fromisoformat(as_of_date)
    if birth > today:
        raise ValueError("FUTURE_BIRTH_DATE")
    return today.year-birth.year-((today.month, today.day) < (birth.month, birth.day))


def normalize(request):
    allowed = {"features", "pregnancy_status", "activity_window_end", "recorded_days", "birth_date", "birth_month"}
    if not isinstance(request, dict) or set(request)-allowed or "activity_window_end" not in request:
        raise ValueError("INVALID_REQUEST_SCHEMA")
    date.fromisoformat(request["activity_window_end"])
    if "birth_month" in request:
        raise ValueError("EXACT_BIRTH_DATE_OR_CONFIRMED_AGE_REQUIRED")
    features = request.get("features", {})
    if not isinstance(features, dict) or set(features)-set(d0.FEATURES):
        raise ValueError("UNKNOWN_FEATURE")
    features = {k: features.get(k) for k in d0.FEATURES}
    if "birth_date" in request:
        age = age_at(request["birth_date"], request["activity_window_end"])
        if features["age_years"] is not None and features["age_years"] != age:
            raise ValueError("AGE_CONFLICT")
        features["age_years"] = age
    invalid, missing = [], []
    for key, value in features.items():
        rule = d0.SCHEMA[key]
        if value is None:
            missing.append(key)
        elif (not finite(value, rule.get("min", 0), rule.get("max", float("inf")))
              or ("allowed" in rule and value not in rule["allowed"])
              or (rule.get("integer") and value != int(value))):
            invalid.append(key)
            features[key] = None
    pregnancy = request.get("pregnancy_status", "unknown")
    if pregnancy not in ("pregnant", "nonpregnant", "unknown", "not_applicable"):
        raise ValueError("INVALID_PREGNANCY_STATUS")
    sex = features["sex_code"]
    if (pregnancy == "not_applicable" and sex != 1) or (pregnancy == "pregnant" and sex == 1):
        raise ValueError("CONTRADICTORY_PREGNANCY_STATUS")
    mapped = "nonpregnant" if pregnancy == "not_applicable" else pregnancy
    reason = ("PREGNANCY_UNSUPPORTED" if mapped == "pregnant" else
              "PREGNANCY_STATUS_UNKNOWN" if mapped == "unknown" else
              "INVALID_REQUIRED_INPUT" if invalid else "MISSING_REQUIRED_INPUT" if missing else None)
    return {"features": features, "pregnancy_status": mapped,
            "activity_window_end": request["activity_window_end"], "recorded_days": request.get("recorded_days", 0)}, reason

