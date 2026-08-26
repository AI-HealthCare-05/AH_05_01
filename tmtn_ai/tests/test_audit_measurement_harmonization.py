"""Synthetic-only tests for aggregate measurement harmonization auditing."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import pytest

from src.audit_measurement_harmonization import audit_measurement_files


CONTRACT = Path("contracts/measurement_harmonization_contract_v0_1.yaml")


def _frame(rows: int = 10, *, hba1c: float = 6.4) -> pd.DataFrame:
    return pd.DataFrame(
        {
            "age": [50] * rows,
            "HE_prg": [0] * rows,
            "HE_fst": [8] * rows,
            "HE_glu": [100] * rows,
            "HE_HbA1c": [hba1c] * rows,
            "HE_sbp": [130] * rows,
            "HE_dbp": [80] * rows,
            "HE_sbp2": [130] * rows,
            "HE_sbp3": [130] * rows,
            "HE_dbp2": [80] * rows,
            "HE_dbp3": [80] * rows,
        }
    )


def _write_years(tmp_path: Path) -> list[tuple[int, Path]]:
    result: list[tuple[int, Path]] = []
    for year in (2019, 2020, 2021, 2022):
        path = tmp_path / f"hn{str(year)[-2:]}_all.csv"
        _frame().to_csv(path, index=False)
        result.append((year, path))
    return result


def test_aggregate_audit_records_policy_and_expected_transitions(tmp_path: Path) -> None:
    output, markdown = audit_measurement_files(
        CONTRACT,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    text = output.read_text(encoding="utf-8")
    report = json.loads(text)

    assert report["policy"]["primary_measurement_values"] == "official_raw_values"
    assert report["policy"]["converted_values_role"] == "sensitivity_only"
    assert report["policy"]["model_training_performed"] is False
    assert report["policy"]["participant_rows_included"] is False
    assert report["sources"]["2022"]["hba1c"]["conversion_applied"] is True
    assert report["sources"]["2022"]["hba1c"]["hba1c_threshold_transition"][
        "changed_count"
    ] == 10
    assert report["sources"]["2019"]["hba1c"]["hba1c_threshold_transition"][
        "changed_count"
    ] == 0
    assert report["sources"]["2021"]["blood_pressure"]["conversion_applied"] is True
    assert report["sources"]["2022"]["blood_pressure"]["official_reproduction"][
        "systolic"
    ]["mismatch_count"] == 0
    assert "SECRET_" not in text
    assert markdown.read_text(encoding="utf-8-sig").startswith("# KNHANES")


def test_rare_transition_uses_complementary_suppression(tmp_path: Path) -> None:
    years = _write_years(tmp_path)
    frame = pd.read_csv(years[-1][1])
    frame.loc[0, "HE_HbA1c"] = 5.0
    frame.to_csv(years[-1][1], index=False)
    output, _ = audit_measurement_files(
        CONTRACT,
        years,
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    transition = json.loads(output.read_text(encoding="utf-8"))["sources"]["2022"][
        "hba1c"
    ]["hba1c_threshold_transition"]
    assert transition["changed_count"] is None
    assert transition["agreement_rate"] is None
    assert len([cell for cell in transition["cells"] if cell["suppressed"]]) == 2


@pytest.mark.parametrize("year", [2023, 2024])
def test_reserved_and_forbidden_years_are_rejected_before_read(
    tmp_path: Path, year: int
) -> None:
    source = tmp_path / f"raw_{year}.csv"
    source.write_text("DO_NOT_READ", encoding="utf-8")
    with pytest.raises(ValueError, match="not allowed"):
        audit_measurement_files(
            CONTRACT,
            [(year, source)],
            tmp_path / "report.json",
            tmp_path / "report.md",
        )


def test_all_audit_years_and_non_overwrite_are_required(tmp_path: Path) -> None:
    years = _write_years(tmp_path)
    with pytest.raises(ValueError, match="All measurement audit years"):
        audit_measurement_files(
            CONTRACT,
            years[:3],
            tmp_path / "report.json",
            tmp_path / "report.md",
        )
    output_json, output_md = audit_measurement_files(
        CONTRACT,
        years,
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    with pytest.raises(FileExistsError, match="--force"):
        audit_measurement_files(CONTRACT, years, output_json, output_md)
