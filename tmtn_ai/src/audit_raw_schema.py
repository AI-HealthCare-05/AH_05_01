"""Schema-only audit for KNHANES raw files without participant-value inspection."""

from __future__ import annotations

import argparse
import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

import pandas as pd
import yaml

from .file_integrity import sha256_file


LOGGER = logging.getLogger(__name__)
SUPPORTED_SUFFIXES = {".csv", ".sas7bdat", ".xpt", ".sav"}


@dataclass(frozen=True)
class SchemaMetadata:
    """Non-value metadata read from a source file."""

    columns: tuple[str, ...]
    file_format: str
    encoding: str | None = None


def _load_pyreadstat() -> Any:
    """Import the optional binary-statistics reader with an actionable error."""
    try:
        import pyreadstat  # type: ignore[import-not-found]
    except ImportError as exc:
        raise RuntimeError(
            "Reading SAS/SPSS metadata requires pyreadstat. Install project requirements "
            "in the active virtual environment and rerun."
        ) from exc
    return pyreadstat


def read_schema_metadata(path: Path) -> SchemaMetadata:
    """Read column names only; never materialize participant rows or values."""
    path = path.resolve()
    if not path.is_file():
        raise FileNotFoundError(f"Raw data file does not exist: {path}")
    suffix = path.suffix.lower()
    if suffix not in SUPPORTED_SUFFIXES:
        raise ValueError(
            f"Unsupported raw file format '{suffix}'. Supported formats: "
            f"{', '.join(sorted(SUPPORTED_SUFFIXES))}."
        )

    if suffix == ".csv":
        errors: list[str] = []
        for encoding in ("utf-8-sig", "cp949"):
            try:
                header = pd.read_csv(path, encoding=encoding, nrows=0)
            except (UnicodeError, pd.errors.ParserError) as exc:
                errors.append(f"{encoding}: {exc}")
                continue
            return SchemaMetadata(
                columns=tuple(str(column) for column in header.columns),
                file_format="csv",
                encoding=encoding,
            )
        raise ValueError(
            f"Could not read the CSV header for {path.name}. " + " | ".join(errors)
        )

    pyreadstat = _load_pyreadstat()
    if suffix == ".sas7bdat":
        _, metadata = pyreadstat.read_sas7bdat(str(path), metadataonly=True)
        file_format = "sas7bdat"
    elif suffix == ".xpt":
        _, metadata = pyreadstat.read_xport(str(path), metadataonly=True)
        file_format = "xpt"
    else:
        _, metadata = pyreadstat.read_sav(str(path), metadataonly=True)
        file_format = "sav"
    return SchemaMetadata(
        columns=tuple(str(column) for column in metadata.column_names),
        file_format=file_format,
    )


def load_audit_spec(path: Path) -> dict[str, Any]:
    """Load and validate a schema-only variable-audit specification."""
    path = path.resolve()
    if not path.is_file():
        raise FileNotFoundError(f"Audit specification does not exist: {path}")
    try:
        raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (UnicodeError, yaml.YAMLError) as exc:
        raise ValueError(f"Could not read audit specification {path}: {exc}") from exc
    if not isinstance(raw, Mapping):
        raise ValueError("Audit specification root must be a YAML mapping.")
    years = raw.get("years")
    concepts = raw.get("concepts")
    privacy = raw.get("privacy")
    if not isinstance(years, list) or not years or not all(
        isinstance(year, int) and not isinstance(year, bool) for year in years
    ):
        raise ValueError("Audit specification 'years' must be a non-empty integer list.")
    if len(years) != len(set(years)):
        raise ValueError("Audit specification 'years' contains duplicates.")
    if not isinstance(concepts, Mapping) or not concepts:
        raise ValueError("Audit specification 'concepts' must be a non-empty mapping.")
    if not isinstance(privacy, Mapping) or privacy.get("schema_only") is not True:
        raise ValueError("Audit specification must set privacy.schema_only: true.")
    forbidden_switches = [
        "allow_sample_rows",
        "allow_value_counts",
        "allow_missing_rates",
        "allow_descriptive_statistics",
    ]
    unsafe = [key for key in forbidden_switches if privacy.get(key) is not False]
    if unsafe:
        raise ValueError(
            "Schema audit privacy switches must be explicitly false: " + ", ".join(unsafe)
        )
    for name, definition in concepts.items():
        if not isinstance(name, str) or not name:
            raise ValueError("Every concept name must be a non-empty string.")
        if not isinstance(definition, Mapping):
            raise ValueError(f"Concept '{name}' must be a YAML mapping.")
        candidates = definition.get("candidate_columns")
        required = definition.get("required_columns")
        if (candidates is None) == (required is None):
            raise ValueError(
                f"Concept '{name}' must define exactly one of candidate_columns or "
                "required_columns."
            )
        columns = candidates if candidates is not None else required
        if not isinstance(columns, list) or not columns or not all(
            isinstance(column, str) and column for column in columns
        ):
            raise ValueError(f"Concept '{name}' contains invalid column names.")
    return dict(raw)


def audit_concepts(
    columns: Sequence[str], concepts: Mapping[str, Mapping[str, Any]]
) -> dict[str, dict[str, Any]]:
    """Compare schema column names with each configured concept."""
    available = set(columns)
    result: dict[str, dict[str, Any]] = {}
    for name, definition in concepts.items():
        if "required_columns" in definition:
            expected = list(definition["required_columns"])
            present = [column for column in expected if column in available]
            missing = [column for column in expected if column not in available]
            status = "complete" if not missing else "partial" if present else "missing"
            result[name] = {
                "mode": "required_group",
                "status": status,
                "present_columns": present,
                "missing_columns": missing,
                "contract_required": bool(definition.get("contract_required", False)),
            }
        else:
            candidates = list(definition["candidate_columns"])
            present = [column for column in candidates if column in available]
            status = "present" if len(present) == 1 else "ambiguous" if present else "missing"
            result[name] = {
                "mode": "candidate",
                "status": status,
                "present_columns": present,
                "candidate_columns": candidates,
                "contract_required": bool(definition.get("contract_required", False)),
            }
    return result


def parse_year_file(value: str) -> tuple[int, Path]:
    """Parse a YEAR=PATH command-line item."""
    if "=" not in value:
        raise argparse.ArgumentTypeError("Use YEAR=PATH, for example 2019=D:\\data\\HN19_ALL.csv")
    year_text, path_text = value.split("=", 1)
    try:
        year = int(year_text)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"Invalid year in --year-file: {year_text}") from exc
    if not path_text.strip():
        raise argparse.ArgumentTypeError("--year-file path cannot be empty.")
    return year, Path(path_text)


def audit_schema_files(
    spec_path: Path,
    year_files: Sequence[tuple[int, Path]],
    output_path: Path,
    *,
    allow_partial: bool = False,
    force: bool = False,
) -> Path:
    """Audit schemas and write a deterministic JSON report containing no row values."""
    spec_path = spec_path.resolve()
    output_path = output_path.resolve()
    if output_path.exists() and not force:
        raise FileExistsError(
            f"Audit output already exists: {output_path}. Use --force only for an intentional replacement."
        )
    spec = load_audit_spec(spec_path)
    expected_years = set(spec["years"])
    supplied: dict[int, Path] = {}
    for year, path in year_files:
        if year in supplied:
            raise ValueError(f"Duplicate --year-file entry for {year}.")
        if year not in expected_years:
            raise ValueError(f"Year {year} is not allowed by the audit specification.")
        supplied[year] = path.resolve()
    missing_years = sorted(expected_years - set(supplied))
    if missing_years and not allow_partial:
        raise ValueError(
            f"Missing files for configured years: {missing_years}. Supply every year or use "
            "--allow-partial for an incremental schema audit."
        )
    if not supplied:
        raise ValueError("At least one --year-file entry is required.")
    if output_path in supplied.values():
        raise ValueError("Audit output cannot overwrite a raw input file.")

    source_reports: dict[str, dict[str, Any]] = {}
    for year in sorted(supplied):
        source_path = supplied[year]
        metadata = read_schema_metadata(source_path)
        source_reports[str(year)] = {
            "filename": source_path.name,
            "sha256": sha256_file(source_path),
            "file_format": metadata.file_format,
            "encoding": metadata.encoding,
            "column_count": len(metadata.columns),
            "columns": list(metadata.columns),
            "concepts": audit_concepts(metadata.columns, spec["concepts"]),
        }

    report: dict[str, Any] = {
        "schema_version": 1,
        "audit_version": spec.get("audit_version"),
        "survey": spec.get("survey"),
        "specification": {
            "filename": spec_path.name,
            "sha256": sha256_file(spec_path),
        },
        "policy": {
            "schema_only": True,
            "participant_rows_read": False,
            "participant_values_included": False,
            "row_counts_included": False,
            "missing_rates_included": False,
            "descriptive_statistics_included": False,
            "holdout_years": list(spec["privacy"].get("holdout_years", [])),
        },
        "missing_configured_years": missing_years,
        "sources": source_reports,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    LOGGER.info("Schema-only audit written to %s", output_path)
    return output_path


def build_argument_parser() -> argparse.ArgumentParser:
    """Build the command-line interface."""
    parser = argparse.ArgumentParser(
        description=(
            "Audit KNHANES column metadata only. The tool never outputs participant rows, "
            "values, distributions, missing rates, or descriptive statistics."
        )
    )
    parser.add_argument("--spec", required=True, type=Path, help="Variable audit YAML path.")
    parser.add_argument(
        "--year-file",
        required=True,
        action="append",
        type=parse_year_file,
        metavar="YEAR=PATH",
        help="Raw file for one year. Repeat for each year.",
    )
    parser.add_argument("--output", required=True, type=Path, help="Schema audit JSON output.")
    parser.add_argument(
        "--allow-partial",
        action="store_true",
        help="Permit an incremental audit when not all configured years are supplied.",
    )
    parser.add_argument("--force", action="store_true", help="Replace the exact output file.")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    """Run the schema-only audit CLI."""
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    args = build_argument_parser().parse_args(argv)
    try:
        audit_schema_files(
            args.spec,
            args.year_file,
            args.output,
            allow_partial=args.allow_partial,
            force=args.force,
        )
    except (FileNotFoundError, FileExistsError, ValueError, RuntimeError) as exc:
        LOGGER.error("%s", exc)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
