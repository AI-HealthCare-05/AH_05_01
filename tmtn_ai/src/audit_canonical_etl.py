"""Aggregate-only confirmation audit for canonical KNHANES ETL."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

import pandas as pd
import yaml

from .audit_raw_schema import parse_year_file
from .audit_raw_values import (
    _private_frequency,
    _private_partition_count,
    _rate,
    read_value_columns,
)
from .canonical_etl import (
    MODEL_INPUT_ALLOWLIST,
    PROHIBITED_MODEL_INPUTS,
    REQUIRED_CANONICAL_INPUT_COLUMNS,
    build_canonical_frame,
)
from .file_integrity import sha256_file


IMPLEMENTATION_VERSION = "v0.1-r2-aggregate-only"
QUALITY_REASON_COLUMNS = [
    "age_quality_reason",
    "sex_quality_reason",
    "pregnancy_quality_reason",
    "height_quality_reason",
    "weight_quality_reason",
    "waist_quality_reason",
    "fasting_hours_quality_reason",
    "fasting_glucose_quality_reason",
    "hba1c_quality_reason",
    "sbp_quality_reason",
    "dbp_quality_reason",
    "survey_weight_quality_reason",
]


def load_contract(path: Path) -> dict[str, Any]:
    raw = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
    if not isinstance(raw, Mapping):
        raise ValueError("Canonical ETL contract must be a YAML mapping.")
    if raw.get("do_not_train") is not True:
        raise ValueError("Canonical ETL confirmation requires do_not_train: true.")
    scope = raw.get("scope")
    quality = raw.get("quality_and_privacy")
    if not isinstance(scope, Mapping) or not isinstance(
        scope.get("implementation_years"), list
    ):
        raise ValueError("scope.implementation_years is required.")
    if scope.get("preserve_all_input_rows") is not True:
        raise ValueError("Canonical ETL must preserve all input rows.")
    if not isinstance(quality, Mapping) or quality.get("aggregate_confirmation_only") is not True:
        raise ValueError("quality_and_privacy.aggregate_confirmation_only must be true.")
    if quality.get("no_sample_rows") is not True or quality.get(
        "no_participant_identifiers_in_reports"
    ) is not True:
        raise ValueError("Canonical audit privacy switches must be enabled.")
    return dict(raw)


def _private_count(value: int, total: int, minimum: int) -> int | None:
    return _private_partition_count(value, total, minimum)


def _boolean_summary(series: pd.Series, minimum: int, digits: int) -> dict[str, Any]:
    boolean = series.fillna(False).astype(bool)
    count = int(boolean.sum())
    private = _private_count(count, len(series), minimum)
    return {
        "count": private,
        "rate": _rate(count, len(series), digits) if private is not None else None,
    }


def _label_summary(
    label: pd.Series, eligible: pd.Series, minimum: int, digits: int
) -> dict[str, Any]:
    eligible_bool = eligible.fillna(False).astype(bool)
    eligible_count = int(eligible_bool.sum())
    positive = eligible_bool & label.eq(1).fillna(False)
    positive_count = int(positive.sum())
    outside_violation = int((label.notna() & ~eligible_bool).sum())
    private_eligible = _private_count(eligible_count, len(label), minimum)
    private_positive = _private_count(positive_count, eligible_count, minimum)
    return {
        "eligible_count": private_eligible,
        "positive_count": private_positive,
        "positive_rate_among_eligible": (
            _rate(positive_count, eligible_count, digits)
            if private_eligible is not None and private_positive is not None
            else None
        ),
        "nonmissing_label_outside_eligibility_count": _private_count(
            outside_violation, len(label) - eligible_count, minimum
        ),
    }


def _audit_year(
    raw: pd.DataFrame, canonical: pd.DataFrame, minimum: int, digits: int
) -> dict[str, Any]:
    id_missing = int(raw["ID"].isna().sum())
    id_duplicates = int(raw["ID"].duplicated().sum())
    quality_reasons = {
        column: _private_frequency(canonical[column], minimum, True)
        for column in QUALITY_REASON_COLUMNS
    }
    p0 = canonical["p0_general_adult_eligible"].fillna(False).astype(bool)
    quality_reasons_p0 = {
        column: _private_frequency(canonical.loc[p0, column], minimum, True)
        for column in QUALITY_REASON_COLUMNS
    }
    return {
        "input_row_count": int(len(raw)),
        "output_row_count": int(len(canonical)),
        "row_count_preserved": len(raw) == len(canonical),
        "id_quality": {
            "missing_count": _private_count(id_missing, len(raw), minimum),
            "duplicate_after_first_count": _private_count(
                id_duplicates, len(raw), minimum
            ),
        },
        "eligibility": {
            "adult": _boolean_summary(canonical["adult_eligible"], minimum, digits),
            "pregnancy_eligible": _boolean_summary(
                canonical["pregnancy_eligible"], minimum, digits
            ),
            "p0_general_adult": _boolean_summary(
                canonical["p0_general_adult_eligible"], minimum, digits
            ),
        },
        "feature_completeness": {
            "F0": _boolean_summary(canonical["feature_f0_complete"], minimum, digits),
            "F1": _boolean_summary(canonical["feature_f1_complete"], minimum, digits),
            "F2_leisure": _boolean_summary(
                canonical["feature_f2_leisure_complete"], minimum, digits
            ),
        },
        "targets": {
            "waist": {
                "eligible": _boolean_summary(
                    canonical["waist_target_eligible"], minimum, digits
                )
            },
            "diabetes_raw": _label_summary(
                canonical["diabetes_measurement_label_raw"],
                canonical["diabetes_target_eligible"],
                minimum,
                digits,
            ),
            "diabetes_sensitivity": _label_summary(
                canonical["diabetes_measurement_label_sensitivity"],
                canonical["diabetes_target_eligible"],
                minimum,
                digits,
            ),
            "hypertension_raw": _label_summary(
                canonical["hypertension_measurement_label_raw"],
                canonical["hypertension_target_eligible"],
                minimum,
                digits,
            ),
            "hypertension_sensitivity": _label_summary(
                canonical["hypertension_measurement_label_sensitivity"],
                canonical["hypertension_target_eligible"],
                minimum,
                digits,
            ),
        },
        "quality_reasons": quality_reasons,
        "quality_reasons_p0_general_adult": quality_reasons_p0,
        "quality_issue_flags": {
            column: _boolean_summary(canonical[column], minimum, digits)
            for column in (
                "anthropometry_quality_issue",
                "diabetes_measurement_quality_issue",
                "blood_pressure_quality_issue",
                "activity_quality_issue",
                "eligibility_quality_issue",
                "sex_pregnancy_consistency_issue",
            )
        },
    }


def _render_markdown(report: Mapping[str, Any]) -> str:
    lines = [
        "# KNHANES canonical ETL 확인 감사",
        "",
        "> 집계 전용 결과 — canonical 행·ID 미출력, 분할·학습·성능평가 미수행",
        "",
        f"- 구현 버전: `{report['implementation']['version']}`",
        f"- 감사 연도: {', '.join(report['sources'])}",
        f"- 모델 입력 allowlist와 금지목록 중복: {report['leakage_guard']['overlap'] or '없음'}",
        "",
    ]
    for year, source in report["sources"].items():
        audit = source["audit"]
        lines.extend(
            [
                f"## {year}",
                "",
                f"- 입력/출력 행 수: {audit['input_row_count']} / {audit['output_row_count']}",
                f"- 행 보존: {audit['row_count_preserved']}",
                f"- ID 품질: {audit['id_quality']}",
                f"- P0 일반 성인: {audit['eligibility']['p0_general_adult']}",
                f"- F0 완전성: {audit['feature_completeness']['F0']}",
                f"- F1 완전성: {audit['feature_completeness']['F1']}",
                f"- F2-leisure 완전성: {audit['feature_completeness']['F2_leisure']}",
                f"- 허리둘레 타깃: {audit['targets']['waist']}",
                f"- 당뇨 원측정 라벨: {audit['targets']['diabetes_raw']}",
                f"- 고혈압 원측정 라벨: {audit['targets']['hypertension_raw']}",
                "",
            ]
        )
    lines.extend(
        [
            "## 제한",
            "",
            "- 후보 유효범위 위반은 값을 보존하고 사유 플래그만 남긴다. 자동 제외 기준이 아니다.",
            "- 결측 대치·스케일링·피처 선택은 train-only 파이프라인으로 연기한다.",
            "- 이 보고서의 비가중 비율은 유병률이 아니다.",
            "- 2023·2024 값은 사용하지 않는다.",
            "",
        ]
    )
    return "\n".join(lines)


def audit_canonical_files(
    contract_path: Path,
    year_files: Sequence[tuple[int, Path]],
    output_json: Path,
    output_md: Path,
    *,
    force: bool = False,
) -> tuple[Path, Path]:
    contract_path = contract_path.resolve()
    contract = load_contract(contract_path)
    scope = contract["scope"]
    allowed = set(scope["implementation_years"])
    blocked = set(scope.get("reserved_model_test_years", [])) | set(
        scope.get("forbidden_years", [])
    )
    supplied: dict[int, Path] = {}
    for year, path in year_files:
        if year not in allowed or year in blocked:
            raise ValueError(f"Year {year} is not allowed for canonical ETL confirmation.")
        if year in supplied:
            raise ValueError(f"Duplicate year {year}.")
        supplied[year] = path.resolve()
    missing = sorted(allowed - set(supplied))
    if missing:
        raise ValueError(f"All canonical confirmation years are required; missing: {missing}")

    output_json = output_json.resolve()
    output_md = output_md.resolve()
    if output_json == output_md:
        raise ValueError("JSON and Markdown outputs must be different files.")
    for output in (output_json, output_md):
        if output.exists() and not force:
            raise FileExistsError(f"Output exists: {output}. Use --force intentionally.")
        if output in set(supplied.values()):
            raise ValueError("Output cannot overwrite an input file.")

    quality = contract["quality_and_privacy"]
    minimum = int(quality["minimum_cell_count"])
    digits = 4
    sources: dict[str, Any] = {}
    declared_model_inputs = set(contract["column_roles"]["model_input_allowed"])
    declared_prohibited = set(contract["prohibited_model_inputs"])
    if declared_model_inputs != MODEL_INPUT_ALLOWLIST:
        raise ValueError("Contract and code model-input allowlists do not match.")
    if declared_prohibited != PROHIBITED_MODEL_INPUTS:
        raise ValueError("Contract and code prohibited-input lists do not match.")
    declared_outputs = {
        column
        for columns in contract["output_columns"].values()
        for column in columns
    }
    for year in sorted(supplied):
        path = supplied[year]
        raw, metadata = read_value_columns(
            path, REQUIRED_CANONICAL_INPUT_COLUMNS, set()
        )
        canonical = build_canonical_frame(raw, year=year)
        missing_outputs = sorted(declared_outputs - set(canonical.columns))
        if missing_outputs:
            raise ValueError(f"Canonical implementation is missing declared outputs: {missing_outputs}")
        sources[str(year)] = {
            "filename": path.name,
            "sha256": sha256_file(path),
            **metadata,
            "audit": _audit_year(raw, canonical, minimum, digits),
        }

    implementation = Path(__file__).resolve()
    etl_module = implementation.with_name("canonical_etl.py")
    overlap = sorted(MODEL_INPUT_ALLOWLIST & PROHIBITED_MODEL_INPUTS)
    report: dict[str, Any] = {
        "schema_version": 1,
        "policy": {
            "aggregate_only": True,
            "canonical_rows_included": False,
            "participant_identifiers_included": False,
            "train_or_test_files_created": False,
            "model_training_performed": False,
            "model_performance_evaluation_performed": False,
            "minimum_cell_count": minimum,
            "blocked_years": sorted(blocked),
        },
        "contract": {"filename": contract_path.name, "sha256": sha256_file(contract_path)},
        "implementation": {
            "version": IMPLEMENTATION_VERSION,
            "filename": implementation.name,
            "sha256": sha256_file(implementation),
            "canonical_etl_sha256": sha256_file(etl_module),
        },
        "leakage_guard": {
            "model_input_allowlist": sorted(MODEL_INPUT_ALLOWLIST),
            "prohibited_model_inputs": sorted(PROHIBITED_MODEL_INPUTS),
            "overlap": overlap,
            "passed": not overlap,
        },
        "sources": sources,
    }
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_md.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    output_md.write_text(_render_markdown(report), encoding="utf-8-sig")
    return output_json, output_md


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Aggregate-only confirmation audit for canonical KNHANES ETL."
    )
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--year-file", required=True, action="append", type=parse_year_file)
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-md", required=True, type=Path)
    parser.add_argument("--force", action="store_true")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    audit_canonical_files(
        args.contract,
        args.year_file,
        args.output_json,
        args.output_md,
        force=args.force,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
