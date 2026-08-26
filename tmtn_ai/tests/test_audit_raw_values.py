"""Synthetic-only tests for aggregate KNHANES value auditing."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import pytest
import yaml

from src.audit_raw_values import audit_value_files, load_value_audit_spec


def _spec_payload() -> dict[str, object]:
    return {
        "schema_version": 1,
        "audit_version": "synthetic_v1",
        "survey": "synthetic",
        "years": [2019, 2020],
        "forbidden_years": [2024],
        "privacy": {
            "aggregate_only": True,
            "allow_sample_rows": False,
            "allow_row_identifiers": False,
            "allow_raw_value_export": False,
            "minimum_cell_count": 3,
            "complementary_suppression": True,
            "round_digits": 4,
        },
        "source": {"participant_id_column": "ID", "id_required": True},
        "variables": {
            "age": {
                "column": "age",
                "kind": "numeric",
                "required": True,
                "candidate_valid_range": [19, 80],
                "thresholds": [{"name": "adult", "operator": "ge", "value": 19}],
            },
            "height": {
                "column": "height",
                "kind": "numeric",
                "required": True,
                "candidate_valid_range": [100, 220],
            },
            "weight": {
                "column": "weight",
                "kind": "numeric",
                "required": True,
                "candidate_valid_range": [25, 250],
            },
            "bmi": {
                "column": "bmi",
                "kind": "numeric",
                "required": True,
                "candidate_valid_range": [10, 80],
            },
            "fasting": {
                "column": "fasting",
                "kind": "numeric",
                "required": True,
                "candidate_special_codes": [99],
            },
            "glucose": {
                "column": "glucose",
                "kind": "numeric",
                "required": True,
                "candidate_special_codes": [999],
            },
            "group": {
                "column": "group",
                "kind": "categorical",
                "required": True,
                "expected_codes": [1, 2],
            },
            "optional": {
                "column": "optional_column",
                "kind": "categorical",
                "required": False,
            },
        },
        "missingness_patterns": [
            {"name": "measurements", "columns": ["fasting", "glucose"]}
        ],
        "cross_tabs": [{"name": "group_by_fasting", "columns": ["group", "fasting"]}],
        "formula_checks": [
            {
                "name": "bmi_check",
                "operation": "bmi",
                "output_column": "bmi",
                "input_columns": ["height", "weight"],
                "tolerance": 0.1,
            }
        ],
        "rule_counts": [
            {
                "name": "measurement_base",
                "pregnancy_exclusion_applied": False,
                "eligibility": {
                    "all": [
                        {"column": "age", "operator": "ge", "value": 19},
                        {"column": "fasting", "operator": "ge", "value": 8},
                        {"column": "glucose", "operator": "not_missing"},
                    ]
                },
                "positive": {
                    "any": [{"column": "glucose", "operator": "ge", "value": 126}]
                },
            }
        ],
    }


def _write_spec(path: Path, payload: dict[str, object] | None = None) -> Path:
    path.write_text(
        yaml.safe_dump(payload or _spec_payload(), sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    return path


def _frame(year: int) -> pd.DataFrame:
    return pd.DataFrame(
        {
            "ID": [f"SECRET_{year}_{index}" for index in range(8)],
            "age": [20, 30, 40, 50, 60, 70, 18, 81],
            "height": [170.0] * 8,
            "weight": [68.0] * 8,
            "bmi": [23.53] * 7 + [99.0],
            "fasting": [8, 8, 8, 7, 99, 8, 8, 8],
            "glucose": [100, 126, 130, 150, 999, None, 90, 110],
            "group": [1, 1, 1, 1, 1, 2, 2, 7],
        }
    )


def _write_years(tmp_path: Path) -> list[tuple[int, Path]]:
    result: list[tuple[int, Path]] = []
    for year in (2019, 2020):
        path = tmp_path / f"raw_{year}.csv"
        _frame(year).to_csv(path, index=False, encoding="utf-8-sig")
        result.append((year, path))
    return result


def test_value_audit_reports_aggregates_without_rows_or_ids(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path / "spec.yaml")
    json_path, md_path = audit_value_files(
        spec,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    report_text = json_path.read_text(encoding="utf-8")
    report = json.loads(report_text)

    assert "SECRET_" not in report_text
    assert "SECRET_" not in md_path.read_text(encoding="utf-8-sig")
    assert report["policy"]["participant_rows_included"] is False
    assert report["policy"]["participant_identifiers_included"] is False
    assert report["policy"]["model_training_performed"] is False
    assert report["policy"]["binary_partition_complementary_suppression"] is True
    assert report["audit_implementation"]["version"]
    assert len(report["audit_implementation"]["sha256"]) == 64
    assert report["sources"]["2019"]["row_count"] == 8
    assert report["sources"]["2019"]["id_quality"]["raw_identifiers_included"] is False


def test_small_cells_and_complementary_cell_are_suppressed(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path / "spec.yaml")
    output, _ = audit_value_files(
        spec,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    report = json.loads(output.read_text(encoding="utf-8"))
    cells = report["sources"]["2019"]["variables"]["group"]["observed_code_counts"]
    suppressed = [cell for cell in cells if cell["suppressed"]]
    assert len(suppressed) >= 2
    assert all(cell["count"] is None for cell in suppressed)


def test_candidate_special_codes_are_excluded_from_numeric_rules(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path / "spec.yaml")
    output, _ = audit_value_files(
        spec,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    report = json.loads(output.read_text(encoding="utf-8"))
    year = report["sources"]["2019"]
    glucose = year["variables"]["glucose"]
    rule = year["rule_counts"]["measurement_base"]
    assert glucose["analysis_value_count"] == 6
    assert rule["eligible_count"] == 4
    assert rule["positive_count"] is None  # two positives are below minimum cell count


def test_formula_and_candidate_range_checks_are_aggregate(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path / "spec.yaml")
    output, _ = audit_value_files(
        spec,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    report = json.loads(output.read_text(encoding="utf-8"))
    year = report["sources"]["2019"]
    assert year["formula_checks"]["bmi_check"]["eligible_count"] == 8
    assert year["formula_checks"]["bmi_check"]["mismatch_count"] is None
    assert year["variables"]["age"]["below_candidate_range_count"] is None
    assert year["variables"]["age"]["above_candidate_range_count"] is None


def test_forbidden_year_is_rejected_before_value_read(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path / "spec.yaml")
    forbidden = tmp_path / "raw_2024.csv"
    forbidden.write_text("DO_NOT_READ", encoding="utf-8")
    with pytest.raises(ValueError, match="forbidden"):
        audit_value_files(
            spec,
            [(2024, forbidden)],
            tmp_path / "report.json",
            tmp_path / "report.md",
            allow_partial=True,
        )


def test_missing_required_column_fails(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path / "spec.yaml")
    source = tmp_path / "raw_2019.csv"
    _frame(2019).drop(columns=["glucose"]).to_csv(source, index=False)
    with pytest.raises(ValueError, match="Required value-audit columns missing"):
        audit_value_files(
            spec,
            [(2019, source)],
            tmp_path / "report.json",
            tmp_path / "report.md",
            allow_partial=True,
        )


def test_partial_and_overwrite_require_explicit_flags(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path / "spec.yaml")
    source = tmp_path / "raw_2019.csv"
    _frame(2019).to_csv(source, index=False)
    with pytest.raises(ValueError, match="Missing files"):
        audit_value_files(
            spec,
            [(2019, source)],
            tmp_path / "report.json",
            tmp_path / "report.md",
        )
    output_json, output_md = audit_value_files(
        spec,
        [(2019, source)],
        tmp_path / "report.json",
        tmp_path / "report.md",
        allow_partial=True,
    )
    assert output_json.exists() and output_md.exists()
    with pytest.raises(FileExistsError, match="--force"):
        audit_value_files(
            spec,
            [(2019, source)],
            output_json,
            output_md,
            allow_partial=True,
        )


def test_unsafe_privacy_configuration_is_rejected(tmp_path: Path) -> None:
    payload = _spec_payload()
    payload["privacy"]["allow_raw_value_export"] = True
    path = _write_spec(tmp_path / "unsafe.yaml", payload)
    with pytest.raises(ValueError, match="must be explicitly false"):
        load_value_audit_spec(path)


def test_expected_codes_can_change_by_year(tmp_path: Path) -> None:
    payload = _spec_payload()
    payload["variables"]["group"].pop("expected_codes")
    payload["variables"]["group"]["expected_codes_by_year"] = {
        2019: [1, 2],
        2020: [1, 2, 7],
    }
    spec = _write_spec(tmp_path / "spec.yaml", payload)
    output, _ = audit_value_files(
        spec,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    report = json.loads(output.read_text(encoding="utf-8"))
    assert report["sources"]["2019"]["variables"]["group"]["unexpected_code_count"] is None
    assert report["sources"]["2020"]["variables"]["group"]["unexpected_code_count"] == 0


def test_expected_codes_by_year_must_cover_all_years(tmp_path: Path) -> None:
    payload = _spec_payload()
    payload["variables"]["group"]["expected_codes_by_year"] = {2019: [1, 2]}
    path = _write_spec(tmp_path / "invalid_year_mapping.yaml", payload)
    with pytest.raises(ValueError, match="cover every configured year"):
        load_value_audit_spec(path)


def test_confirmed_special_codes_and_valid_range_are_reported(tmp_path: Path) -> None:
    payload = _spec_payload()
    fasting = payload["variables"]["fasting"]
    fasting.pop("candidate_special_codes")
    fasting["special_codes"] = [99]
    payload["variables"]["age"].pop("candidate_valid_range")
    payload["variables"]["age"]["valid_range"] = [19, 80]
    spec = _write_spec(tmp_path / "spec.yaml", payload)
    output, _ = audit_value_files(
        spec,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    year = json.loads(output.read_text(encoding="utf-8"))["sources"]["2019"]
    assert year["variables"]["fasting"]["special_codes"] == [99]
    assert year["variables"]["age"]["valid_range"] == [19, 80]


def test_rule_count_suppresses_small_complement(tmp_path: Path) -> None:
    payload = _spec_payload()
    payload["rule_counts"].append(
        {
            "name": "adult_completeness",
            "eligibility": {"all": [{"column": "age", "operator": "not_missing"}]},
            "positive": {"all": [{"column": "age", "operator": "ge", "value": 19}]},
        }
    )
    spec = _write_spec(tmp_path / "spec.yaml", payload)
    output, _ = audit_value_files(
        spec,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    rule = json.loads(output.read_text(encoding="utf-8"))["sources"]["2019"][
        "rule_counts"
    ]["adult_completeness"]
    assert rule["eligible_count"] == 8
    assert rule["positive_count"] is None
    assert rule["positive_rate_among_eligible"] is None


def test_missing_count_suppresses_small_nonmissing_complement(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path / "spec.yaml")
    source = tmp_path / "raw_2019.csv"
    frame = _frame(2019)
    frame.loc[:6, "group"] = None
    frame.to_csv(source, index=False)
    output, _ = audit_value_files(
        spec,
        [(2019, source)],
        tmp_path / "report.json",
        tmp_path / "report.md",
        allow_partial=True,
    )
    group = json.loads(output.read_text(encoding="utf-8"))["sources"]["2019"][
        "variables"
    ]["group"]
    assert group["missing_count"] is None
    assert group["missing_rate"] is None
