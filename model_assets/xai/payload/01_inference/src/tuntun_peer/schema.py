"""Closed JSON Schema and dependency-free validator for this schema's vocabulary."""
import math

def obj(properties):
    return {"type": "object", "properties": properties, "required": list(properties), "additionalProperties": False}

TEXT = {"type": "string"}
BOOL = {"type": "boolean"}
NUMBER = {"type": "number", "minimum": 0, "maximum": 100}
NULL_NUMBER = {**NUMBER, "type": ["number", "null"]}
NULL_TEXT = {"type": ["string", "null"]}
DISPLAY = {**obj({"percentile": NUMBER, "topPercentApprox": NUMBER, "text": TEXT}), "type": ["object", "null"]}
COMPONENT_SCHEMA = obj({
    "componentKey": {"enum": ["physical", "diabetes", "hypertension", "lifestyle", "muscle"]}, "label": TEXT,
    "direction": {"enum": ["higher_is_healthier", "higher_is_more_not_healthier"]}, "includedInComposite": BOOL,
    "valueType": {"enum": ["health_direction_peer_percentile", "quantity_peer_percentile"]}, "absoluteReferenceScore": NULL_NUMBER,
    "peerPercentile": NULL_NUMBER, "topPercentApprox": NULL_NUMBER,
    "percentileRange": {"type": ["array", "null"], "items": NUMBER, "minItems": 2, "maxItems": 2},
    "tieMassPercent": NULL_NUMBER, "referenceGroup": NULL_TEXT,
    "referenceN": {"type": ["integer", "null"], "minimum": 1},
    "referenceEffectiveN": {"type": ["number", "null"], "minimum": 0},
    "available": BOOL, "unavailableReason": NULL_TEXT,
    "modelVersion": TEXT, "referenceVersion": TEXT, "formulaVersion": TEXT, "displayPolicyVersion": TEXT,
    "source": {"enum": ["questionnaire", "model_inference", "synthetic", "unavailable"]}, "display": DISPLAY})
RESPONSE_SCHEMA = {"$schema": "https://json-schema.org/draft/2020-12/schema", **obj({
    "schemaVersion": {"const": "tuntun-peer-v0.1"}, "formulaVersion": TEXT, "displayPolicyVersion": TEXT,
    "modelVersion": TEXT, "referenceVersion": TEXT,
    "referenceYears": {"type": "array", "items": {"type": "integer"}, "minItems": 1},
    "referencePopulation": TEXT, "components": {"type": "array", "items": COMPONENT_SCHEMA, "minItems": 4, "maxItems": 5},
    "peerCompositeScore": NULL_NUMBER, "availableComponents": {"type": "array", "items": TEXT, "maxItems": 4},
    "availableComponentCount": {"type": "integer", "minimum": 0, "maximum": 4}, "isPartialScore": BOOL,
    "scoreAvailable": BOOL, "ageTopCoded": BOOL,
    "lifestyleAvailableSubcomponentCount": {"type": "integer", "minimum": 0, "maximum": 2},
    "comparisonKey": TEXT, "isSynthetic": BOOL, "releaseStatus": {"const": "LOCAL_REVIEW_CANDIDATE"}, "notice": TEXT})}


def validate_json(value, schema):
    if "const" in schema and value != schema["const"]:
        raise ValueError("SCHEMA_CONST")
    if "enum" in schema and value not in schema["enum"]:
        raise ValueError("SCHEMA_ENUM")
    types = schema.get("type", [])
    types = [types] if isinstance(types, str) else types
    kind = ("null" if value is None else "boolean" if type(value) is bool else "integer" if type(value) is int
            else "number" if type(value) is float else "string" if type(value) is str
            else "array" if type(value) is list else "object" if type(value) is dict else "unknown")
    if types and kind not in types and not (kind == "integer" and "number" in types):
        raise ValueError("SCHEMA_TYPE")
    if kind in ("integer", "number"):
        if not math.isfinite(value) or value < schema.get("minimum", -math.inf) or value > schema.get("maximum", math.inf):
            raise ValueError("SCHEMA_RANGE")
    if kind == "object":
        properties = schema.get("properties", {})
        if not set(schema.get("required", [])) <= set(value) or (schema.get("additionalProperties") is False and set(value)-set(properties)):
            raise ValueError("SCHEMA_PROPERTIES")
        for key, item in value.items():
            if key in properties:
                validate_json(item, properties[key])
    if kind == "array":
        if not schema.get("minItems", 0) <= len(value) <= schema.get("maxItems", math.inf):
            raise ValueError("SCHEMA_ARRAY_LENGTH")
        for item in value:
            validate_json(item, schema.get("items", {}))
