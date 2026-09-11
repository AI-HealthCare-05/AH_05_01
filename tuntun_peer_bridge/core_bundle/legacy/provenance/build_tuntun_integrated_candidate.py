"""Build W2 refit + frozen empirical CDF + unchanged D0 + questionnaire/V2 runtime."""
from __future__ import annotations
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import yaml

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from analysis.mvp import refit_d0_production as refit
from analysis.mvp import build_tuntun_input_frame as core
from src import waist_submodel_pipeline as waist
from src import tuntun_inference as runtime
from src import d0_inference as d0

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "contracts/tuntun_integrated_candidate_v0_1.yaml"
CONTRACT_HASH = "37c5d32b3b462ba4ca56d06f227ab3c03d20062875b0c3a8259f55d8fffbbb2e"
D0_ROOT = ROOT / "artifacts/production_candidates/d0_refit_v0_1_20260904"
EXPECTED_CANDIDATE = "random_forest_regressor(max_depth=12,min_samples_leaf=5,max_features=0.7)"


def validate_waist(frame, oof, expected_rows=16403):
    keys = core.KEY_COLUMNS
    if len(frame) != expected_rows or len(oof) != expected_rows:
        raise refit.RefitError("waist rows mismatch")
    for item in (frame, oof):
        core.validate_id_column(item["participant_id"], "waist keys")
        core.validate_strict_integer(item["source_year"], "waist years", {2019, 2020, 2021})
        if item.duplicated(keys).any() or item["participant_id"].duplicated().any():
            raise refit.RefitError("duplicate waist keys")
    core.assert_exact_key_match(frame, oof, "waist", 42)
    d0.prepare_features(frame[d0.FEATURES])
    if (frame.age_years > 80).any():
        raise refit.RefitError("training age not top-coded")
    core.validate_finite_numeric(frame["waist_cm"], "waist label")
    if not (frame.waist_cm > 0).all():
        raise refit.RefitError("invalid waist label")
    if set(oof["waist_selected_candidate"]) != {EXPECTED_CANDIDATE}:
        raise refit.RefitError("OOF candidate differs from frozen W2")
    core.validate_strict_integer(oof["waist_outer_fold"], "waist folds", set(range(5)))
    if set(oof["waist_outer_fold"]) != set(range(5)):
        raise refit.RefitError("incomplete OOF folds")
    core.validate_finite_numeric(oof["waist_oof_pred_cm"], "waist prediction")
    return frame.merge(oof[keys + ["waist_oof_pred_cm", "waist_outer_fold"]], on=keys, validate="one_to_one")


def build_reference(frame):
    residual = frame.waist_cm.to_numpy() - frame.waist_oof_pred_cm.to_numpy()
    groups = np.asarray([runtime.group_key(a,s) for a,s in zip(frame.age_years,frame.sex_code)])
    reference = {}
    for key in runtime.GROUPS:
        values, counts = np.unique(residual[groups == key], return_counts=True)
        reference[key] = {"values": values.tolist(), "counts": counts.tolist(), "n": int(counts.sum())}
    runtime.validate_reference(reference)
    return reference


def requests():
    base = dict(zip(d0.FEATURES, [45,1,172,75,150,2]))
    cases = [base, {**base,"age_years":68,"sex_code":2}, {**base,"age_years":90},
        {d0.FEATURES[4]:0,d0.FEATURES[5]:0}, {d0.FEATURES[4]:150}, {},
        {**base,"height_cm":None}, {**base,"strength_days_week":None}]
    result = [{"features":f,"pregnancy_status":"nonpregnant","activity_window_end":"2026-09-04","recorded_days":0} for f in cases]
    result += [{"features":base,"pregnancy_status":p,"activity_window_end":"2026-09-04","recorded_days":0} for p in ("pregnant","unknown")]
    return result


def run(args):
    if args.output_root.exists():
        raise refit.RefitError("output exists")
    if d0.sha256_file(CONTRACT) != CONTRACT_HASH:
        raise refit.RefitError("integrated contract changed")
    contract = yaml.safe_load(CONTRACT.read_text(encoding="utf-8"))
    paths = {"contract": CONTRACT, "trainer": Path(__file__), "runtime": Path(runtime.__file__),
        "d0_runtime": Path(d0.__file__), "waist_pipeline": Path(waist.__file__),
        "refit_helpers":Path(refit.__file__), "io_helpers":Path(core.__file__),
        "task_frame":ROOT/"data/cohort_rebuild_20260830/task_frames/development_v0_2/waist/development_2019_2021.csv",
        "oof":ROOT/"artifacts/model_development_v0_1/waist/oof/W2_seed42.csv",
        "receipt":ROOT/"docs/WAIST_OOF_DEVELOPMENT_V0_1_RECEIPT.json",
        "formula":ROOT/"contracts/tuntun_score_formula_v0_2_empirical_cdf_owner_approved.yaml",
        "v2_dto":ROOT.parent/"worktrees/AH_05_01-tuntun-v2/app/dtos/tuntun_score.py"}
    hashes = {k:d0.sha256_file(p) for k,p in paths.items()}
    if (hashes["task_frame"] != contract["waist"]["training_sha256"]
            or hashes["oof"] != contract["waist"]["oof_sha256"]
            or hashes["formula"] != contract["formula"]["source_contract_sha256"]):
        raise refit.RefitError("input or formula hash mismatch")
    receipt = json.loads(paths["receipt"].read_text(encoding="utf-8"))
    if receipt["restricted_artifact_receipts"]["oof/W2_seed42.csv"] != hashes["oof"]:
        raise refit.RefitError("OOF receipt mismatch")
    d0.verify_package(D0_ROOT, runtime.D0_DIGEST)
    frame = validate_waist(pd.read_csv(paths["task_frame"]), core.load_waist_oof(paths["oof"],42))
    reference = build_reference(frame)
    if args.dry_run:
        return {"status":"PREFLIGHT_PASS","waist_n":len(frame),"group_n":{k:v["n"] for k,v in reference.items()},"files_written":False}
    model = waist.build_pipeline("random_forest_regressor", contract["waist"]["params"])
    model.set_params(model__n_jobs=1)
    print("W2 full-development refit started: 16403 rows",flush=True)
    x = d0.prepare_features(frame[d0.FEATURES])
    model.fit(x,frame.waist_cm)
    before = model.predict(x)
    args.output_root.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".tuntun_integrated_",dir=args.output_root.parent) as temp:
        stage=Path(temp)
        joblib.dump(model,stage/"waist_model.joblib",compress=3)
        after=joblib.load(stage/"waist_model.joblib").predict(x)
        error=float(np.max(np.abs(before-after)))
        if error > 1e-10:
            raise refit.RefitError("waist serialization parity failed")
        refit.write_json(stage/"empirical_reference.json",reference)
        reference_back=json.loads((stage/"empirical_reference.json").read_text())
        # CDF storage parity over every development input is serialization QA, not outcome validation.
        cdf_error=max(abs(runtime.physical_probability(p,a,s,reference)-runtime.physical_probability(p,a,s,reference_back))
            for p,a,s in zip(before,frame.age_years,frame.sex_code))
        if cdf_error > 1e-10:
            raise refit.RefitError("CDF serialization mismatch")
        (stage/"d0").mkdir()
        d0_manifest=json.loads((D0_ROOT/"manifest.json").read_text())
        for name in [*d0_manifest["files"],"manifest.json"]:
            target=stage/"d0"/name
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(D0_ROOT/name,target)
        for name,source in (("tuntun_inference.py",runtime.__file__),("d0_inference.py",d0.__file__),
                            ("tuntun_local_service.py",ROOT/"src/tuntun_local_service.py")):
            shutil.copyfile(source,stage/name)
        (stage/"provenance").mkdir()
        for key in ("contract","trainer","waist_pipeline","receipt","formula","v2_dto"):
            shutil.copyfile(paths[key],stage/"provenance"/paths[key].name)
        # Standalone copy for DTO parity testing; the active app checkout remains untouched.
        source=paths["v2_dto"].read_text(encoding="utf-8")
        old='Literal["mock_health_input", "questionnaire", "unavailable"]'
        if source.count(old)!=1:
            raise refit.RefitError("unexpected V2 DTO source shape")
        (stage/"integration").mkdir()
        (stage/"integration/tuntun_score_dto_candidate.py").write_text(source.replace(old,'Literal["mock_health_input", "model_inference", "questionnaire", "unavailable"]'),encoding="utf-8")
        refit.write_json(stage/"metadata.json",{"model_version":runtime.VERSION,"environment":d0.environment(),
            "production_release_gate":"BLOCKED","d0_manifest":runtime.D0_DIGEST,"waist_params":contract["waist"]["params"],
            "waist_n_jobs":1,"reference_group_n":{k:v["n"] for k,v in reference.items()},"input_schema":d0.SCHEMA})
        summary={"waist_n":len(frame),"waist_serialization_max_abs_diff":error,"cdf_serialization_max_abs_diff":cdf_error,
            "reference_group_n":{k:v["n"] for k,v in reference.items()},"input_sha256":hashes,
            "independent_performance_evaluation":False,"production_release_gate":"BLOCKED",
            "raw_rows_or_oof_predictions_exported":False,"reference_contains_anonymous_distribution_parameters":True}
        refit.write_json(stage/"validation_summary.json",summary)
        shutil.copyfile(D0_ROOT/"requirements.txt",stage/"requirements.txt")
        # Build expected synthetic results from in-memory fitted estimators.
        predictor=runtime.TuntunPredictor.__new__(runtime.TuntunPredictor)
        predictor.reference=reference
        predictor.waist=model
        predictor.disease=d0.D0Predictor(D0_ROOT,runtime.D0_DIGEST)
        cases=[{"request":r,"expected":predictor.score(**r)} for r in requests()]
        refit.write_json(stage/"smoke_cases.json",cases)
        refit.selection.validate_no_pii_text(stage)
        if any(d0.sha256_file(paths[k]) != value for k,value in hashes.items()):
            raise refit.RefitError("source changed during build")
        refit.write_json(stage/"manifest.json",{"files":{str(p.relative_to(stage)).replace("\\","/"):d0.sha256_file(p)
            for p in sorted(stage.rglob("*")) if p.is_file()}})
        digest=d0.sha256_file(stage/"manifest.json")
        runtime.verify_bundle(stage,digest)
        proc=subprocess.run([sys.executable,str(stage/"tuntun_inference.py"),"--bundle",str(stage),"--manifest-sha256",digest],cwd=stage,capture_output=True,text=True)
        if proc.returncode!=0 or json.loads(proc.stdout).get("status")!="PASS":
            raise refit.RefitError("integrated fresh-process smoke failed")
        refit.publish(stage,args.output_root)
    return {"status":"INTEGRATED_LOCAL_CANDIDATE","manifest_sha256":digest,"smoke_cases":len(cases),"production_release_gate":"BLOCKED"}


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-root",type=Path,required=True)
    parser.add_argument("--dry-run",action="store_true")
    try:
        print(json.dumps(run(parser.parse_args())))
    except Exception as exc:
        print(json.dumps({"status":"FAIL","error_type":type(exc).__name__}),file=sys.stderr)
        raise SystemExit(2)
