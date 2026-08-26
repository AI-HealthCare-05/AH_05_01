"""Materialize the audited KNHANES 2019-2021 canonical development snapshot.

The command accepts exactly three explicitly mapped source files, verifies them against
the prior aggregate audit receipt, applies the frozen row-preserving canonical ETL, and
writes a new non-overwriting package.  It does not filter cohorts, split, train, or score.
"""

from __future__ import annotations

import argparse
import json
import shutil
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

import pandas as pd

from .audit_raw_schema import parse_year_file
from .audit_raw_values import read_value_columns
from .canonical_etl import REQUIRED_CANONICAL_INPUT_COLUMNS, build_canonical_frame
from .experiment_setup import load_authorized_config
from .file_integrity import sha256_file, verify_unchanged


DEVELOPMENT_YEARS = (2019, 2020, 2021)
KEY_COLUMNS = ["source_year", "participant_id"]
IMPLEMENTATION_VERSION = "v0.1"


def load_audit_receipt(path: Path) -> dict[str, Any]:
    receipt = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(receipt, Mapping):
        raise ValueError("Canonical audit receipt must be a JSON object.")
    if receipt.get("policy", {}).get("model_training_performed") is not False:
        raise ValueError("Canonical audit receipt training policy is invalid.")
    sources = receipt.get("sources")
    if not isinstance(sources, Mapping):
        raise ValueError("Canonical audit receipt has no sources mapping.")
    for year in DEVELOPMENT_YEARS:
        source = sources.get(str(year))
        if not isinstance(source, Mapping) or not source.get("sha256"):
            raise ValueError(f"Canonical audit receipt is missing source year {year}.")
    return dict(receipt)


def validate_year_files(
    year_files: Sequence[tuple[int, Path]], receipt: Mapping[str, Any]
) -> dict[int, Path]:
    supplied: dict[int, Path] = {}
    for year, path in year_files:
        if year not in DEVELOPMENT_YEARS:
            raise ValueError(f"Only 2019-2021 are allowed; received year {year}.")
        if year in supplied:
            raise ValueError(f"Duplicate source mapping for year {year}.")
        resolved = path.resolve()
        if not resolved.is_file():
            raise FileNotFoundError(f"Raw source does not exist: {resolved}")
        supplied[year] = resolved
    missing = sorted(set(DEVELOPMENT_YEARS) - set(supplied))
    if missing:
        raise ValueError(f"All development years are required; missing {missing}.")
    for year, path in supplied.items():
        expected = receipt["sources"][str(year)]["sha256"]
        actual = sha256_file(path)
        if actual != expected:
            raise ValueError(
                f"Raw source hash mismatch for {year}; the file is not the audited snapshot."
            )
    return supplied


def materialize_development_canonical(
    config_path: Path,
    year_files: Sequence[tuple[int, Path]],
    output_root: Path,
) -> Path:
    """Write one canonical snapshot plus a no-row-example integrity manifest."""

    config_path = config_path.resolve()
    config = load_authorized_config(config_path)
    materialization = config.get("canonical_materialization")
    if not isinstance(materialization, Mapping):
        raise ValueError("Authorized config has no canonical_materialization section.")
    receipt_path = (Path(__file__).resolve().parents[1] / materialization["audit_receipt"]).resolve()
    receipt = load_audit_receipt(receipt_path)
    supplied = validate_year_files(year_files, receipt)
    source_hashes = {year: sha256_file(path) for year, path in supplied.items()}
    config_hash = sha256_file(config_path)
    receipt_hash = sha256_file(receipt_path)

    version = str(materialization["output_version"])
    output_root = output_root.resolve()
    final = output_root / version
    if final.exists():
        raise FileExistsError(f"Canonical output exists; overwrite is prohibited: {final}")
    output_root.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix=f".{version}-", dir=output_root))
    try:
        frames: list[pd.DataFrame] = []
        source_manifest: dict[str, dict[str, Any]] = {}
        for year in DEVELOPMENT_YEARS:
            raw, metadata = read_value_columns(
                supplied[year], set(REQUIRED_CANONICAL_INPUT_COLUMNS), set()
            )
            canonical = build_canonical_frame(raw, year=year)
            del raw
            frames.append(canonical)
            source_manifest[str(year)] = {
                "filename": supplied[year].name,
                "sha256": source_hashes[year],
                "file_format": metadata.get("file_format"),
                "encoding": metadata.get("encoding"),
                "rows": int(len(canonical)),
            }
        combined = pd.concat(frames, ignore_index=True)
        if combined[KEY_COLUMNS].isna().any().any() or combined.duplicated(KEY_COLUMNS).any():
            raise ValueError("Combined canonical key is missing or duplicated.")
        if set(combined["source_year"].astype(int).unique()) != set(DEVELOPMENT_YEARS):
            raise ValueError("Combined canonical contains an unexpected or missing year.")

        relative = Path("canonical_2019_2021.csv")
        combined.to_csv(stage / relative, index=False, encoding="utf-8-sig", lineterminator="\n")
        manifest = {
            "schema_version": 1,
            "implementation_version": IMPLEMENTATION_VERSION,
            "output_version": version,
            "sources": source_manifest,
            "audit_receipt": {"path": receipt_path.name, "sha256": receipt_hash},
            "authorized_config_sha256": config_hash,
            "canonical_output": {
                "filename": relative.as_posix(),
                "rows": int(len(combined)),
                "sha256": sha256_file(stage / relative),
            },
            "policy": {
                "row_preserving": True,
                "cohort_filtering_performed": False,
                "imputation_performed": False,
                "splitting_performed": False,
                "training_performed": False,
                "evaluation_performed": False,
                "reserved_year_accessed": False,
                "row_examples_in_manifest": False,
            },
        }
        (stage / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        for year, path in supplied.items():
            verify_unchanged(path, source_hashes[year])
        verify_unchanged(config_path, config_hash)
        verify_unchanged(receipt_path, receipt_hash)
        stage.replace(final)
    except BaseException:
        shutil.rmtree(stage, ignore_errors=True)
        raise
    return final


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument(
        "--year-file",
        action="append",
        required=True,
        metavar="YEAR=PATH",
        help="Repeat exactly for 2019, 2020, and 2021.",
    )
    parser.add_argument("--output-root", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    year_files = [parse_year_file(value) for value in args.year_file]
    final = materialize_development_canonical(args.config, year_files, args.output_root)
    print(f"Canonical development snapshot created: {final}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
