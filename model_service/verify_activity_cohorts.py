"""Exercise every broad age/sex group through the real model, using invented inputs."""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from personal_shap import PersonalShapEngine  # noqa: E402 - embedded runtime does not add the script directory


def verify(payload, output):
    engine = PersonalShapEngine(payload)
    base = json.loads((payload / "01_inference/examples/base_64kg.json").read_text(encoding="utf-8"))
    output.mkdir(parents=True, exist_ok=True)
    results = []
    for age, band in ((35, "19-39"), (55, "40-64"), (70, "65+")):
        for code, sex in ((1, "male"), (2, "female")):
            key = f"{band}:{code}"
            request = {
                **base,
                "birthYear": 2026 - age,
                "birthMonth": 1,
                "referenceDate": "2026-09-15",
                "sex": sex,
                "pregnancyStatus": "nonpregnant" if sex == "female" else "not_applicable",
                "heightCm": 170 if sex == "male" else 160,
                "weightKg": 64,
                "strengthWeeklyCount": 0 if age == 70 else 4,
                "strengthIntensity": None if age == 70 else "moderate",
                "strengthFrequencyUnit": "days",
                "aerobicModerateMinutes": 0 if age == 70 else 120,
                "aerobicVigorousMinutes": 0,
                "inputRevision": f"synthetic-cohort-{age}-{sex}",
            }
            snapshot = engine.compute(request)
            assert snapshot["status"] == "ready"
            activity = snapshot["activity_comparison"]
            assert activity["status"] == "ready" and activity["group_key"] == key
            assert activity["input_revision"] == request["inputRevision"]
            assert all(d["reference_group"] == key and d["additivity_error"] <= 1e-9 for d in snapshot["domains"])
            assert all(c["n"] >= 30 for c in activity["cards"])
            result = {
                "group": key,
                "ready": True,
                "seconds": snapshot["elapsed_seconds"],
                "valid_response_counts": {c["key"]: c["n"] for c in activity["cards"]},
                "maximum_additivity_error": max(d["additivity_error"] for d in snapshot["domains"]),
            }
            results.append(result)
            (output / f"{age}-{sex}.synthetic.json").write_bytes(
                (json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n").encode()
            )
            print(json.dumps(result), flush=True)
    (output / "verification.json").write_bytes(
        (json.dumps({"synthetic_only": True, "real_model": True, "results": results}, indent=2) + "\n").encode()
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    verify(args.payload, args.output)
