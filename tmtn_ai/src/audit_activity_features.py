"""Aggregate-only confirmation audit for KNHANES activity feature derivation."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

import pandas as pd
import yaml

from .activity_features import (
    ACTIVITY_BRANCHES,
    REQUIRED_KNHANES_COLUMNS,
    derive_knhanes_activity_features,
)
from .audit_raw_schema import parse_year_file
from .audit_raw_values import (
    _private_frequency,
    _private_partition_count,
    _rate,
    read_value_columns,
)
from .file_integrity import sha256_file


IMPLEMENTATION_VERSION = "v0.1.1-aggregate-only-domain-checks"


def _expected_raw_domains() -> dict[str, set[int]]:
    domains: dict[str, set[int]] = {"BE5_1": {1, 2, 3, 4, 5, 6, 8, 9}}
    for participation, days, hours, minutes in ACTIVITY_BRANCHES.values():
        domains[participation] = {1, 2, 8, 9}
        domains[days] = set(range(1, 8)) | {8, 9}
        domains[hours] = set(range(0, 24)) | {88, 99}
        domains[minutes] = set(range(0, 60)) | {88, 99}
    domains["pa_aerobic"] = {0, 1}
    domains["pa_muscle"] = {0, 1}
    return domains


def load_activity_contract(path: Path) -> dict[str, Any]:
    raw = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
    if not isinstance(raw, Mapping):
        raise ValueError("Activity feature contract must be a YAML mapping.")
    if raw.get("do_not_train") is not True:
        raise ValueError("Activity confirmation requires do_not_train: true.")
    gate = raw.get("raw_confirmation_gate")
    if not isinstance(gate, Mapping) or gate.get("status") is None:
        raise ValueError("raw_confirmation_gate is required.")
    years = gate.get("years")
    if not isinstance(years, list) or not years or not all(isinstance(y, int) for y in years):
        raise ValueError("raw_confirmation_gate.years must be an integer list.")
    forbidden = set(raw.get("forbidden_years", []))
    reserved = set(gate.get("reserved_years_not_used_for_rule_revision", []))
    if set(years) & (forbidden | reserved):
        raise ValueError("Confirmation years overlap forbidden or reserved years.")
    return dict(raw)


def _binary_summary(
    reproduced: pd.Series,
    official: pd.Series,
    *,
    minimum: int,
    digits: int,
) -> dict[str, Any]:
    comparable = reproduced.notna() & official.notna()
    comparable_count = int(comparable.sum())
    mismatch_count = int(reproduced.loc[comparable].ne(official.loc[comparable]).sum())
    private_comparable = _private_partition_count(comparable_count, len(reproduced), minimum)
    private_mismatch = _private_partition_count(mismatch_count, comparable_count, minimum)
    labels = pd.Series("not_comparable", index=reproduced.index, dtype="object")
    labels.loc[comparable & reproduced.eq(official)] = "match"
    labels.loc[comparable & reproduced.ne(official)] = "mismatch"
    return {
        "comparable_count": private_comparable,
        "mismatch_count": private_mismatch,
        "match_rate": (
            _rate(comparable_count - mismatch_count, comparable_count, digits)
            if private_comparable is not None and private_mismatch is not None
            else None
        ),
        "cells": _private_frequency(labels, minimum, True),
    }


def _completeness_summary(series: pd.Series, minimum: int, digits: int) -> dict[str, Any]:
    complete = series.fillna(False).astype(bool)
    count = int(complete.sum())
    private_count = _private_partition_count(count, len(series), minimum)
    return {
        "complete_count": private_count,
        "complete_rate": _rate(count, len(series), digits) if private_count is not None else None,
    }


def _numeric_summary(series: pd.Series, minimum: int, digits: int) -> dict[str, Any]:
    numeric = pd.to_numeric(series, errors="coerce")
    missing = int(numeric.isna().sum())
    private_missing = _private_partition_count(missing, len(series), minimum)
    valid = numeric.dropna()
    result: dict[str, Any] = {
        "missing_count": private_missing,
        "missing_rate": _rate(missing, len(series), digits) if private_missing is not None else None,
    }
    if len(valid) >= minimum:
        q = valid.quantile([0.01, 0.25, 0.5, 0.75, 0.99])
        result["quantiles"] = {
            "p01": round(float(q.loc[0.01]), digits),
            "p25": round(float(q.loc[0.25]), digits),
            "p50": round(float(q.loc[0.50]), digits),
            "p75": round(float(q.loc[0.75]), digits),
            "p99": round(float(q.loc[0.99]), digits),
        }
    return result


def _unexpected_code_summary(
    frame: pd.DataFrame, minimum: int
) -> dict[str, dict[str, int | None]]:
    """Report unexpected counts only; never emit unexpected participant values."""

    summaries: dict[str, dict[str, int | None]] = {}
    for column, expected in _expected_raw_domains().items():
        if column not in frame.columns:
            continue
        numeric = pd.to_numeric(frame[column], errors="coerce")
        nonmissing = numeric.notna()
        unexpected_count = int((nonmissing & ~numeric.isin(expected)).sum())
        summaries[column] = {
            "unexpected_count": _private_partition_count(
                unexpected_count, int(nonmissing.sum()), minimum
            )
        }
    return summaries


def _audit_year(frame: pd.DataFrame, minimum: int, digits: int) -> dict[str, Any]:
    derived = derive_knhanes_activity_features(frame)
    official = derived.get("pa_aerobic_official")
    if official is None:
        raise ValueError("Official pa_aerobic column is required for confirmation.")
    result: dict[str, Any] = {
        "row_count": int(len(frame)),
        "pa_aerobic_reproduction": _binary_summary(
            derived["pa_aerobic_reproduced"], official, minimum=minimum, digits=digits
        ),
        "completeness": {
            "branches": {
                branch: _completeness_summary(
                    derived[f"{branch}_min_week"].notna(), minimum, digits
                )
                for branch in ACTIVITY_BRANCHES
            },
            "total_activity": _completeness_summary(
                derived["total_activity_complete"], minimum, digits
            ),
            "leisure_activity": _completeness_summary(
                derived["leisure_activity_complete"], minimum, digits
            ),
        },
        "features": {
            "total_moderate_equivalent_min_week": _numeric_summary(
                derived["total_moderate_equivalent_min_week"], minimum, digits
            ),
            "leisure_aerobic_moderate_equivalent_min_week": _numeric_summary(
                derived["leisure_aerobic_moderate_equivalent_min_week"], minimum, digits
            ),
            "strength_days_week": _numeric_summary(
                derived["strength_days_week"], minimum, digits
            ),
        },
        "raw_code_domains": _unexpected_code_summary(frame, minimum),
    }
    if "pa_muscle" in frame.columns:
        official_muscle_raw = pd.to_numeric(frame["pa_muscle"], errors="coerce")
        official_muscle = pd.Series(pd.NA, index=frame.index, dtype="Int8")
        official_muscle_valid = official_muscle_raw.isin([0, 1])
        official_muscle.loc[official_muscle_valid] = official_muscle_raw.loc[
            official_muscle_valid
        ].astype("int8")
        result["pa_muscle_reproduction"] = _binary_summary(
            derived["pa_muscle_reproduced"],
            official_muscle,
            minimum=minimum,
            digits=digits,
        )
    return result


def _render_markdown(report: Mapping[str, Any]) -> str:
    lines = [
        "# KNHANES 운동 피처 확인 감사",
        "",
        "> 집계 전용 결과 — 행·ID 미포함, 모델 학습 미수행",
        "",
        f"- 구현 버전: `{report['implementation']['version']}`",
        f"- 감사 연도: {', '.join(report['sources'])}",
        f"- 누락 연도: {report['missing_years'] or '없음'}",
        "",
    ]
    for year, source in report["sources"].items():
        pa = source["pa_aerobic_reproduction"]
        unexpected = [
            column
            for column, summary in source["raw_code_domains"].items()
            if summary["unexpected_count"] != 0
        ]
        lines.extend(
            [
                f"## {year}",
                "",
                f"- 행 수: {source['row_count']}",
                f"- pa_aerobic 비교 가능: {pa['comparable_count']}",
                f"- 불일치: {pa['mismatch_count']}",
                f"- 일치율: {pa['match_rate']}",
                f"- 전체 활동 완전성: {source['completeness']['total_activity']}",
                f"- 여가 활동 완전성: {source['completeness']['leisure_activity']}",
                f"- 영역별 완전성: {source['completeness']['branches']}",
                f"- 예상 밖 코드 확인 필요 컬럼: {unexpected or '없음'}",
                "",
            ]
        )
    lines.extend(
        [
            "## 제한",
            "",
            "- 공식 pa_aerobic은 일·이동·여가 전체 활동 지표다.",
            "- 여가 중강도환산분과 공식 pa_aerobic을 서로 대체하지 않는다.",
            "- 이 결과로 모델 학습 또는 성능 평가를 수행하지 않는다.",
            "- 2022·2023은 규칙 수정에 사용하지 않으며 2024는 접근하지 않는다.",
            "",
        ]
    )
    return "\n".join(lines)


def audit_activity_files(
    contract_path: Path,
    year_files: Sequence[tuple[int, Path]],
    output_json: Path,
    output_md: Path,
    *,
    force: bool = False,
) -> tuple[Path, Path]:
    contract_path = contract_path.resolve()
    contract = load_activity_contract(contract_path)
    allowed = set(contract["raw_confirmation_gate"]["years"])
    forbidden = set(contract.get("forbidden_years", []))
    reserved = set(contract["raw_confirmation_gate"].get("reserved_years_not_used_for_rule_revision", []))
    supplied: dict[int, Path] = {}
    for year, path in year_files:
        if year in forbidden or year in reserved or year not in allowed:
            raise ValueError(f"Year {year} is not allowed for activity rule confirmation.")
        if year in supplied:
            raise ValueError(f"Duplicate year {year}.")
        supplied[year] = path.resolve()
    missing_years = sorted(allowed - set(supplied))
    if missing_years:
        raise ValueError(f"All confirmation years are required; missing: {missing_years}")

    output_json = output_json.resolve()
    output_md = output_md.resolve()
    if output_json == output_md:
        raise ValueError("JSON and Markdown outputs must be different files.")
    for output in (output_json, output_md):
        if output.exists() and not force:
            raise FileExistsError(f"Output exists: {output}. Use --force intentionally.")
        if output in set(supplied.values()):
            raise ValueError("Output cannot overwrite an input file.")

    required = set(REQUIRED_KNHANES_COLUMNS) | {"pa_aerobic"}
    optional = {"pa_muscle"}
    sources: dict[str, Any] = {}
    minimum = 5
    digits = 4
    for year in sorted(supplied):
        path = supplied[year]
        frame, metadata = read_value_columns(path, required, optional)
        sources[str(year)] = {
            "filename": path.name,
            "sha256": sha256_file(path),
            **metadata,
            **_audit_year(frame, minimum, digits),
        }

    implementation = Path(__file__).resolve()
    report: dict[str, Any] = {
        "schema_version": 1,
        "policy": {
            "aggregate_only": True,
            "participant_rows_included": False,
            "participant_identifiers_included": False,
            "model_training_performed": False,
            "minimum_cell_count": minimum,
            "binary_partition_complementary_suppression": True,
            "forbidden_years": sorted(forbidden),
            "reserved_years": sorted(reserved),
        },
        "contract": {"filename": contract_path.name, "sha256": sha256_file(contract_path)},
        "implementation": {
            "version": IMPLEMENTATION_VERSION,
            "filename": implementation.name,
            "sha256": sha256_file(implementation),
            "activity_feature_sha256": sha256_file(Path(__file__).with_name("activity_features.py")),
        },
        "missing_years": missing_years,
        "sources": sources,
    }
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_md.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    output_md.write_text(_render_markdown(report), encoding="utf-8-sig")
    return output_json, output_md


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Aggregate-only KNHANES activity feature confirmation audit.")
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--year-file", required=True, action="append", type=parse_year_file)
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-md", required=True, type=Path)
    parser.add_argument("--force", action="store_true")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    audit_activity_files(
        args.contract,
        args.year_file,
        args.output_json,
        args.output_md,
        force=args.force,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
