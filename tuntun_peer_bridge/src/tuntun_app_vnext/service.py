"""Versioned rank display and full app input usage; no mock fallback."""
from copy import deepcopy
from . import SCHEMA_VERSION, MAPPING_VERSION, DISPLAY_VERSION
from .inputs import normalize
from ..tuntun_peer.percentile import rounded


def rank_number(top_percent):
    return max(1, min(100, int(rounded(top_percent, 0))))


def rank_display(component):
    if not component["available"]:
        return {"rankApprox": None, "rankRange": None, "text": "또래 위치 미산출", "tieNotice": None}
    p = component["peerPercentile"]
    low, high = component["percentileRange"]
    rank = rank_number(100-p)
    return {"rankApprox": rank, "rankRange": [rank_number(100-high), rank_number(100-low)],
            "text": f"또래 100명 중 약 {rank}등",
            "tieNotice": "동점자가 있어 대략적인 위치입니다." if component["tieMassPercent"] > 0 else None}


class AppService:
    def __init__(self, peer_service):
        self.peer_service = peer_service

    def score(self, request, client_schema=SCHEMA_VERSION):
        if client_schema != SCHEMA_VERSION:
            raise ValueError("UNSUPPORTED_CLIENT_SCHEMA")
        normalized = normalize(request)
        try:
            peer = self.peer_service.score(normalized["canonical"])
        except Exception as exc:
            raise RuntimeError("MODEL_INFERENCE_FAILED") from exc
        output = deepcopy(peer)
        output["schemaVersion"] = SCHEMA_VERSION
        output["inputMappingVersion"] = MAPPING_VERSION
        output["displayPolicyVersion"] = DISPLAY_VERSION
        output["ageContext"] = normalized["ageContext"]
        output["habitContext"] = normalized["habitContext"]
        output["inputRevision"] = normalized["inputRevision"]
        output["isMock"] = False
        output["comparisonKey"] += "|"+MAPPING_VERSION+"|age="+str(normalized["ageContext"]["ageYearsUsed"])+"|approx="+str(normalized["ageContext"]["isApproximate"])
        for component in output["components"]:
            component["displayPolicyVersion"] = DISPLAY_VERSION
            component["rankDisplay"] = rank_display(component)
            if normalized["strengthReason"] and component["unavailableReason"] == "MISSING_REQUIRED_INPUT":
                component["unavailableReason"] = normalized["strengthReason"]
            if not normalized["ageContext"]["healthAgeSupported"] and component["unavailableReason"] == "MISSING_REQUIRED_INPUT":
                component["unavailableReason"] = "AGE_RANGE_UNSUPPORTED_OR_UNCERTAIN"
        score = output["peerCompositeScore"]
        output["compositeDisplay"] = {"score": rounded(score) if score is not None else None,
                                      "unit": "점", "bandLabel": None,
                                      "text": f"튼튼지수 {rounded(score):.1f}점" if score is not None else "튼튼지수 미산출"}
        output["inputUsage"] = {
            "birthYearMonth": "model_age_and_peer_reference",
            "sex": "model_and_peer_reference", "pregnancyStatus": "health_eligibility",
            "heightCm": "health_models", "weightKg": "health_models",
            "strengthWeeklyCount": "health_models_and_lifestyle_if_days_confirmed",
            "strengthIntensity": "context_only_not_scored", "aerobicLowMinutes": "context_only_not_scored",
            "aerobicModerateMinutes": "health_models_and_lifestyle", "aerobicVigorousMinutes": "health_models_and_lifestyle_times_two",
            "bedtimeWakeTime": "time_interval_context_only_not_scored",
        }
        output["referenceCaution"] = "동일 성별·연령대 개발 표본의 환산 위치입니다. 실제 100명의 등수가 아니며 연령대 변경으로 순위가 달라질 수 있습니다."
        from .schema import validate_response
        validate_response(output)
        return output
