"""Deterministic, domain-aware copy from an already validated SHAP snapshot.

Ranks here describe attribution magnitudes within one calculation, not clinical
importance, treatment effects, risk, or comparisons of disease severity.
"""

import math

VERSION = "tmtn-shap-story-v2"
EPS = 1e-8
LABELS = {
    "age_years": "나이",
    "sex_code": "성별",
    "height_cm": "키",
    "weight_kg": "몸무게",
    "leisure_aerobic_moderate_equivalent_min_week": "유산소 활동 시간",
    "strength_days_week": "근력운동 일수",
}
DOMAIN_LABELS = {"diabetes": "당뇨", "hypertension": "혈압"}
ACTIVITY_KEYS = list(LABELS)[-2:]
INTRODUCTIONS = {
    "diabetes": (
        "당뇨 참고지표, 내 속도로 차곡차곡",
        "습관은 내 생활에 들어갈 자리가 있을 때 이어가기 편해요. 오늘은 일상 틈에 넣기 좋은 미션 하나부터 골라볼까요?",
    ),
    "hypertension": (
        "혈압 참고지표, 실천은 부담 없이",
        "매일 같은 만큼 해내지 않아도 괜찮아요. 오늘의 컨디션에 맞는 미션을 골라, 할 수 있는 만큼 이어가 봐요.",
    ),
}


def direction(value):
    return "higher" if value > EPS else "lower" if value < -EPS else "neutral"


def phrase(value):
    return {"higher": "높이는 쪽", "lower": "낮추는 쪽", "neutral": "거의 차이가 없는 쪽"}[direction(value)]


def ordered(rows):
    return sorted(rows, key=lambda row: (-abs(row["value"]), row["key"]))


def leaders(rows):
    ranked = ordered(rows)
    peak = abs(ranked[0]["value"])
    return [row for row in ranked if peak > EPS and abs(abs(row["value"]) - peak) <= EPS]


def describe_focus(rows, label):
    top = leaders(rows)
    focus = [r["key"] for r in top]
    if not top:
        return f"{label} 모델에서 살펴본 내 정보", "이번 계산에서는 기준 자료와 비교한 각 항목의 차이가 거의 없어요."
    if len(top) > 1:
        return (
            f"{label} 계산에서 나란히 반영된 정보",
            f"{'·'.join(LABELS[k] for k in focus)} 항목이 같은 크기로 가장 크게 반영됐어요. 각 방향은 아래 막대에서 살펴봐요.",
        )
    name = LABELS[top[0]["key"]]
    summary = f"{label} 계산에서는 {name} 항목이 참고점수를 {phrase(top[0]['value'])}으로 가장 크게 반영됐어요."
    following = leaders([r for r in rows if r["key"] not in focus])
    if len(following) == 1:
        second = following[0]
        summary += f" 그다음인 {LABELS[second['key']]} 항목은 {phrase(second['value'])}이에요."
    elif following:
        summary += f" 그다음에는 {'·'.join(LABELS[r['key']] for r in following)} 항목이 같은 크기로 이어져요."
    return f"{label} 계산에서 먼저 보이는 {name}", summary


def make_story(domains, selected):
    """Call only after release, completeness and additivity validation."""
    if len(domains) != 2 or selected not in DOMAIN_LABELS or {d["domain"] for d in domains} != set(DOMAIN_LABELS):
        raise ValueError("Story domain mismatch")
    by_domain = {d["domain"]: d for d in domains}
    for domain in domains:
        rows = domain["contributions"]
        if len(rows) != len(LABELS) or {r["key"] for r in rows} != set(LABELS):
            raise ValueError("Story feature mismatch")
        if any(not math.isfinite(r["value"]) for r in rows):
            raise ValueError("Story nonfinite attribution")
    other_key = "hypertension" if selected == "diabetes" else "diabetes"
    label = DOMAIN_LABELS[selected]
    rows, other_rows = by_domain[selected]["contributions"], by_domain[other_key]["contributions"]
    values = {r["key"]: r["value"] for r in rows}
    top = leaders(rows)
    focus = [r["key"] for r in top]
    calculation_title, calculation_summary = describe_focus(rows, label)
    # Editorial encouragement is not inferred from a positive attribution and
    # never labels the largest modifiable feature as the model's overall leader.
    title, summary = INTRODUCTIONS[selected]

    aerobic, strength = (values[k] for k in ACTIVITY_KEYS)
    if direction(aerobic) == direction(strength) == "neutral":
        activity = f"{label} 모델에서는 두 운동 항목 모두 기준 자료와의 차이가 거의 없어요."
    elif direction(aerobic) == direction(strength):
        activity = (
            f"입력한 유산소 활동 시간과 근력운동 일수는 모두 {label} 참고점수를 {phrase(aerobic)}으로 반영됐어요."
        )
    else:
        activity = f"{label} 참고점수에서 유산소 활동 시간은 {phrase(aerobic)}, 근력운동 일수는 {phrase(strength)}으로 반영됐어요."

    kind, contrast, comparison_keys = compare_models(rows, other_rows, selected)
    return {
        "version": VERSION,
        "domain": selected,
        "title": title,
        "summary": summary,
        "intro_kind": "challenge_encouragement",
        "calculation_title": calculation_title,
        "calculation_summary": calculation_summary,
        "context_note": "참고점수는 실천의 성적표가 아니에요. 나이·성별도 계산에 쓰이지만, 챌린지에서 바꿔야 할 목표는 아니에요.",
        "focus_keys": focus,
        "activity_text": activity,
        "activity_keys": ACTIVITY_KEYS,
        "comparison_text": contrast,
        "comparison_kind": kind,
        "comparison_keys": comparison_keys,
        "scope_note": "이번 모델 계산을 풀어 본 설명이에요. 실제 혈당·혈압의 변화나 운동 효과를 뜻하지는 않아요.",
    }


def compare_models(rows, other_rows, selected):
    other_key = "hypertension" if selected == "diabetes" else "diabetes"
    label, other_label = DOMAIN_LABELS[selected], DOMAIN_LABELS[other_key]
    values = {r["key"]: r["value"] for r in rows}
    other_values = {r["key"]: r["value"] for r in other_rows}
    focus = [r["key"] for r in leaders(rows)]
    other_top = leaders(other_rows)
    different = [r for r in ordered(rows) if direction(r["value"]) != direction(other_values[r["key"]])]
    comparison_keys = []
    if different:
        key = different[0]["key"]
        comparison_keys = [key]
        kind = "different_direction"
        contrast = f"같은 {LABELS[key]} 항목도 {label} 참고점수에서는 {phrase(values[key])}, {other_label} 참고점수에서는 {phrase(other_values[key])}으로 반영됐어요."
    elif focus and [r["key"] for r in other_top] != focus:
        kind = "different_leader"
        comparison_keys = focus + [r["key"] for r in other_top if r["key"] not in focus]
        contrast = f"{label} 계산에서는 {'·'.join(LABELS[k] for k in focus)}, {other_label} 계산에서는 {'·'.join(LABELS[r['key']] for r in other_top) or '두드러지는 항목이 없음'} 항목이 가장 크게 반영됐어요."
    else:
        # The leader is often age in both models. Show a real difference in the
        # next tier rather than inventing disease-specific biological effects.
        remaining = [r for r in rows if r["key"] not in focus]
        other_remaining = [r for r in other_rows if r["key"] not in focus]
        runner, other_runner = (
            leaders(remaining) if remaining else [],
            leaders(other_remaining) if other_remaining else [],
        )
        if runner and other_runner and [r["key"] for r in runner] != [r["key"] for r in other_runner]:
            kind = "different_next_tier"
            comparison_keys = list(dict.fromkeys(r["key"] for r in runner + other_runner))
            contrast = f"가장 큰 항목 다음으로는 {label} 계산에서 {'·'.join(LABELS[r['key']] for r in runner)}, {other_label} 계산에서 {'·'.join(LABELS[r['key']] for r in other_runner)} 항목이 눈에 들어와요."
        else:
            kind = "shared_pattern"
            contrast = "두 지표에서 크게 반영된 항목과 방향이 비슷해요. 같은 정보를 각 모델이 따로 계산한 결과예요."
    return kind, contrast, comparison_keys
