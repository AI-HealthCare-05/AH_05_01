"""Aggregate-only audit of KNHANES HbA1c and blood-pressure harmonization."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

import numpy as np
import pandas as pd
import yaml

from .audit_raw_schema import parse_year_file
from .audit_raw_values import (
    _private_frequency,
    _private_partition_count,
    _rate,
    read_value_columns,
)
from .file_integrity import sha256_file
from .measurement_harmonization import (
    convert_hba1c_to_2019_2021_scale,
    convert_microlife_bp_to_greenlight_scale,
    reproduce_official_final_bp,
)


IMPLEMENTATION_VERSION = "v0.1-aggregate-only"
REQUIRED_COLUMNS = {
    "age",
    "HE_prg",
    "HE_fst",
    "HE_glu",
    "HE_HbA1c",
    "HE_sbp",
    "HE_dbp",
    "HE_sbp2",
    "HE_sbp3",
    "HE_dbp2",
    "HE_dbp3",
}


def load_contract(path: Path) -> dict[str, Any]:
    raw = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
    if not isinstance(raw, Mapping):
        raise ValueError("Measurement harmonization contract must be a YAML mapping.")
    if raw.get("do_not_train") is not True:
        raise ValueError("Measurement audit requires do_not_train: true.")
    scope = raw.get("scope")
    privacy = raw.get("privacy")
    if not isinstance(scope, Mapping) or not isinstance(scope.get("audit_years"), list):
        raise ValueError("scope.audit_years is required.")
    if not isinstance(privacy, Mapping) or privacy.get("aggregate_only") is not True:
        raise ValueError("privacy.aggregate_only must be true.")
    for key in ("allow_sample_rows", "allow_row_identifiers", "allow_raw_value_export"):
        if privacy.get(key) is not False:
            raise ValueError(f"privacy.{key} must be false.")
    if raw.get("pre_registered_policy", {}).get("primary_measurement_values") != "official_raw_values":
        raise ValueError("The primary raw-value policy must be pre-registered.")
    return dict(raw)


def _numeric(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce").astype("Float64")


def _finite(series: pd.Series) -> pd.Series:
    numeric = _numeric(series)
    values = numeric.to_numpy(dtype="float64", na_value=np.nan)
    return numeric.where(np.isfinite(values))


def _count(count: int, denominator: int, minimum: int) -> int | None:
    return _private_partition_count(count, denominator, minimum)


def _numeric_summary(series: pd.Series, minimum: int, digits: int) -> dict[str, Any]:
    numeric = _numeric(series)
    finite = _finite(series)
    missing_count = int(numeric.isna().sum())
    nonfinite_count = int((numeric.notna() & finite.isna()).sum())
    analysis = finite.dropna()
    result: dict[str, Any] = {
        "analysis_count": int(len(analysis)),
        "missing_count": _count(missing_count, len(series), minimum),
        "nonfinite_count": _count(nonfinite_count, int(numeric.notna().sum()), minimum),
    }
    if len(analysis) >= minimum:
        q = analysis.quantile([0.01, 0.25, 0.5, 0.75, 0.99])
        result.update(
            {
                "mean": round(float(analysis.mean()), digits),
                "standard_deviation": round(float(analysis.std(ddof=1)), digits),
                "quantiles": {
                    "p01": round(float(q.loc[0.01]), digits),
                    "p25": round(float(q.loc[0.25]), digits),
                    "p50": round(float(q.loc[0.50]), digits),
                    "p75": round(float(q.loc[0.75]), digits),
                    "p99": round(float(q.loc[0.99]), digits),
                },
            }
        )
    return result


def _nullable_label(condition: pd.Series, eligible: pd.Series) -> pd.Series:
    result = pd.Series(pd.NA, index=condition.index, dtype="Int8")
    result.loc[eligible] = condition.loc[eligible].astype("int8")
    return result


def _transition_summary(
    raw_label: pd.Series,
    sensitivity_label: pd.Series,
    minimum: int,
    digits: int,
) -> dict[str, Any]:
    comparable = raw_label.notna() & sensitivity_label.notna()
    labels = pd.Series("not_comparable", index=raw_label.index, dtype="object")
    for raw_value in (0, 1):
        for converted_value in (0, 1):
            mask = comparable & raw_label.eq(raw_value) & sensitivity_label.eq(converted_value)
            labels.loc[mask] = f"{raw_value}_to_{converted_value}"
    changed = comparable & raw_label.ne(sensitivity_label)
    comparable_count = int(comparable.sum())
    changed_count = int(changed.sum())
    private_comparable = _count(comparable_count, len(raw_label), minimum)
    private_changed = _count(changed_count, comparable_count, minimum)
    return {
        "comparable_count": private_comparable,
        "changed_count": private_changed,
        "agreement_rate": (
            _rate(comparable_count - changed_count, comparable_count, digits)
            if private_comparable is not None and private_changed is not None
            else None
        ),
        "cells": _private_frequency(labels, minimum, True),
    }


def _bp_reproduction_summary(
    official: pd.Series,
    reproduced: pd.Series,
    tolerance: float,
    minimum: int,
    digits: int,
) -> dict[str, Any]:
    official_numeric = _finite(official)
    reproduced_numeric = _finite(reproduced)
    comparable = official_numeric.notna() & reproduced_numeric.notna()
    absolute_error = (official_numeric - reproduced_numeric).abs()
    mismatch = comparable & absolute_error.gt(tolerance)
    comparable_count = int(comparable.sum())
    mismatch_count = int(mismatch.sum())
    private_comparable = _count(comparable_count, len(official), minimum)
    private_mismatch = _count(mismatch_count, comparable_count, minimum)
    result: dict[str, Any] = {
        "tolerance_mmHg": tolerance,
        "comparable_count": private_comparable,
        "mismatch_count": private_mismatch,
        "match_rate": (
            _rate(comparable_count - mismatch_count, comparable_count, digits)
            if private_comparable is not None and private_mismatch is not None
            else None
        ),
    }
    comparable_errors = absolute_error.loc[comparable]
    if len(comparable_errors) >= minimum:
        result["maximum_absolute_error"] = round(float(comparable_errors.max()), digits)
    return result


def _audit_year(
    frame: pd.DataFrame,
    year: int,
    minimum: int,
    digits: int,
    bp_tolerance: float,
) -> dict[str, Any]:
    age = _finite(frame["age"])
    pregnancy = _numeric(frame["HE_prg"])
    nonpregnant_adult = age.ge(19) & pregnancy.isin([0, 8])

    raw_hba1c = _finite(frame["HE_HbA1c"])
    converted_hba1c = convert_hba1c_to_2019_2021_scale(raw_hba1c, year=year)
    fasting = _finite(frame["HE_fst"])
    glucose = _finite(frame["HE_glu"])
    diabetes_eligible = (
        nonpregnant_adult
        & fasting.ge(8)
        & glucose.notna()
        & raw_hba1c.notna()
        & converted_hba1c.notna()
    )
    raw_hba_label = _nullable_label(raw_hba1c.ge(6.5), diabetes_eligible)
    converted_hba_label = _nullable_label(converted_hba1c.ge(6.5), diabetes_eligible)
    raw_diabetes_label = _nullable_label(
        glucose.ge(126) | raw_hba1c.ge(6.5), diabetes_eligible
    )
    converted_diabetes_label = _nullable_label(
        glucose.ge(126) | converted_hba1c.ge(6.5), diabetes_eligible
    )

    raw_sbp = _finite(frame["HE_sbp"])
    raw_dbp = _finite(frame["HE_dbp"])
    converted_bp = convert_microlife_bp_to_greenlight_scale(
        raw_sbp, raw_dbp, age, year=year
    )
    converted_sbp = converted_bp["sbp_greenlight_sensitivity"]
    converted_dbp = converted_bp["dbp_greenlight_sensitivity"]
    bp_eligible = (
        nonpregnant_adult
        & raw_sbp.notna()
        & raw_dbp.notna()
        & converted_sbp.notna()
        & converted_dbp.notna()
    )
    raw_bp_label = _nullable_label(raw_sbp.ge(140) | raw_dbp.ge(90), bp_eligible)
    converted_bp_label = _nullable_label(
        converted_sbp.ge(140) | converted_dbp.ge(90), bp_eligible
    )

    reproduced = reproduce_official_final_bp(frame)
    return {
        "row_count": int(len(frame)),
        "cohorts": {
            "nonpregnant_adult_count": _count(
                int(nonpregnant_adult.sum()), len(frame), minimum
            ),
            "diabetes_measurement_eligible_count": _count(
                int(diabetes_eligible.sum()), len(frame), minimum
            ),
            "blood_pressure_measurement_eligible_count": _count(
                int(bp_eligible.sum()), len(frame), minimum
            ),
            "age_80_top_coded_count": _count(
                int((nonpregnant_adult & age.eq(80)).sum()),
                int(nonpregnant_adult.sum()),
                minimum,
            ),
        },
        "hba1c": {
            "conversion_applied": year >= 2022,
            "raw": _numeric_summary(raw_hba1c, minimum, digits),
            "converted_sensitivity": _numeric_summary(converted_hba1c, minimum, digits),
            "delta_converted_minus_raw": _numeric_summary(
                converted_hba1c - raw_hba1c, minimum, digits
            ),
            "hba1c_threshold_transition": _transition_summary(
                raw_hba_label, converted_hba_label, minimum, digits
            ),
            "diabetes_measurement_label_transition": _transition_summary(
                raw_diabetes_label, converted_diabetes_label, minimum, digits
            ),
        },
        "blood_pressure": {
            "conversion_applied": year >= 2021,
            "official_reproduction": {
                "systolic": _bp_reproduction_summary(
                    raw_sbp,
                    reproduced["HE_sbp_reproduced"],
                    bp_tolerance,
                    minimum,
                    digits,
                ),
                "diastolic": _bp_reproduction_summary(
                    raw_dbp,
                    reproduced["HE_dbp_reproduced"],
                    bp_tolerance,
                    minimum,
                    digits,
                ),
            },
            "raw_sbp": _numeric_summary(raw_sbp, minimum, digits),
            "raw_dbp": _numeric_summary(raw_dbp, minimum, digits),
            "converted_sbp_sensitivity": _numeric_summary(converted_sbp, minimum, digits),
            "converted_dbp_sensitivity": _numeric_summary(converted_dbp, minimum, digits),
            "sbp_delta_converted_minus_raw": _numeric_summary(
                converted_sbp - raw_sbp, minimum, digits
            ),
            "dbp_delta_converted_minus_raw": _numeric_summary(
                converted_dbp - raw_dbp, minimum, digits
            ),
            "hypertension_measurement_label_transition": _transition_summary(
                raw_bp_label, converted_bp_label, minimum, digits
            ),
        },
    }


def _render_markdown(report: Mapping[str, Any]) -> str:
    lines = [
        "# KNHANES 측정값 조화 영향 감사",
        "",
        "> 집계 전용 결과 — 행·ID 미포함, 모델 학습·성능평가 미수행",
        "",
        f"- 구현 버전: `{report['implementation']['version']}`",
        f"- 감사 연도: {', '.join(report['sources'])}",
        "- 주 분석 정책: 공식 원측정값",
        "- 연구자 전환값: 민감도 분석 전용",
        "",
    ]
    for year, source in report["sources"].items():
        hba = source["hba1c"]
        bp = source["blood_pressure"]
        lines.extend(
            [
                f"## {year}",
                "",
                f"- 당뇨 측정 cohort: {source['cohorts']['diabetes_measurement_eligible_count']}",
                f"- HbA1c 전환 적용: {hba['conversion_applied']}",
                f"- HbA1c 기준 재분류: {hba['hba1c_threshold_transition']}",
                f"- 당뇨 측정 라벨 재분류: {hba['diabetes_measurement_label_transition']}",
                f"- 혈압 측정 cohort: {source['cohorts']['blood_pressure_measurement_eligible_count']}",
                f"- 혈압 전환 적용: {bp['conversion_applied']}",
                f"- 수축기 공식 평균 재현: {bp['official_reproduction']['systolic']}",
                f"- 이완기 공식 평균 재현: {bp['official_reproduction']['diastolic']}",
                f"- 고혈압 측정 라벨 재분류: {bp['hypertension_measurement_label_transition']}",
                "",
            ]
        )
    lines.extend(
        [
            "## 해석 제한",
            "",
            "- 전환 후 분포가 이전 연도와 가까워진다는 이유만으로 정책을 선택하지 않는다.",
            "- 혈압 전환식은 나이와 맥압을 포함하므로 주 라벨에 사용하지 않는다.",
            "- 80세 이상은 원시 나이 80으로 top-coding되어 혈압 전환 민감도에 제한이 있다.",
            "- 이 감사는 모델 성능 또는 임상 타당성 평가가 아니다.",
            "- 2023·2024 값은 사용하지 않는다.",
            "",
        ]
    )
    return "\n".join(lines)


def audit_measurement_files(
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
    allowed = set(scope["audit_years"])
    blocked = set(scope.get("reserved_model_test_years", [])) | set(
        scope.get("forbidden_years", [])
    )
    supplied: dict[int, Path] = {}
    for year, path in year_files:
        if year not in allowed or year in blocked:
            raise ValueError(f"Year {year} is not allowed for measurement harmonization audit.")
        if year in supplied:
            raise ValueError(f"Duplicate year {year}.")
        supplied[year] = path.resolve()
    missing = sorted(allowed - set(supplied))
    if missing:
        raise ValueError(f"All measurement audit years are required; missing: {missing}")

    output_json = output_json.resolve()
    output_md = output_md.resolve()
    if output_json == output_md:
        raise ValueError("JSON and Markdown outputs must be different files.")
    for output in (output_json, output_md):
        if output.exists() and not force:
            raise FileExistsError(f"Output exists: {output}. Use --force intentionally.")
        if output in set(supplied.values()):
            raise ValueError("Output cannot overwrite an input file.")

    privacy = contract["privacy"]
    minimum = int(privacy["minimum_cell_count"])
    digits = 4
    tolerance = float(
        contract["blood_pressure"]["official_final_formula"]["tolerance_mmHg"]
    )
    sources: dict[str, Any] = {}
    for year in sorted(supplied):
        path = supplied[year]
        frame, metadata = read_value_columns(path, REQUIRED_COLUMNS, set())
        sources[str(year)] = {
            "filename": path.name,
            "sha256": sha256_file(path),
            **metadata,
            **_audit_year(frame, year, minimum, digits, tolerance),
        }

    implementation = Path(__file__).resolve()
    harmonization_module = implementation.with_name("measurement_harmonization.py")
    report: dict[str, Any] = {
        "schema_version": 1,
        "policy": {
            "aggregate_only": True,
            "participant_rows_included": False,
            "participant_identifiers_included": False,
            "model_training_performed": False,
            "model_performance_evaluation_performed": False,
            "primary_measurement_values": "official_raw_values",
            "converted_values_role": "sensitivity_only",
            "minimum_cell_count": minimum,
            "blocked_years": sorted(blocked),
        },
        "contract": {"filename": contract_path.name, "sha256": sha256_file(contract_path)},
        "implementation": {
            "version": IMPLEMENTATION_VERSION,
            "filename": implementation.name,
            "sha256": sha256_file(implementation),
            "harmonization_module_sha256": sha256_file(harmonization_module),
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
        description="Aggregate-only KNHANES HbA1c/BP harmonization impact audit."
    )
    parser.add_argument("--contract", required=True, type=Path)
    parser.add_argument("--year-file", required=True, action="append", type=parse_year_file)
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-md", required=True, type=Path)
    parser.add_argument("--force", action="store_true")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    audit_measurement_files(
        args.contract,
        args.year_file,
        args.output_json,
        args.output_md,
        force=args.force,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
