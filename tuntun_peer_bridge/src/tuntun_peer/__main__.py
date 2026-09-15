"""Run from review_src / tmtn_ai: python -m src.tuntun_peer --help."""
import argparse
import json
from pathlib import Path
from .bundle import load_bundle, validate_bundle, read, write
from .reference import build_reference
from .service import legacy_score

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("validate", "infer", "legacy", "compat", "smoke"):
        sub = commands.add_parser(name)
        sub.add_argument("--bundle", type=Path, required=True)
        sub.add_argument("--manifest-sha256", required=True)
        if name in ("infer", "legacy"):
            sub.add_argument("--request", type=Path, required=True)
    sub = commands.add_parser("build-reference")
    sub.add_argument("--input", type=Path, required=True, help="Internal JSON: rows, policy, modelBinding, version, provenance")
    sub.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "build-reference":
        if args.output.exists():
            raise ValueError("OUTPUT_EXISTS")
        data = read(args.input)
        write(args.output, build_reference(data["rows"], data["policy"], data["modelBinding"], data["version"], data["provenance"]))
        return {"status": "REFERENCE_BUILT_LOCAL_ONLY"}
    if args.command == "validate":
        validate_bundle(args.bundle, args.manifest_sha256)
        return {"status": "PASS", "production": "NOT_APPROVED"}
    service = load_bundle(args.bundle, args.manifest_sha256)
    if args.command == "infer":
        request = read(args.request)
        return service.batch(request) if isinstance(request, list) else service.score(request)
    if args.command == "legacy":
        return legacy_score(service.adapter.predictor, read(args.request))
    cases = read(args.bundle/"legacy/smoke_cases.json")
    rejected = 0
    for case in cases:
        if legacy_score(service.adapter.predictor, case["request"]) != case["expected"]:
            raise ValueError("LEGACY_GOLDEN_REGRESSION")
        if args.command == "smoke":
            if case["request"]["features"].get("sex_code") == 1 and case["request"]["pregnancy_status"] == "pregnant":
                try:
                    service.score(case["request"])
                except ValueError as exc:
                    if str(exc) != "CONTRADICTORY_PREGNANCY_STATUS":
                        raise
                    rejected += 1
                else:
                    raise ValueError("EXPECTED_CONTRADICTION_REJECTION")
            else:
                assert service.batch([case["request"]])[0] == service.score(case["request"])
    return {"status": "PASS", "legacyGoldenCases": len(cases), "vNextExpectedRejections": rejected, "syntheticInputs": True}

if __name__ == "__main__":
    try:
        print(json.dumps(main(), ensure_ascii=True, allow_nan=False))
    except Exception as exc:
        print(json.dumps({"status": "FAIL", "error": type(exc).__name__}))
        raise SystemExit(2)
