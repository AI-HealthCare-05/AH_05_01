"""Reference store exports distribution parameters, never research identifiers."""
from .percentile import distribution, finite, validate_distribution
from .registry import CORE


def group_key(age, sex, grouping="broad"):
    if not finite(age, 19, 120) or age != int(age) or type(sex) is bool or sex not in (1, 2):
        return None
    if grouping == "broad":
        band = "19-39" if age < 40 else "40-64" if age < 65 else "65+"
    elif grouping == "five_year":
        band = "19-24" if age < 25 else "80+" if age >= 80 else f"{int(age)//5*5}-{int(age)//5*5+4}"
    else:
        raise ValueError("UNSUPPORTED_GROUPING")
    return f"{band}:{int(sex)}"


def build_reference(rows, policy, model_binding, version, provenance):
    if provenance.get("predictionSource") not in ("oof", "held_out", "synthetic"):
        raise ValueError("OOF_OR_HELDOUT_REQUIRED")
    cells = {}
    for row in rows:
        key = group_key(row["age_years"], row["sex_code"], policy["grouping"])
        if key is None:
            raise ValueError("INVALID_REFERENCE_DEMOGRAPHICS")
        if policy["cohort"] == "common" and any(row.get(k) is None for k in CORE):
            continue
        for component in CORE:
            score = row.get(component)
            if score is None:
                continue
            cell = cells.setdefault((component, key), ([], []))
            cell[0].append(score)
            cell[1].append(row["examination_weight"] if policy["weighting"] == "examination_weight" else 1.)
    return {"schemaVersion": "peer-reference-v0.1", "referenceVersion": version,
            "modelBinding": model_binding, "grouping": policy["grouping"],
            "weighting": policy["weighting"], "cohort": policy["cohort"], "provenance": provenance,
            "components": {c: {g: distribution(*v) for (k, g), v in cells.items() if k == c} for c in CORE}}


def validate_reference(ref, policy, binding):
    if (ref["schemaVersion"] != "peer-reference-v0.1" or ref["modelBinding"] != binding
            or any(ref[k] != policy[k] for k in ("grouping", "weighting", "cohort"))
            or set(ref["components"]) != set(CORE)
            or ref["provenance"].get("predictionSource") not in ("oof", "held_out", "synthetic")):
        raise ValueError("MODEL_REFERENCE_POLICY_MISMATCH")
    for cells in ref["components"].values():
        for group in cells.values():
            validate_distribution(group)
            if ref["weighting"] == "unweighted" and (sum(group["weights"]) != group["n"]
                    or group["effectiveN"] != group["n"] or any(w != int(w) for w in group["weights"])):
                raise ValueError("UNWEIGHTED_COUNT_MISMATCH")
