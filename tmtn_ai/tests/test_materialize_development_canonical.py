from __future__ import annotations

from pathlib import Path

import pytest

from src.file_integrity import sha256_file
from src.materialize_development_canonical import validate_year_files


def _files(tmp_path: Path) -> tuple[list[tuple[int, Path]], dict]:
    mappings: list[tuple[int, Path]] = []
    sources: dict[str, dict[str, str]] = {}
    for year in (2019, 2020, 2021):
        path = tmp_path / f"hn{str(year)[-2:]}_all.csv"
        path.write_text(f"synthetic-{year}\n", encoding="utf-8")
        mappings.append((year, path))
        sources[str(year)] = {"sha256": sha256_file(path)}
    return mappings, {"sources": sources}


def test_year_files_require_exact_audited_2019_2021_snapshots(tmp_path: Path) -> None:
    mappings, receipt = _files(tmp_path)

    validated = validate_year_files(mappings, receipt)

    assert set(validated) == {2019, 2020, 2021}


def test_year_files_reject_reserved_year_before_reading(tmp_path: Path) -> None:
    mappings, receipt = _files(tmp_path)
    reserved = tmp_path / "hn22_all.csv"
    reserved.write_text("synthetic-2022\n", encoding="utf-8")

    with pytest.raises(ValueError, match="Only 2019-2021"):
        validate_year_files([*mappings, (2022, reserved)], receipt)


def test_year_files_reject_a_snapshot_not_seen_in_the_audit(tmp_path: Path) -> None:
    mappings, receipt = _files(tmp_path)
    mappings[0][1].write_text("changed-after-audit\n", encoding="utf-8")

    with pytest.raises(ValueError, match="hash mismatch for 2019"):
        validate_year_files(mappings, receipt)
