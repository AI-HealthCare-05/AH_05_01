"""Execute the official nested cross-fitted waist submodel OOF run for
tiers W0/W2 across stability seeds 42/1042/2042, against the authorized
2019-2021 development task frame and fold registry.

Writes OOF predictions, aggregate metrics, and a SHA-256 manifest under
artifacts/model_development_v0_1/waist/. Never overwrites existing output
version directories. Never prints row-level values.
"""

from __future__ import annotations

import hashlib
import json
import sys
import time
from pathlib import Path

import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.waist_submodel_pipeline import INPUT_TIERS, evaluate_oof, run_nested_oof

SEEDS = [42, 1042, 2042]
TASK = "waist"
LABEL = "waist_cm"


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main(task_frame_root: Path, fold_root: Path, output_root: Path) -> None:
    if output_root.exists() and any(output_root.iterdir()):
        raise SystemExit(f"output_root already exists and is non-empty, refusing to overwrite: {output_root}")
    output_root.mkdir(parents=True, exist_ok=True)
    oof_dir = output_root / "oof"
    oof_dir.mkdir(parents=True, exist_ok=True)

    task_df = pd.read_csv(task_frame_root / TASK / "development_2019_2021.csv")

    results = {"task": TASK, "tiers": {}}
    output_hashes = {}

    for tier_name, feature_cols in INPUT_TIERS.items():
        results["tiers"][tier_name] = {}
        for seed in SEEDS:
            t0 = time.time()
            seed_dir = fold_root / TASK / f"seed_{seed}"
            outer_df = pd.read_csv(seed_dir / "outer_fold_registry.csv")
            inner_df = pd.read_csv(seed_dir / "inner_fold_registry.csv")

            oof_df, selections = run_nested_oof(task_df, outer_df, inner_df, feature_cols, LABEL)
            metrics = evaluate_oof(oof_df, task_df, LABEL)
            elapsed = time.time() - t0

            out_path = oof_dir / f"{tier_name}_seed{seed}.csv"
            oof_df.to_csv(out_path, index=False)
            output_hashes[str(out_path.relative_to(output_root))] = sha256_of(out_path)

            selection_summary = [
                {
                    "outer_fold": s.outer_fold,
                    "candidate": s.candidate,
                    "inner_cv_mae": s.inner_cv_mae,
                    "inner_cv_rmse": s.inner_cv_rmse,
                }
                for s in selections
            ]

            results["tiers"][tier_name][f"seed_{seed}"] = {
                "metrics": metrics,
                "outer_fold_selections": selection_summary,
                "elapsed_sec": elapsed,
            }
            print(
                f"[done] tier={tier_name} seed={seed} n={metrics['n']} "
                f"mae_cm={metrics['mae_cm']:.4f} rmse_cm={metrics['rmse_cm']:.4f} "
                f"elapsed_sec={elapsed:.1f}",
                flush=True,
            )

    results_path = output_root / "waist_oof_results.json"
    with open(results_path, "w", encoding="utf-8") as fh:
        json.dump(results, fh, indent=2)
    output_hashes[str(results_path.relative_to(output_root))] = sha256_of(results_path)

    manifest_path = output_root / "manifest_sha256.json"
    with open(manifest_path, "w", encoding="utf-8") as fh:
        json.dump(output_hashes, fh, indent=2)

    print("ALL_DONE", flush=True)


if __name__ == "__main__":
    tf_root = Path(sys.argv[1])
    fr_root = Path(sys.argv[2])
    out_root = Path(sys.argv[3])
    main(tf_root, fr_root, out_root)
