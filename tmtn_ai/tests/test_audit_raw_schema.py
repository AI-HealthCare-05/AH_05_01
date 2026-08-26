"""Synthetic-only tests for the schema-only raw-data audit."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import pytest
import yaml

from src.audit_raw_schema import (
    audit_concepts,
    audit_schema_files,
    load_audit_spec,
    read_schema_metadata,
)


def _spec_payload() -> dict[str, object]:
    return {
        "schema_version": 1,
        "audit_version": "synthetic_v1",
        "survey": "synthetic",
        "years": [2019, 2024],
        "privacy": {
            "schema_only": True,
            "allow_sample_rows": False,
            "allow_value_counts": False,
            "allow_missing_rates": False,
            "allow_descriptive_statistics": False,
            "holdout_years": [2024],
        },
        "concepts": {
            "age": {
                "candidate_columns": ["age"],
                "contract_required": True,
            },
            "strength": {
                "candidate_columns": ["BE5_1", "strength_days"],
                "contract_required": False,
            },
            "vigorous": {
                "required_columns": ["BE3_71", "BE3_72"],
                "contract_required": False,
            },
        },
    }


def _write_spec(path: Path) -> Path:
    path.write_text(
        yaml.safe_dump(_spec_payload(), sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    return path


def test_csv_reader_returns_header_only(tmp_path: Path) -> None:
    source = tmp_path / "synthetic.csv"
    source.write_text("age,BE5_1\nSECRET_AGE,SECRET_STRENGTH\n", encoding="utf-8-sig")
    metadata = read_schema_metadata(source)
    assert metadata.columns == ("age", "BE5_1")
    assert metadata.file_format == "csv"


def test_concept_audit_reports_complete_partial_and_missing() -> None:
    concepts = _spec_payload()["concepts"]
    result = audit_concepts(["age", "BE3_71"], concepts)
    assert result["age"]["status"] == "present"
    assert result["strength"]["status"] == "missing"
    assert result["vigorous"]["status"] == "partial"
    assert result["vigorous"]["missing_columns"] == ["BE3_72"]


def test_report_contains_no_participant_values_or_row_counts(tmp_path: Path) -> None:
    spec_path = _write_spec(tmp_path / "spec.yaml")
    year_files: list[tuple[int, Path]] = []
    for year in (2019, 2024):
        source = tmp_path / f"raw_{year}.csv"
        pd.DataFrame(
            {
                "age": [f"SECRET_AGE_{year}"],
                "BE5_1": [f"SECRET_STRENGTH_{year}"],
                "BE3_71": [f"SECRET_ACTIVITY_{year}"],
                "BE3_72": [f"SECRET_DAYS_{year}"],
            }
        ).to_csv(source, index=False, encoding="utf-8-sig")
        year_files.append((year, source))

    output = audit_schema_files(spec_path, year_files, tmp_path / "audit.json")
    report_text = output.read_text(encoding="utf-8")
    report = json.loads(report_text)

    assert "SECRET_" not in report_text
    assert '"row_count":' not in report_text
    assert report["policy"]["row_counts_included"] is False
    assert report["policy"]["participant_rows_read"] is False
    assert report["policy"]["participant_values_included"] is False
    assert report["sources"]["2024"]["concepts"]["strength"]["status"] == "present"


def test_partial_audit_requires_explicit_flag(tmp_path: Path) -> None:
    spec_path = _write_spec(tmp_path / "spec.yaml")
    source = tmp_path / "raw_2019.csv"
    source.write_text("age\n99\n", encoding="utf-8-sig")
    with pytest.raises(ValueError, match="Missing files"):
        audit_schema_files(spec_path, [(2019, source)], tmp_path / "audit.json")
    output = audit_schema_files(
        spec_path,
        [(2019, source)],
        tmp_path / "partial.json",
        allow_partial=True,
    )
    report = json.loads(output.read_text(encoding="utf-8"))
    assert report["missing_configured_years"] == [2024]


def test_unsafe_privacy_switch_is_rejected(tmp_path: Path) -> None:
    payload = _spec_payload()
    payload["privacy"]["allow_value_counts"] = True
    path = tmp_path / "unsafe.yaml"
    path.write_text(yaml.safe_dump(payload, sort_keys=False), encoding="utf-8")
    with pytest.raises(ValueError, match="must be explicitly false"):
        load_audit_spec(path)


def test_existing_report_is_not_replaced_without_force(tmp_path: Path) -> None:
    spec_path = _write_spec(tmp_path / "spec.yaml")
    source = tmp_path / "raw_2019.csv"
    source.write_text("age\n99\n", encoding="utf-8-sig")
    output = tmp_path / "audit.json"
    output.write_text("protected", encoding="utf-8")
    with pytest.raises(FileExistsError, match="--force"):
        audit_schema_files(
            spec_path,
            [(2019, source)],
            output,
            allow_partial=True,
        )
