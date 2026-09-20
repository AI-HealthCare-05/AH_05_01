"""Versioned survey comparisons. Shared by the model worker and local authoring UI.

Only precomputed aggregate statistics are shipped. Text and arithmetic stay local.
"""

import hashlib
import json
import math
from functools import lru_cache
from pathlib import Path

REFERENCE_PATH = Path(__file__).resolve().parents[1] / "app/data/reference/knhanes_activity_2019_2021.json"
REFERENCE_PIN = "9f90ba20624d0afac93fbbb4968ce9e0226946892bf518574b4f4bb35f8ab787"
REFERENCE_VERSION = "knhanes-activity-2019-2021-weighted-v1"
METRICS = {
    "leisure_aerobic_moderate_equivalent_min_week": ("유산소 활동량", "분", 0),
    "strength_days_week": ("근력운동 일수", "일", 1),
}
BANDS = {"19-39": "만 19~39세", "40-64": "만 40~64세", "65+": "만 65세 이상"}


def coaching_hint(feature, value):
    """설문 응답에 맞춘 편집 힌트이며 또래 평균을 운동 목표로 쓰지 않습니다."""
    if feature not in METRICS or not math.isfinite(value) or value < 0:
        raise ValueError("ACTIVITY_HINT_INPUT_INVALID")
    strength = feature == "strength_days_week"
    label = "근력운동 일수" if strength else "유산소 활동량"
    if value == 0:
        title = "내가 편한 시작 찾기"
        text = "이번 설문은 내 시작점을 살펴보는 기록이에요. 해보고 싶은 작은 실천부터 천천히 찾아봐요."
        reason = f"{label}을 0으로 알려주셔서, 시작을 응원하는 힌트를 담았어요."
    elif strength:
        title = "내 운동 리듬에 맞춰"
        text = "알려주신 운동 리듬에 맞춰 이어가 볼까요? 오늘의 컨디션에 어울리는 작은 실천을 떠올려봐요."
        reason = "근력운동을 한다고 알려주셔서, 내 리듬에 맞춰 이어갈 힌트를 담았어요."
    else:
        title = "오늘의 틈을 찾아볼까요?"
        text = "오늘 일정에는 어떤 움직임을 끼워 넣기 편할까요? 잠깐의 여유에 할 수 있는 작은 실천을 떠올려봐요."
        reason = "유산소 활동량을 알려주셔서, 생활 속에서 이어갈 힌트를 담았어요."
    return dict(
        version="tmtn-activity-hint-v1",
        basis="survey_zero" if value == 0 else "survey_reported",
        title=title,
        text=text,
        reason=reason,
    )


def group_key(age, sex):
    if (
        type(age) not in (int, float)
        or not math.isfinite(age)
        or not 19 <= age <= 120
        or int(age) != age
        or type(sex) not in (int, float)
        or sex not in (1, 2)
    ):
        raise ValueError("ACTIVITY_INPUT_INVALID")
    return ("19-39" if age < 40 else "40-64" if age < 65 else "65+") + f":{int(sex)}"


@lru_cache(maxsize=1)
def load_reference():
    raw = REFERENCE_PATH.read_bytes().replace(b"\r\n", b"\n")
    if hashlib.sha256(raw).hexdigest() != REFERENCE_PIN:
        raise ValueError("ACTIVITY_REFERENCE_MISMATCH")
    package = json.loads(raw)
    if (
        package["schema_version"] != REFERENCE_VERSION
        or package["weighting"] != "wt_itvex / 3"
        or package["years"] != [2019, 2020, 2021]
    ):
        raise ValueError("ACTIVITY_REFERENCE_CONTRACT_MISMATCH")
    return package


def build_comparison(features, *, input_revision, reference_date, expected_group=None):
    package = load_reference()
    key = group_key(features["age_years"], features["sex_code"])
    if expected_group is not None and key != expected_group:
        raise ValueError("ACTIVITY_GROUP_MISMATCH")
    band, sex = key.split(":")
    label = BANDS[band] + (" 남성" if sex == "1" else " 여성")
    group = package["groups"][key]
    cards = []
    for feature, (metric_label, unit, decimals) in METRICS.items():
        value = features.get(feature)
        if (
            type(value) not in (float, int)
            or not math.isfinite(value)
            or value < 0
            or (feature == "strength_days_week" and (value > 7 or int(value) != value))
        ):
            continue
        statistic = group["metrics"][feature]
        if statistic["status"] != "ready":
            continue
        mean = statistic["mean"]
        capped = feature == "strength_days_week" and value >= 5
        compared_value = min(value, 5) if capped else value
        mean_display = f"{mean:.{decimals}f}".removesuffix(".0")
        value_display = "5일 이상" if capped else f"{value:g}{unit}"
        delta = compared_value - mean
        delta_display = f"{abs(delta):.{decimals}f}".removesuffix(".0")
        relation = (
            "평균과 비슷하게 답하셨어요."
            if float(delta_display) == 0
            else (f"평균보다 약 {delta_display}{unit} {'많게' if delta > 0 else '적게'} 답하셨어요.")
        )
        if capped:
            relation = "조사에서 가장 높은 일수 구간에 해당해요."
        if feature == "strength_days_week":
            title = "근력운동을 챙긴 날은"
            text = f"설문에서 근력운동을 주 {value_display} 한다고 알려주셨어요. 같은 나이대·성별의 평균은 주 {mean_display}일이에요."
            note = "조사의 ‘주 5일 이상’ 응답은 5일로 계산한 평균이에요."
        else:
            title = "유산소 활동을 나란히 보면"
            text = f"설문에 답한 유산소 활동량은 주 {value_display}이에요. 같은 나이대·성별의 평균은 주 {mean_display}분이에요."
            note = "여가 중·고강도 활동을 중강도 기준으로 정리한 설문 활동량이에요."
        cards.append(
            {
                "key": feature,
                "label": metric_label,
                "unit": unit,
                "n": statistic["n"],
                "mean": mean,
                "value": compared_value,
                "mean_display": mean_display,
                "value_display": "5+" if capped else f"{value:g}",
                "delta": delta,
                "title": title,
                "text": text,
                "comparison_text": relation,
                "unit_note": note,
                "ci95": statistic["ci95_normal"],
                "topcoded": capped,
                "coaching_hint": coaching_hint(feature, compared_value),
            }
        )
    return {
        "status": "ready" if cards else "insufficient_sample",
        "cards": cards,
        "reference_version": REFERENCE_VERSION,
        "source_sha256": REFERENCE_PIN,
        "input_revision": input_revision,
        "reference_date": reference_date,
        "group_key": key,
        "group_label": label,
        "n": group["n"],
        "source_kind": "knhanes_population_survey",
        "years": [2019, 2020, 2021],
        "source_label": package["source_label"],
        "source_url": package["source_url"],
        "scope_note": "평균은 운동 목표가 아니에요. 내 몸에 맞는 속도로 이어가세요.",
        "activity_unit_note": "저장한 운동 설문을 바탕으로 비교했어요.",
        "method_note": "조사 가중치를 적용한 평균이에요. 운동하지 않는다는 응답도 포함하고, 무응답은 제외했어요. 항목별 응답 인원은 다를 수 있어요.",
        "grouping": package["grouping"],
    }
