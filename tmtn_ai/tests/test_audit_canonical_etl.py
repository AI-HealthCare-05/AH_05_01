"""Synthetic-only tests for aggregate canonical ETL confirmation."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import pytest

from src.activity_features import ACTIVITY_BRANCHES
from src.audit_canonical_etl import audit_canonical_files


CONTRACT = Path("contracts/canonical_etl_contract_v0_1.yaml")


def _frame(rows: int = 10) -> pd.DataFrame:
    payload: dict[str, list[object]] = {
        "ID": [f"SECRET_{i}" for i in range(rows)],
        "age": [50] * rows,
        "sex": [2] * rows,
        "wt_itvex": [1.5] * rows,
        "kstrata": [101] * rows,
        "psu": [1] * rows,
        "HE_prg": [0] * rows,
        "HE_ht": [170.0] * rows,
        "HE_wt": [70.0] * rows,
        "HE_wc": [90.0] * rows,
        "HE_fst": [8.0] * rows,
        "HE_glu": [100.0] * rows,
        "HE_HbA1c": [6.4] * rows,
        "HE_DM_HbA1c": [2] * rows,
        "HE_sbp": [130.0] * rows,
        "HE_dbp": [80.0] * rows,
        "HE_sbp2": [130.0] * rows,
        "HE_sbp3": [130.0] * rows,
        "HE_dbp2": [80.0] * rows,
        "HE_dbp3": [80.0] * rows,
        "HE_HP": [1] * rows,
        "BE5_1": [1] * rows,
        "pa_aerobic": [0] * rows,
    }
    for participation, days, hours, minutes in ACTIVITY_BRANCHES.values():
        payload[participation] = [2] * rows
        payload[days] = [8] * rows
        payload[hours] = [88] * rows
        payload[minutes] = [88] * rows
    return pd.DataFrame(payload)


def _write_years(tmp_path: Path) -> list[tuple[int, Path]]:
    result: list[tuple[int, Path]] = []
    for year in (2019, 2020, 2021, 2022):
        path = tmp_path / f"hn{str(year)[-2:]}_all.csv"
        _frame().to_csv(path, index=False)
        result.append((year, path))
    return result


def test_confirmation_preserves_rows_blocks_leakage_and_exports_no_ids(
    tmp_path: Path,
) -> None:
    output, markdown = audit_canonical_files(
        CONTRACT,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    text = output.read_text(encoding="utf-8")
    report = json.loads(text)

    assert report["policy"]["canonical_rows_included"] is False
    assert report["policy"]["participant_identifiers_included"] is False
    assert report["policy"]["train_or_test_files_created"] is False
    assert report["policy"]["model_training_performed"] is False
    assert report["leakage_guard"]["passed"] is True
    assert report["sources"]["2019"]["audit"]["row_count_preserved"] is True
    assert report["sources"]["2019"]["audit"]["feature_completeness"]["F2_leisure"][
        "count"
    ] == 10
    assert report["sources"]["2022"]["audit"]["targets"]["diabetes_raw"][
        "positive_count"
    ] == 0
    assert report["sources"]["2022"]["audit"]["targets"]["diabetes_sensitivity"][
        "positive_count"
    ] == 10
    assert "SECRET_" not in text
    assert markdown.read_text(encoding="utf-8-sig").startswith("# KNHANES")


def test_rare_quality_reason_is_complementarily_suppressed(tmp_path: Path) -> None:
    years = _write_years(tmp_path)
    frame = pd.read_csv(years[0][1])
    frame.loc[0, "HE_ht"] = 99.0
    frame.to_csv(years[0][1], index=False)
    output, _ = audit_canonical_files(
        CONTRACT,
        years,
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    cells = json.loads(output.read_text(encoding="utf-8"))["sources"]["2019"]["audit"][
        "quality_reasons"
    ]["height_quality_reason"]
    assert len([cell for cell in cells if cell["suppressed"]]) == 2


@pytest.mark.parametrize("year", [2023, 2024])
def test_blocked_years_are_rejected_before_read(tmp_path: Path, year: int) -> None:
    source = tmp_path / f"raw_{year}.csv"
    source.write_text("DO_NOT_READ", encoding="utf-8")
    with pytest.raises(ValueError, match="not allowed"):
        audit_canonical_files(
            CONTRACT,
            [(year, source)],
            tmp_path / "report.json",
            tmp_path / "report.md",
        )


def test_all_years_and_non_overwrite_are_required(tmp_path: Path) -> None:
    years = _write_years(tmp_path)
    with pytest.raises(ValueError, match="All canonical confirmation years"):
        audit_canonical_files(
            CONTRACT,
            years[:3],
            tmp_path / "report.json",
            tmp_path / "report.md",
        )
    output_json, output_md = audit_canonical_files(
        CONTRACT,
        years,
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    with pytest.raises(FileExistsError, match="--force"):
        audit_canonical_files(CONTRACT, years, output_json, output_md)
