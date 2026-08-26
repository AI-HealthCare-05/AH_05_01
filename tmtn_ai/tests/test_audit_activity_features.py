"""Synthetic-only tests for aggregate activity confirmation auditing."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import pytest

from src.activity_features import ACTIVITY_BRANCHES
from src.audit_activity_features import audit_activity_files


CONTRACT = Path("contracts/activity_feature_contract_v0_1.yaml")


def _frame(rows: int = 10) -> pd.DataFrame:
    payload: dict[str, list[int]] = {}
    for participation, days, hours, minutes in ACTIVITY_BRANCHES.values():
        payload[participation] = [2] * rows
        payload[days] = [8] * rows
        payload[hours] = [88] * rows
        payload[minutes] = [88] * rows
    payload["BE5_1"] = [1] * rows
    payload["pa_aerobic"] = [0] * rows
    payload["pa_muscle"] = [0] * rows
    return pd.DataFrame(payload)


def _write_years(tmp_path: Path, *, one_mismatch: bool = False) -> list[tuple[int, Path]]:
    result: list[tuple[int, Path]] = []
    for year in (2019, 2020, 2021):
        frame = _frame()
        if one_mismatch and year == 2019:
            frame.loc[0, "pa_aerobic"] = 1
        path = tmp_path / f"hn{str(year)[-2:]}_all.csv"
        frame.to_csv(path, index=False)
        result.append((year, path))
    return result


def test_aggregate_confirmation_contains_no_rows_and_matches_official(tmp_path: Path) -> None:
    output, markdown = audit_activity_files(
        CONTRACT,
        _write_years(tmp_path),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    text = output.read_text(encoding="utf-8")
    report = json.loads(text)

    assert report["policy"]["aggregate_only"] is True
    assert report["policy"]["model_training_performed"] is False
    assert report["policy"]["participant_rows_included"] is False
    assert report["missing_years"] == []
    assert report["sources"]["2019"]["pa_aerobic_reproduction"]["mismatch_count"] == 0
    assert report["sources"]["2019"]["pa_aerobic_reproduction"]["match_rate"] == 1.0
    assert set(report["sources"]["2019"]["completeness"]["branches"]) == set(
        ACTIVITY_BRANCHES
    )
    assert all(
        summary["unexpected_count"] == 0
        for summary in report["sources"]["2019"]["raw_code_domains"].values()
    )
    assert "SECRET_" not in text
    assert markdown.read_text(encoding="utf-8-sig").startswith("# KNHANES")


def test_small_mismatch_and_large_complement_are_both_suppressed(tmp_path: Path) -> None:
    output, _ = audit_activity_files(
        CONTRACT,
        _write_years(tmp_path, one_mismatch=True),
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    summary = json.loads(output.read_text(encoding="utf-8"))["sources"]["2019"][
        "pa_aerobic_reproduction"
    ]
    assert summary["mismatch_count"] is None
    assert summary["match_rate"] is None
    suppressed = [cell for cell in summary["cells"] if cell["suppressed"]]
    assert len(suppressed) == 2


def test_rare_unexpected_raw_code_is_flagged_without_exposing_value(tmp_path: Path) -> None:
    years = _write_years(tmp_path)
    frame = pd.read_csv(years[0][1])
    frame.loc[0, "BE5_1"] = 7
    frame.to_csv(years[0][1], index=False)
    output, _ = audit_activity_files(
        CONTRACT,
        years,
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    text = output.read_text(encoding="utf-8")
    report = json.loads(text)

    assert report["sources"]["2019"]["raw_code_domains"]["BE5_1"][
        "unexpected_count"
    ] is None
    assert '"BE5_1"' in text
    assert '"unexpected_count": null' in text


@pytest.mark.parametrize("year", [2022, 2023, 2024])
def test_reserved_and_forbidden_years_are_rejected_before_read(tmp_path: Path, year: int) -> None:
    source = tmp_path / f"raw_{year}.csv"
    source.write_text("DO_NOT_READ", encoding="utf-8")
    with pytest.raises(ValueError, match="not allowed"):
        audit_activity_files(
            CONTRACT,
            [(year, source)],
            tmp_path / "report.json",
            tmp_path / "report.md",
        )


def test_all_confirmation_years_and_non_overwrite_are_required(tmp_path: Path) -> None:
    years = _write_years(tmp_path)
    with pytest.raises(ValueError, match="All confirmation years"):
        audit_activity_files(
            CONTRACT,
            years[:1],
            tmp_path / "report.json",
            tmp_path / "report.md",
        )
    output_json, output_md = audit_activity_files(
        CONTRACT,
        years,
        tmp_path / "report.json",
        tmp_path / "report.md",
    )
    with pytest.raises(FileExistsError, match="--force"):
        audit_activity_files(CONTRACT, years, output_json, output_md)
