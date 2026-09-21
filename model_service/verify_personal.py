"""Repeat real inference on synthetic inputs; export only synthetic review fixtures."""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from personal_shap import PersonalShapEngine

parser = argparse.ArgumentParser()
parser.add_argument("--payload", type=Path, required=True)
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
engine = PersonalShapEngine(args.payload)
base = json.loads((args.payload / "01_inference/examples/base_64kg.json").read_text(encoding="utf-8"))
cases = {
    "base": base,
    "weight72": {**base, "weightKg": 72, "inputRevision": "synthetic-weight72"},
    "zero_activity": {
        **base,
        "aerobicModerateMinutes": 0,
        "strengthWeeklyCount": 0,
        "strengthIntensity": None,
        "inputRevision": "synthetic-zero",
    },
    "unknown_days": {**base, "strengthFrequencyUnit": None, "inputRevision": "synthetic-unknown-days"},
    "pregnant": {**base, "sex": "female", "pregnancyStatus": "pregnant", "inputRevision": "synthetic-pregnant"},
}
results = {}
for name, request in cases.items():
    result = engine.compute(request)
    results[name] = result
    (args.out / (name + ".synthetic.json")).write_text(
        json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8"
    )
    print(json.dumps({"case": name, "status": result["status"], "seconds": result.get("elapsed_seconds")}), flush=True)
assert results["base"]["status"] == results["weight72"]["status"] == results["zero_activity"]["status"] == "ready"
assert results["unknown_days"]["status"] == results["pregnant"]["status"] == "unavailable"
assert results["base"]["domains"][0]["contributions"] != results["weight72"]["domains"][0]["contributions"]
for actual, expected in zip(
    results["base"]["domains"],
    json.loads((args.payload / "01_inference/examples/base_64kg.expected.json").read_text(encoding="utf-8"))[
        "components"
    ][1:3],
    strict=True,
):
    assert actual["rank"] == expected["rankDisplay"]["rankApprox"]
    assert abs(actual["output_value"] - expected["absoluteReferenceScore"]) < 1e-9
report = {
    "synthetic_only": True,
    "real_inference": True,
    "cases": len(cases),
    "prediction_and_rank_parity": True,
    "input_change_changes_explanation": True,
    "unsupported_inputs_hidden": True,
    "max_additivity_error": max(
        domain["additivity_error"] for result in results.values() for domain in result["domains"]
    ),
}
(args.out / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("Personal SHAP verification passed", flush=True)
