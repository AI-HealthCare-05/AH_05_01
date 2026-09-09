"""Refit frozen D0 candidates and export a local integration package (not release)."""
from __future__ import annotations

import argparse
import json
import platform
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import yaml

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from analysis.mvp import build_tuntun_input_frame as io
from analysis.mvp import select_d0_single_pipeline as selection
from src import disease_model_pipeline as dmp
from src import d0_inference as runtime

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "contracts/d0_production_refit_v0_1.yaml"
CONTRACT_HASH = "5279bac2bf09a9392d3a343612510a3f9e132854279714546011b44c4cc8e606"
SELECTION = ROOT / "artifacts/model_development_v0_1/d0_single_pipeline_selection_v0_1"
COUNTS = {"diabetes": 15720, "hypertension": 16325}
FEATURES = runtime.FEATURES
KEYS = dmp.KEY_COLS


class RefitError(ValueError):
    pass


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True, allow_nan=False) + "\n", encoding="utf-8")


def load_contract(path: Path) -> dict:
    if runtime.sha256_file(path) != CONTRACT_HASH:
        raise RefitError("refit contract differs from frozen implementation contract")
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def candidates() -> dict:
    result = {}
    for task, leaf in (("diabetes", 5), ("hypertension", 50)):
        matches = [c for c in dmp.candidate_grid() if c["family"] == "random_forest_classifier"
                   and c["params"]["max_depth"] == 8 and c["params"]["min_samples_leaf"] == leaf
                   and c["params"]["max_features"] == "sqrt" and c["params"]["class_weight"] is None]
        if len(matches) != 1:
            raise RefitError("frozen candidate not uniquely resolved")
        result[task] = matches[0]
    return result


def selection_evidence() -> tuple[dict, dict]:
    manifest = json.loads((SELECTION / "manifest_sha256.json").read_text(encoding="utf-8"))
    for name, digest in manifest.items():
        path = (SELECTION / name).resolve()
        if path.parent != SELECTION.resolve() or runtime.sha256_file(path) != digest:
            raise RefitError("frozen selection output integrity failure")
    selected = json.loads((SELECTION / "selected_combinations.json").read_text(encoding="utf-8"))
    for task, candidate in candidates().items():
        if selected[task]["candidate"] != dmp.candidate_name(candidate) or selected[task]["calibrator"] != "platt":
            raise RefitError("refit candidate differs from frozen selection")
    return selected, json.loads((SELECTION / "validation_summary.json").read_text(encoding="utf-8"))


def validate_training_frame(frame: pd.DataFrame, registry: pd.DataFrame, task: str,
                            expected_rows: int) -> pd.DataFrame:
    label = selection.TASKS[task]
    if len(frame) != expected_rows or len(registry) != expected_rows:
        raise RefitError("unexpected task/registry row count")
    for data in (frame, registry):
        if not set(KEYS) <= set(data.columns) or data[KEYS].isna().any().any() or data.duplicated(KEYS).any():
            raise RefitError("missing or duplicate keys")
        ids = data["participant_id"]
        if not ids.map(lambda x: isinstance(x, str) and bool(x.strip()) and x == x.strip()).all():
            raise RefitError("invalid identifier format")
        # Also prevent a participant from crossing folds under a second year key.
        if ids.duplicated().any():
            raise RefitError("participant occurs more than once")
        if not pd.api.types.is_numeric_dtype(data["source_year"]) or not data["source_year"].isin([2019, 2020, 2021]).all():
            raise RefitError("forbidden or invalid source year")
    if set(frame[KEYS].itertuples(index=False, name=None)) != set(registry[KEYS].itertuples(index=False, name=None)):
        raise RefitError("task and fold keys differ")
    folds = registry["outer_fold"]
    if folds.map(lambda x: isinstance(x, (bool, np.bool_))).any() or not pd.api.types.is_numeric_dtype(folds) or set(folds) != set(range(5)):
        raise RefitError("folds must be exactly integers 0..4")
    prepared = runtime.prepare_features(frame[FEATURES])
    if (frame["age_years"] > 80).any():
        raise RefitError("training age must already be top-coded")
    if not pd.api.types.is_numeric_dtype(frame[label]) or not frame[label].isin([0, 1]).all():
        raise RefitError("invalid binary labels")
    dmp._assert_both_classes_present(frame[label], "full_development")
    result = frame[KEYS + [label]].copy()
    result[FEATURES] = prepared
    result = result.merge(registry[KEYS + ["outer_fold"]], on=KEYS, validate="one_to_one", sort=False)
    return result


def fit_task(frame: pd.DataFrame, task: str, candidate: dict) -> tuple:
    label = selection.TASKS[task]
    x, y = frame[FEATURES], frame[label].to_numpy()
    raw = np.full(len(frame), np.nan)
    coverage = np.zeros(len(frame), dtype=int)
    audit = []
    for fold in range(5):
        holdout = frame["outer_fold"].to_numpy() == fold
        train = ~holdout
        if not holdout.any() or not train.any():
            raise RefitError("empty fold")
        fit_ids = set(frame.loc[train, "participant_id"])
        if fit_ids & set(frame.loc[holdout, "participant_id"]):
            raise RefitError("cross-fit participant leakage")
        dmp._assert_both_classes_present(pd.Series(y[train]), "crossfit_train")
        model = dmp.build_pipeline(candidate["family"], candidate["params"])
        model.set_params(model__n_jobs=1)
        model.fit(x.loc[train], y[train])
        raw[holdout] = model.predict_proba(x.loc[holdout])[:, 1]
        coverage[holdout] += 1
        audit.append({"fold": fold, "fit_n": int(train.sum()), "heldout_n": int(holdout.sum()), "overlap": 0})
        print(f"{task}: calibration cross-fit {fold + 1}/5 complete", flush=True)
    if not (coverage == 1).all():
        raise RefitError("crossfit coverage invalid")
    dmp._assert_valid_probabilities(raw, "full_development_crossfit")
    calibrator = dmp.PlattCalibrator().fit(raw, y)
    model = dmp.build_pipeline(candidate["family"], candidate["params"])
    model.set_params(model__n_jobs=1)
    model.fit(x, y)
    stats = {"n": len(frame), "n_events": int(y.sum()), "crossfit_coverage": int(coverage.sum()),
             "crossfit_fold_audit": audit, "full_refit_n": len(frame),
             "calibrator_coefficient": float(calibrator.lr.coef_[0, 0]),
             "calibrator_intercept": float(calibrator.lr.intercept_[0]),
             "raw_crossfit_brier_descriptive": dmp.brier_score(y, raw),
             "not_independent_validation": True}
    return model, calibrator.lr, stats


def synthetic_cases(models: dict) -> list:
    cases = []
    for values in ((45, 1, 172, 75, 150, 2), (68, 2, 155, 62, 0, 0),
                   (19, 1, 180, 80, 300, 5), (90, 2, 160, 60, 150, 3)):
        features = dict(zip(FEATURES, values, strict=True))
        prepared = runtime.prepare_features(pd.DataFrame([features], columns=FEATURES))
        expected = {"supported_input": True, "age_top_coded_80_plus": values[0] >= 80}
        for task in runtime.TASKS:
            _, probability = runtime.calibrated_probability(*models[task], prepared)
            expected[f"{task}_score"] = float(100 * (1 - probability[0]))
            expected[f"{task}_available"] = True
        cases.append({"features": features, "pregnancy_status": "nonpregnant", "expected": expected})
    for pregnancy in ("pregnant", "unknown"):
        cases.append({"features": dict(cases[0]["features"]), "pregnancy_status": pregnancy,
            "expected": {"supported_input": False, "diabetes_score": None, "hypertension_score": None}})
    return cases


def publish(stage: Path, target: Path) -> None:
    io._apply_best_effort_posix_permissions(stage)
    io._fix_windows_acl_inheritance(stage)
    io._verify_final_files_readable(stage, [str(p.relative_to(stage)) for p in stage.rglob("*") if p.is_file()])
    if target.exists():
        raise RefitError("output appeared before commit")
    stage.rename(target)


def run(args) -> dict:
    if args.output_root.exists():
        raise RefitError("output root must not exist, even if empty")
    contract = load_contract(args.contract)
    selected, evidence = selection_evidence()
    paths = {"refit_contract": args.contract, "selection_contract": ROOT / selection.CONTRACT_REL,
             "selection_summary": SELECTION / "validation_summary.json",
             "selection_result": SELECTION / "selected_combinations.json",
             "selection_manifest": SELECTION / "manifest_sha256.json",
             "review": ROOT / "incoming/jihyun_d0_independent_review_2026-09-04/REVIEW_RESPONSE_TEMPLATE.md",
             "trainer": Path(__file__), "runtime": Path(runtime.__file__), "disease_pipeline": Path(dmp.__file__),
             "selector": Path(selection.__file__), "io_helpers": Path(io.__file__)}
    frames = {}
    for task in runtime.TASKS:
        paths[f"task_frame/{task}"] = args.task_frame_root / task / "development_2019_2021.csv"
        paths[f"fold/{task}/42/outer"] = args.fold_root / task / "seed_42/outer_fold_registry.csv"
    hashes = {key: runtime.sha256_file(path) for key, path in paths.items()}
    for task in runtime.TASKS:
        for key in (f"task_frame/{task}", f"fold/{task}/42/outer"):
            if hashes[key] != evidence["inputs"][key]:
                raise RefitError("input bytes differ from selected baseline")
        frames[task] = validate_training_frame(pd.read_csv(paths[f"task_frame/{task}"]),
            pd.read_csv(paths[f"fold/{task}/42/outer"]), task, COUNTS[task])
    summary = {"status": "PREFLIGHT_PASS", "production_release_gate": "BLOCKED", "inputs_sha256": hashes,
               "training_environment": {**runtime.environment(), "PyYAML": yaml.__version__, "platform": platform.platform()},
               "generated_at_utc": datetime.now(timezone.utc).isoformat(), "tasks": {}}
    if args.dry_run:
        return {"status": "PREFLIGHT_PASS", "rows": COUNTS, "files_written": False}
    models = {}
    for task, candidate in candidates().items():
        model, calibrator, task_summary = fit_task(frames[task], task, candidate)
        models[task] = (model, calibrator)
        summary["tasks"][task] = task_summary
    args.output_root.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".d0_refit_", dir=args.output_root.parent) as temp:
        stage = Path(temp)
        for task, (model, calibrator) in models.items():
            joblib.dump(model, stage / f"{task}_model.joblib", compress=3)
            joblib.dump(calibrator, stage / f"{task}_platt.joblib", compress=3)
            loaded = (joblib.load(stage / f"{task}_model.joblib"), joblib.load(stage / f"{task}_platt.joblib"))
            before = runtime.calibrated_probability(model, calibrator, frames[task][FEATURES])
            after = runtime.calibrated_probability(*loaded, frames[task][FEATURES])
            differences = [float(np.max(np.abs(a - b))) for a, b in zip(before, after, strict=True)]
            if max(differences) > 1e-10:
                raise RefitError("all-row serialization parity failure")
            summary["tasks"][task]["serialization_max_abs_diff_raw_calibrated"] = differences
        shutil.copyfile(runtime.__file__, stage / "d0_inference.py")
        shutil.copyfile(args.contract, stage / "refit_contract.yaml")
        source = stage / "provenance"
        source.mkdir()
        for key in ("trainer", "selector", "disease_pipeline", "io_helpers", "review", "selection_contract", "selection_result", "selection_summary", "selection_manifest"):
            shutil.copyfile(paths[key], source / paths[key].name)
        metadata = {"model_version": runtime.VERSION, "schema_version": runtime.SCHEMA["version"],
            "calibration_version": "d0_platt_crossfit42_v0_1", "environment": runtime.environment(),
            "selected_combinations": selected, "candidate_parameters": candidates(),
            "effective_n_jobs": 1, "calibration_fold_seed": 42, "production_release_gate": "BLOCKED",
            "status": "LOCAL_BACKEND_INTEGRATION_CANDIDATE", "score_formula": "100*(1-calibrated_probability)"}
        write_json(stage / "metadata.json", metadata)
        write_json(stage / "input_schema.json", runtime.SCHEMA)
        write_json(stage / "smoke_cases.json", synthetic_cases(models))
        (stage / "requirements.txt").write_text("\n".join(f"{k}=={v}" for k, v in runtime.environment().items() if k != "python") + "\n", encoding="utf-8")
        (stage / "README.md").write_text(
            "# D0 local backend integration candidate\n\n"
            "Live release remains BLOCKED. Includes diabetes and hypertension only.\n"
            "Use the exact Python and dependency versions in metadata.json.\n"
            "Verify the trusted manifest digest supplied separately before loading joblib files.\n"
            "Run: python d0_inference.py --bundle . --manifest-sha256 <trusted digest>\n"
            "Python: D0Predictor(Path(bundle), trusted_digest).score(canonical_features, pregnancy_status='nonpregnant')\n"
            "The runtime returns only two scores and availability/version metadata.\n"
            "Canonical six-feature input semantics are in input_schema.json. No sensor conversion or API/DB mapping is included.\n"
            "5 means 5+ strength days; age >=80 is top-coded to 80. Missing exercise is unavailable, not zero.\n"
            "Backend must attach its own input_snapshot_id and apply approved cohort/safety wording and eligibility.\n"
            "Cross-fitted predictions train Platt; calibrated values on that same sample are NOT independent validation.\n"
            "Original selection provenance limits remain; provenance contains current sources, not a reconstruction of the past environment.\n",
            encoding="utf-8")
        summary["status"] = "REFIT_SERIALIZATION_VERIFIED"
        summary["row_level_outputs_written"] = False
        summary["independent_performance_evaluation_performed"] = False
        write_json(stage / "validation_summary.json", summary)
        selection.validate_no_pii_text(stage)
        if any(runtime.sha256_file(paths[key]) != digest for key, digest in hashes.items()):
            raise RefitError("source input changed during training")
        write_json(stage / "manifest.json", {"files": {str(p.relative_to(stage)).replace("\\", "/"): runtime.sha256_file(p)
            for p in sorted(stage.rglob("*")) if p.is_file()}})
        digest = runtime.sha256_file(stage / "manifest.json")
        runtime.verify_package(stage, digest)
        # Real standalone subprocess, without repository imports or PYTHONPATH.
        proc = subprocess.run([sys.executable, str(stage / "d0_inference.py"), "--bundle", str(stage),
            "--manifest-sha256", digest], cwd=stage, capture_output=True, text=True)
        if proc.returncode != 0 or json.loads(proc.stdout).get("status") != "PASS":
            raise RefitError("fresh-process package smoke test failed")
        publish(stage, args.output_root)
    return {"status": "LOCAL_BACKEND_INTEGRATION_CANDIDATE", "manifest_sha256": digest,
            "fresh_process_smoke": "PASS", "production_release_gate": "BLOCKED", "rows": COUNTS}


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task-frame-root", type=Path, default=ROOT / "data/cohort_rebuild_20260830/task_frames/development_v0_2")
    parser.add_argument("--fold-root", type=Path, default=ROOT / "data/cohort_rebuild_20260830/folds/d0_single_selection_rebuilt/development_v0_2")
    parser.add_argument("--contract", type=Path, default=CONTRACT)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--dry-run", action="store_true")
    return parser


def main():
    try:
        print(json.dumps(run(build_parser().parse_args()), sort_keys=True))
    except Exception as exc:
        # Avoid echoing corrupt raw values or source identifiers in errors.
        print(json.dumps({"status": "FAIL", "error_type": type(exc).__name__}), file=sys.stderr)
        raise SystemExit(2) from None


if __name__ == "__main__":
    main()
