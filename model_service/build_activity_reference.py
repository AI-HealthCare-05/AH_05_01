"""Build aggregate-only KNHANES activity references from pinned 2019-2021 CSVs.

Run offline. No model training, participant export, member data or API calls.
"""

import argparse
import csv
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path

VERSION = "knhanes-activity-2019-2021-weighted-v1"
SOURCE_PINS = {
    2019: "34b87583dd5eeac553fbc6fa19adecdd698b9639fa52c3a8de62e65cc5f0a0d7",
    2020: "3970e1e376d8ad700e6d6659f80dd94674d66cf9dc2bba91c4f47ade0f82ba2f",
    2021: "578ab64da503b6b0d51f640aa4e39aa7e973a8dbd05f4c7adecaac66c95788d8",
}
BRANCHES = {
    "work_vigorous": ("BE3_71", "BE3_72", "BE3_73", "BE3_74"),
    "leisure_vigorous": ("BE3_75", "BE3_76", "BE3_77", "BE3_78"),
    "work_moderate": ("BE3_81", "BE3_82", "BE3_83", "BE3_84"),
    "leisure_moderate": ("BE3_85", "BE3_86", "BE3_87", "BE3_88"),
    "transport": ("BE3_91", "BE3_92", "BE3_93", "BE3_94"),
}
AEROBIC = "leisure_aerobic_moderate_equivalent_min_week"
STRENGTH = "strength_days_week"


def number(value):
    try:
        result = float(value)
        return result if math.isfinite(result) else None
    except (ValueError, TypeError):
        return None


def whole(value, low, high):
    value = number(value)
    return value if value is not None and low <= value <= high and value.is_integer() else None


def weekly_minutes(row, columns):
    participation = number(row.get(columns[0]))
    if participation == 2:
        return 0.0
    if participation != 1:
        return None
    days = whole(row.get(columns[1]), 1, 7)
    hours = whole(row.get(columns[2]), 0, 23)
    minutes = whole(row.get(columns[3]), 0, 59)
    if None in (days, hours, minutes):
        return None
    return days * (hours * 60 + minutes)


def features(row):
    branches = {key: weekly_minutes(row, columns) for key, columns in BRANCHES.items()}
    moderate, vigorous = branches["leisure_moderate"], branches["leisure_vigorous"]
    strength = whole(row.get("BE5_1"), 1, 6)
    return {
        AEROBIC: None if None in (moderate, vigorous) else moderate + 2 * vigorous,
        STRENGTH: None if strength is None else strength - 1,
    }, branches


def age_group(age, sex):
    if age is None or not 19 <= age <= 120 or sex not in (1, 2):
        return None
    return ("19-39" if age < 40 else "40-64" if age < 65 else "65+") + f":{int(sex)}"


def survey_summary(records, design, group, feature):
    """Taylor linearization for a domain ratio mean, with zero PSU contributions.

    All sampled PSUs (including those without domain responses) remain in the
    variance. Year is nested into strata/PSU. With-replacement, no FPC supplied.
    """
    selected = [r for r in records if r["group"] == group and r[feature] is not None]
    n = len(selected)
    if not n:
        return {"n": 0, "status": "insufficient_sample"}
    denominator = math.fsum(r["weight"] for r in selected)
    mean = math.fsum(r["weight"] * r[feature] for r in selected) / denominator
    effective_n = denominator**2 / math.fsum(r["weight"] ** 2 for r in selected)
    linearized = defaultdict(float)
    for row in selected:
        linearized[row["design"]] += row["weight"] * (row[feature] - mean) / denominator
    variance, singleton = 0.0, False
    for strata, clusters in design.items():
        if len(clusters) < 2:
            singleton = True
            continue
        scores = [linearized[(strata, cluster)] for cluster in clusters]
        center = math.fsum(scores) / len(scores)
        variance += len(scores) / (len(scores) - 1) * math.fsum((s - center) ** 2 for s in scores)
    se = math.sqrt(variance)
    relative_se = se / mean if mean > 0 else None
    stable = n >= 30 and effective_n >= 30 and not singleton and (relative_se is None or relative_se <= 0.3)
    cumulative, median = 0.0, None
    for row in sorted(selected, key=lambda r: r[feature]):
        cumulative += row["weight"]
        if cumulative >= denominator / 2:
            median = row[feature]
            break
    result = {
        "status": "ready" if stable else "unstable_estimate",
        "n": n,
        "mean": mean,
        "median": median,
        "standard_error": se,
        "relative_standard_error": relative_se,
        "effective_n": effective_n,
        "ci95_normal": [max(0.0, mean - 1.96 * se), mean + 1.96 * se],
        "zero_response_n": sum(r[feature] == 0 for r in selected),
    }
    if feature == STRENGTH:
        result["topcoded_n"] = sum(r[feature] == 5 for r in selected)
        result["two_or_more_days_percent"] = (
            100 * math.fsum(r["weight"] for r in selected if r[feature] >= 2) / denominator
        )
    return result


def check_official_indicators(row, values, branches, checks):
    official = number(row.get("pa_aerobic"))
    if official in (0, 1) and all(v is not None for v in branches.values()):
        total = (
            2 * (branches["work_vigorous"] + branches["leisure_vigorous"])
            + branches["work_moderate"]
            + branches["leisure_moderate"]
            + branches["transport"]
        )
        checks["official_aerobic_compared"] += 1
        checks["official_aerobic_mismatch"] += int(int(total >= 150) != official)
    muscle = number(row.get("pa_muscle"))
    if muscle in (0, 1) and values[STRENGTH] is not None:
        checks["official_strength_compared"] += 1
        checks["official_strength_mismatch"] += int(int(values[STRENGTH] >= 2) != muscle)


def build_groups(records, design):
    groups = {}
    for band in ("19-39", "40-64", "65+"):
        for sex in (1, 2):
            group = f"{band}:{sex}"
            groups[group] = {
                "n": sum(r["group"] == group for r in records),
                "metrics": {
                    feature: survey_summary(records, design, group, feature) for feature in (AEROBIC, STRENGTH)
                },
            }
    return groups


def build(source_dir):
    records, sources, design = [], [], defaultdict(set)
    checks = {
        "official_aerobic_compared": 0,
        "official_aerobic_mismatch": 0,
        "official_strength_compared": 0,
        "official_strength_mismatch": 0,
    }
    for year, pin in SOURCE_PINS.items():
        path = source_dir / f"hn{year % 100}_all.csv"
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != pin:
            raise ValueError(f"Source hash mismatch: {path.name}")
        row_count, adult_count = 0, 0
        with path.open(encoding="utf-8-sig", newline="") as stream:
            reader = csv.DictReader(stream)
            required = {"age", "sex", "BE5_1", "wt_itvex", "kstrata", "psu"} | {
                c for cs in BRANCHES.values() for c in cs
            }
            if required - set(reader.fieldnames or []):
                raise ValueError(f"Required columns missing: {path.name}")
            for row in reader:
                row_count += 1
                age, sex = whole(row["age"], 0, 120), whole(row["sex"], 1, 2)
                group = age_group(age, sex)
                adult_count += int(age is not None and age >= 19)
                values, branches = features(row)
                if group:
                    check_official_indicators(row, values, branches, checks)
                weight = number(row["wt_itvex"])
                if weight is None or weight <= 0:
                    continue
                if not row["kstrata"] or not row["psu"]:
                    raise ValueError("Survey design missing for a positive weight")
                strata, cluster = (year, row["kstrata"]), row["psu"]
                design[strata].add(cluster)
                records.append({"group": group, "weight": weight / 3, "design": (strata, cluster), **values})
        sources.append(
            {"file": path.name, "year": year, "sha256": actual, "rows": row_count, "adult_rows": adult_count}
        )
    if checks["official_aerobic_compared"] < 10000 or checks["official_aerobic_mismatch"]:
        raise ValueError(f"Official aerobic indicator crosscheck failed: {checks}")
    if checks["official_strength_mismatch"]:
        raise ValueError("Official strength indicator crosscheck failed")
    groups = build_groups(records, design)
    return {
        "schema_version": VERSION,
        "source_kind": "knhanes_population_survey",
        "years": [2019, 2020, 2021],
        "weighting": "wt_itvex / 3",
        "grouping": "age19_39_40_64_65plus_by_survey_sex",
        "source_label": "국민건강영양조사 2019~2021",
        "source_url": "https://knhanes.kdca.go.kr/knhanes/main.do",
        "sources": sources,
        "adult_source_rows": sum(s["adult_rows"] for s in sources),
        "variance_method": "Taylor domain ratio; year-nested strata and PSU; with-replacement; no FPC; normal 95% interval",
        "suppression_policy": "n>=30, Kish effective_n>=30, RSE<=0.30, no singleton strata",
        "definitions": {
            AEROBIC: "leisure moderate + 2 * leisure vigorous; zero only when participation=no; invalid/missing excluded",
            STRENGTH: "BE5_1 codes 1..6 -> 0..5 days; 5+ topcoded at 5; codes 8/9/missing excluded",
        },
        "definition_evidence": "Frozen activity_features.py; KDCA raw-data user guide updated 2026-07 PDF pp.38,41,177; official pa_aerobic reproduction in each pinned CSV",
        "crosschecks": checks,
        "groups": groups,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    package = build(args.source_dir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(package, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    args.output.write_bytes(payload.encode("utf-8"))
    print(
        json.dumps(
            {
                "sha256": hashlib.sha256(payload.encode()).hexdigest(),
                "adult_source_rows": package["adult_source_rows"],
                "crosschecks": package["crosschecks"],
                "groups": package["groups"],
            },
            ensure_ascii=False,
        )
    )
