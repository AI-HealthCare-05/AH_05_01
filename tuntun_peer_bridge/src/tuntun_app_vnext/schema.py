"""Closed response schema for the new app route; legacy schema is untouched."""
from copy import deepcopy
from . import SCHEMA_VERSION, MAPPING_VERSION, DISPLAY_VERSION
from ..tuntun_peer.schema import RESPONSE_SCHEMA as PEER_SCHEMA, validate_json, obj, TEXT, BOOL, NULL_NUMBER, NULL_TEXT

REQUEST_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", "additionalProperties": False,
    "required": ["birthYear", "birthMonth", "sex", "referenceDate"],
    "properties": {
        "birthYear": {"type": "integer", "minimum": 1, "maximum": 9999},
        "birthMonth": {"type": "integer", "minimum": 1, "maximum": 12},
        "referenceDate": {"type": "string", "pattern": "^\\d{4}-\\d{2}-\\d{2}$"},
        "sex": {"enum": ["male", "female"]},
        "pregnancyStatus": {"enum": ["pregnant", "nonpregnant", "unknown", "not_applicable"]},
        "heightCm": {"type": ["number", "null"], "minimum": 100, "maximum": 220},
        "weightKg": {"type": ["number", "null"], "minimum": 25, "maximum": 250},
        "strengthWeeklyCount": {"type": ["integer", "null"], "minimum": 0, "maximum": 7},
        "strengthFrequencyUnit": {"enum": [None, "days", "sessions"]},
        "strengthIntensity": {"enum": [None, "light", "moderate", "hard"]},
        **{k: {"type": ["number", "null"], "minimum": 0, "maximum": 10080} for k in ("aerobicLowMinutes", "aerobicModerateMinutes", "aerobicVigorousMinutes")},
        "bedtime": {"type": ["string", "null"], "pattern": "^(?:[01]\\d|2[0-3]):[0-5]\\d$"},
        "wakeTime": {"type": ["string", "null"], "pattern": "^(?:[01]\\d|2[0-3]):[0-5]\\d$"},
        "inputRevision": {"type": ["string", "null"], "minLength": 1, "maxLength": 100},
    },
}

RANK = obj({"rankApprox": {"type": ["integer", "null"], "minimum": 1, "maximum": 100},
            "rankRange": {"type": ["array", "null"], "minItems": 2, "maxItems": 2, "items": {"type": "integer", "minimum": 1, "maximum": 100}},
            "text": TEXT, "tieNotice": NULL_TEXT})
RESPONSE_SCHEMA = deepcopy(PEER_SCHEMA)
RESPONSE_SCHEMA["properties"]["schemaVersion"] = {"const": SCHEMA_VERSION}
RESPONSE_SCHEMA["properties"]["displayPolicyVersion"] = {"const": DISPLAY_VERSION}
component = RESPONSE_SCHEMA["properties"]["components"]["items"]
component["properties"]["displayPolicyVersion"] = {"const": DISPLAY_VERSION}
component["properties"]["rankDisplay"] = RANK
component["required"].append("rankDisplay")
RESPONSE_SCHEMA["properties"]["components"]["maxItems"] = 4
component["properties"]["componentKey"] = {"enum": ["physical", "diabetes", "hypertension", "lifestyle"]}
RESPONSE_SCHEMA["properties"]["isSynthetic"] = {"const": False, "type": "boolean"}
extra = {
    "inputMappingVersion": {"const": MAPPING_VERSION}, "isMock": {"const": False}, "inputRevision": NULL_TEXT,
    "ageContext": obj({"ageYearsUsed": {"type": "integer"}, "possibleAgeYears": {"type": "array", "minItems": 2, "maxItems": 2, "items": {"type": "integer"}},
                       "isApproximate": BOOL, "policy": {"const": "completed_birth_month_lower_age_v1"}, "referenceDate": TEXT,
                       "timezone": {"const": "Asia/Seoul"}, "ageTopCoded": BOOL, "healthAgeSupported": BOOL, "notice": TEXT}),
    "habitContext": obj({"lowIntensityMinutes": {"type": ["number", "null"], "minimum": 0},
        "totalReportedActivityMinutes": {"type": ["number", "null"], "minimum": 0},
        "moderateEquivalentMinutes": {"type": ["number", "null"], "minimum": 0}, "strengthIntensity": {"enum": [None, "light", "moderate", "hard"]},
        "strengthDaysTopCoded": BOOL, "bedToWakeIntervalMinutes": {"type": ["integer", "null"], "minimum": 1, "maximum": 1439},
        "bedToWakeStatus": {"enum": ["available", "ambiguous_same_time", "incomplete_optional_input"]},
        "lowIntensityNotice": TEXT, "strengthIntensityNotice": TEXT, "sleepNotice": TEXT}),
    "compositeDisplay": obj({"score": NULL_NUMBER, "unit": {"const": "점"}, "bandLabel": {"type": "null"}, "text": TEXT}),
    "inputUsage": obj({k:TEXT for k in ("birthYearMonth", "sex", "pregnancyStatus", "heightCm", "weightKg", "strengthWeeklyCount", "strengthIntensity", "aerobicLowMinutes", "aerobicModerateMinutes", "aerobicVigorousMinutes", "bedtimeWakeTime")}),
    "referenceCaution": TEXT,
}
RESPONSE_SCHEMA["properties"].update(extra)
RESPONSE_SCHEMA["required"].extend(extra)


def validate_response(output):
    validate_json(output, RESPONSE_SCHEMA)
    from .service import rank_display
    from ..tuntun_peer.percentile import rounded
    keys = [c["componentKey"] for c in output["components"]]
    if keys != ["physical", "diabetes", "hypertension", "lifestyle"]:
        raise ValueError("COMPONENT_ORDER_OR_DUPLICATE")
    available = [c["componentKey"] for c in output["components"] if c["available"]]
    if (output["availableComponents"] != available or output["availableComponentCount"] != len(available)
            or output["scoreAvailable"] != bool(available) or output["isPartialScore"] != (len(available) < 4)):
        raise ValueError("AVAILABILITY_MISMATCH")
    for c in output["components"]:
        if c["rankDisplay"] != rank_display(c):
            raise ValueError("RANK_DISPLAY_MISMATCH")
    values = [c["peerPercentile"] for c in output["components"] if c["available"] and c["includedInComposite"]]
    mean = sum(values)/len(values) if values else None
    if output["peerCompositeScore"] != mean or output["compositeDisplay"]["score"] != (rounded(mean) if mean is not None else None):
        raise ValueError("COMPOSITE_DISPLAY_MISMATCH")
