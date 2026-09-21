"""Normalize real app fields without inventing a birthday or exercise days."""
import calendar
from datetime import date
import re
from ..tuntun_peer.percentile import finite

FIELDS = {
    "birthYear", "birthMonth", "sex", "pregnancyStatus", "heightCm", "weightKg",
    "strengthWeeklyCount", "strengthFrequencyUnit", "strengthIntensity",
    "aerobicLowMinutes", "aerobicModerateMinutes", "aerobicVigorousMinutes",
    "bedtime", "wakeTime", "referenceDate", "inputRevision",
}


def month_age(year, month, reference_date):
    if type(year) is not int or type(month) is not int or not 1 <= month <= 12 or not 1 <= year <= 9999:
        raise ValueError("INVALID_BIRTH_YEAR_MONTH")
    today = date.fromisoformat(reference_date)
    if (year, month) > (today.year, today.month):
        raise ValueError("FUTURE_BIRTH_YEAR_MONTH")
    years = today.year-year
    if today.month < month:
        low = high = years-1
    elif today.month > month or today.day == calendar.monthrange(today.year, today.month)[1]:
        low = high = years
    else:
        low, high = years-1, years
    # Policy: use youngest possible age until everyone born in that month has
    # had their birthday. It is a convention, not an imputed birth date.
    return {"ageYearsUsed": low, "possibleAgeYears": [low, high], "isApproximate": low != high,
            "policy": "completed_birth_month_lower_age_v1", "referenceDate": reference_date,
            "timezone": "Asia/Seoul", "ageTopCoded": low >= 80,
            "healthAgeSupported": 19 <= low <= high <= 120,
            "notice": "출생연월 기준 근사 나이입니다. 생일 월에는 가능한 나이 중 낮은 값을 사용합니다." if low != high else "출생연월과 기준일로 만 나이를 계산했습니다."}


def clock_minutes(value):
    if value is None:
        return None
    if not isinstance(value, str) or not re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", value):
        raise ValueError("INVALID_CLOCK_TIME")
    hour, minute = map(int, value.split(":"))
    return hour*60+minute


def normalize(request):
    if not isinstance(request, dict) or set(request)-FIELDS:
        raise ValueError("UNKNOWN_APP_INPUT")
    if not {"birthYear", "birthMonth", "referenceDate", "sex"} <= set(request):
        raise ValueError("MISSING_DEMOGRAPHIC_INPUT")
    if request["sex"] not in ("male", "female"):
        raise ValueError("INVALID_SEX")
    if not isinstance(request["referenceDate"], str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", request["referenceDate"]):
        raise ValueError("INVALID_REFERENCE_DATE")
    age = month_age(request["birthYear"], request["birthMonth"], request["referenceDate"])
    pregnancy = request.get("pregnancyStatus", "unknown")
    if pregnancy not in ("nonpregnant", "pregnant", "unknown", "not_applicable"):
        raise ValueError("INVALID_PREGNANCY_STATUS")
    if (pregnancy == "pregnant" and request["sex"] == "male") or (pregnancy == "not_applicable" and request["sex"] != "male"):
        raise ValueError("CONTRADICTORY_PREGNANCY_STATUS")
    ranges = {"heightCm": (100, 220), "weightKg": (25, 250),
              "aerobicLowMinutes": (0, 10080), "aerobicModerateMinutes": (0, 10080),
              "aerobicVigorousMinutes": (0, 10080)}
    for key, (low, high) in ranges.items():
        if request.get(key) is not None and not finite(request[key], low, high):
            raise ValueError("INVALID_"+key.upper())
    minutes = [request.get(k) for k in ("aerobicLowMinutes", "aerobicModerateMinutes", "aerobicVigorousMinutes")]
    if sum(v for v in minutes if v is not None) > 10080:
        raise ValueError("ACTIVITY_EXCEEDS_WEEK")
    count = request.get("strengthWeeklyCount")
    if count is not None and (type(count) is not int or not 0 <= count <= 7):
        raise ValueError("INVALID_STRENGTH_COUNT")
    unit = request.get("strengthFrequencyUnit")
    if unit not in (None, "days", "sessions"):
        raise ValueError("INVALID_STRENGTH_UNIT")
    strength = min(count, 5) if count is not None and (count == 0 or unit == "days") else None
    strength_reason = "STRENGTH_DAYS_UNCONFIRMED" if count is not None and count > 0 and unit != "days" else None
    intensity = request.get("strengthIntensity")
    if intensity not in (None, "light", "moderate", "hard"):
        raise ValueError("INVALID_STRENGTH_INTENSITY")
    if count == 0 and intensity is not None:
        raise ValueError("STRENGTH_INTENSITY_WITH_ZERO_DAYS")
    bed, wake = clock_minutes(request.get("bedtime")), clock_minutes(request.get("wakeTime"))
    interval = (wake-bed) % 1440 if bed is not None and wake is not None and bed != wake else None
    moderate, vigorous = request.get("aerobicModerateMinutes"), request.get("aerobicVigorousMinutes")
    equivalent = moderate+2*vigorous if moderate is not None and vigorous is not None else None
    revision = request.get("inputRevision")
    if revision is not None and (not isinstance(revision, str) or not 1 <= len(revision) <= 100):
        raise ValueError("INVALID_INPUT_REVISION")
    features = {"age_years": age["ageYearsUsed"] if age["healthAgeSupported"] else None,
                "sex_code": 1 if request["sex"] == "male" else 2,
                "height_cm": request.get("heightCm"), "weight_kg": request.get("weightKg"),
                "leisure_aerobic_moderate_equivalent_min_week": equivalent, "strength_days_week": strength}
    return {
        "canonical": {"features": features, "pregnancy_status": pregnancy,
                      "activity_window_end": request["referenceDate"], "recorded_days": 0},
        "ageContext": age, "strengthReason": strength_reason, "inputRevision": revision,
        "habitContext": {
            "lowIntensityMinutes": request.get("aerobicLowMinutes"),
            "totalReportedActivityMinutes": sum(minutes) if all(v is not None for v in minutes) else None,
            "moderateEquivalentMinutes": equivalent,
            "strengthIntensity": intensity, "strengthDaysTopCoded": count is not None and count >= 5 and unit == "days",
            "bedToWakeIntervalMinutes": interval,
            "bedToWakeStatus": "available" if interval is not None else "ambiguous_same_time" if bed is not None and wake == bed else "incomplete_optional_input",
            "lowIntensityNotice": "저강도 활동 기록에 반영했습니다. 현재 건강모델·생활습관 점수에는 가산하지 않습니다.",
            "strengthIntensityNotice": "선택한 근력 강도 설명에 반영합니다. 강도를 높였다고 자동 가산하지 않습니다.",
            "sleepNotice": "취침부터 기상까지의 시간 간격이며 실제 수면시간·수면의 질·규칙성 점수는 아닙니다.",
        },
    }
