"""Aggregate-only KNHANES value audit for approved development years.

This module intentionally does not export participant rows or identifiers and does not
perform modeling, feature selection, imputation, splitting, or performance evaluation.
"""

from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path
from typing import Any, Mapping, Sequence

import numpy as np
import pandas as pd
import yaml

from .audit_raw_schema import SUPPORTED_SUFFIXES, parse_year_file
from .file_integrity import sha256_file


LOGGER = logging.getLogger(__name__)
OPERATORS = {"eq", "ne", "ge", "gt", "le", "lt", "in", "not_in", "missing", "not_missing"}
AUDIT_IMPLEMENTATION_VERSION = "v0.2.1-binary-complement-suppression"


def _load_pyreadstat() -> Any:
    try:
        import pyreadstat  # type: ignore[import-not-found]
    except ImportError as exc:
        raise RuntimeError(
            "Reading SAS/SPSS values requires pyreadstat. Install project requirements "
            "in the active virtual environment and rerun."
        ) from exc
    return pyreadstat


def _require_bool_false(mapping: Mapping[str, Any], names: Sequence[str]) -> None:
    unsafe = [name for name in names if mapping.get(name) is not False]
    if unsafe:
        raise ValueError("Privacy switches must be explicitly false: " + ", ".join(unsafe))


def load_value_audit_spec(path: Path) -> dict[str, Any]:
    """Load and strictly validate an aggregate-only value-audit specification."""
    path = path.resolve()
    if not path.is_file():
        raise FileNotFoundError(f"Value audit specification does not exist: {path}")
    try:
        raw = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
    except (UnicodeError, yaml.YAMLError) as exc:
        raise ValueError(f"Could not read value audit specification {path}: {exc}") from exc
    if not isinstance(raw, Mapping):
        raise ValueError("Value audit specification root must be a YAML mapping.")

    years = raw.get("years")
    forbidden_years = raw.get("forbidden_years")
    privacy = raw.get("privacy")
    variables = raw.get("variables")
    source = raw.get("source")
    if not isinstance(years, list) or not years or not all(
        isinstance(year, int) and not isinstance(year, bool) for year in years
    ):
        raise ValueError("'years' must be a non-empty integer list.")
    if len(years) != len(set(years)):
        raise ValueError("'years' contains duplicates.")
    if not isinstance(forbidden_years, list) or not all(
        isinstance(year, int) and not isinstance(year, bool) for year in forbidden_years
    ):
        raise ValueError("'forbidden_years' must be an integer list.")
    if set(years) & set(forbidden_years):
        raise ValueError("Configured audit years overlap forbidden years.")
    if not isinstance(privacy, Mapping) or privacy.get("aggregate_only") is not True:
        raise ValueError("privacy.aggregate_only must be true.")
    _require_bool_false(
        privacy,
        ["allow_sample_rows", "allow_row_identifiers", "allow_raw_value_export"],
    )
    minimum_cell_count = privacy.get("minimum_cell_count")
    if not isinstance(minimum_cell_count, int) or minimum_cell_count < 2:
        raise ValueError("privacy.minimum_cell_count must be an integer of at least 2.")
    if privacy.get("complementary_suppression") is not True:
        raise ValueError("privacy.complementary_suppression must be true.")
    if not isinstance(source, Mapping) or not isinstance(source.get("participant_id_column"), str):
        raise ValueError("source.participant_id_column must be configured.")
    if not isinstance(variables, Mapping) or not variables:
        raise ValueError("'variables' must be a non-empty mapping.")

    for name, definition in variables.items():
        if not isinstance(name, str) or not name or not isinstance(definition, Mapping):
            raise ValueError("Every variable must have a non-empty name and mapping definition.")
        column = definition.get("column")
        kind = definition.get("kind")
        if not isinstance(column, str) or not column:
            raise ValueError(f"Variable '{name}' has an invalid column.")
        if kind not in {"numeric", "categorical"}:
            raise ValueError(f"Variable '{name}' kind must be numeric or categorical.")
        bounds = definition.get("valid_range", definition.get("candidate_valid_range"))
        if bounds is not None and (
            kind != "numeric"
            or not isinstance(bounds, list)
            or len(bounds) != 2
            or not all(isinstance(value, (int, float)) for value in bounds)
            or bounds[0] >= bounds[1]
        ):
            raise ValueError(f"Variable '{name}' has an invalid valid range.")
        expected_by_year = definition.get("expected_codes_by_year")
        if expected_by_year is not None:
            if kind != "categorical" or not isinstance(expected_by_year, Mapping):
                raise ValueError(
                    f"Variable '{name}' expected_codes_by_year must be a mapping for a categorical variable."
                )
            configured_years = set(years)
            mapped_years: set[int] = set()
            for raw_year, codes in expected_by_year.items():
                try:
                    mapped_years.add(int(raw_year))
                except (TypeError, ValueError) as exc:
                    raise ValueError(
                        f"Variable '{name}' expected_codes_by_year contains an invalid year."
                    ) from exc
                if not isinstance(codes, list) or not codes:
                    raise ValueError(
                        f"Variable '{name}' expected_codes_by_year requires non-empty code lists."
                    )
            if mapped_years != configured_years:
                raise ValueError(
                    f"Variable '{name}' expected_codes_by_year must cover every configured year."
                )
        for threshold in definition.get("thresholds", []):
            if not isinstance(threshold, Mapping) or threshold.get("operator") not in OPERATORS:
                raise ValueError(f"Variable '{name}' contains an invalid threshold rule.")

    for collection_name in ("missingness_patterns", "cross_tabs"):
        collection = raw.get(collection_name, [])
        if not isinstance(collection, list):
            raise ValueError(f"'{collection_name}' must be a list.")
        for item in collection:
            if not isinstance(item, Mapping) or not isinstance(item.get("name"), str):
                raise ValueError(f"Every {collection_name} item needs a name.")
            columns = item.get("columns")
            if not isinstance(columns, list) or len(columns) < 2 or not all(
                isinstance(column, str) and column for column in columns
            ):
                raise ValueError(f"{collection_name} '{item.get('name')}' has invalid columns.")

    formula_checks = raw.get("formula_checks", [])
    if not isinstance(formula_checks, list):
        raise ValueError("'formula_checks' must be a list.")
    for check in formula_checks:
        if not isinstance(check, Mapping) or check.get("operation") not in {"bmi", "mean"}:
            raise ValueError("Formula checks support only bmi and mean operations.")
        if not isinstance(check.get("output_column"), str):
            raise ValueError("Every formula check requires output_column.")
        inputs = check.get("input_columns")
        if not isinstance(inputs, list) or not inputs:
            raise ValueError("Every formula check requires input_columns.")
        if not isinstance(check.get("tolerance"), (int, float)) or check["tolerance"] < 0:
            raise ValueError("Every formula check requires a non-negative tolerance.")

    rule_counts = raw.get("rule_counts", [])
    if not isinstance(rule_counts, list):
        raise ValueError("'rule_counts' must be a list.")
    for rule in rule_counts:
        if not isinstance(rule, Mapping) or not isinstance(rule.get("name"), str):
            raise ValueError("Every rule_count needs a name.")
        _validate_condition_group(rule.get("eligibility"), f"rule_count {rule.get('name')}")
        if "positive" in rule:
            _validate_condition_group(rule["positive"], f"rule_count {rule.get('name')} positive")
    return dict(raw)


def _validate_condition_group(group: Any, label: str) -> None:
    if not isinstance(group, Mapping) or len(group) != 1:
        raise ValueError(f"{label} must contain exactly one of all or any.")
    mode, conditions = next(iter(group.items()))
    if mode not in {"all", "any"} or not isinstance(conditions, list) or not conditions:
        raise ValueError(f"{label} must contain a non-empty all/any list.")
    for condition in conditions:
        if not isinstance(condition, Mapping):
            raise ValueError(f"{label} contains an invalid condition.")
        if not isinstance(condition.get("column"), str) or condition.get("operator") not in OPERATORS:
            raise ValueError(f"{label} contains an invalid column or operator.")
        if condition["operator"] not in {"missing", "not_missing"} and "value" not in condition:
            raise ValueError(f"{label} condition requires a value.")


def _columns_from_spec(spec: Mapping[str, Any]) -> tuple[set[str], set[str]]:
    required: set[str] = set()
    optional: set[str] = set()
    id_column = spec["source"]["participant_id_column"]
    (required if spec["source"].get("id_required", True) else optional).add(id_column)
    for definition in spec["variables"].values():
        target = required if definition.get("required", False) else optional
        target.add(definition["column"])
    for name in ("missingness_patterns", "cross_tabs"):
        for item in spec.get(name, []):
            optional.update(item["columns"])
    for check in spec.get("formula_checks", []):
        optional.add(check["output_column"])
        optional.update(check["input_columns"])
    for rule in spec.get("rule_counts", []):
        for group_name in ("eligibility", "positive"):
            if group_name not in rule:
                continue
            conditions = next(iter(rule[group_name].values()))
            optional.update(condition["column"] for condition in conditions)
    optional -= required
    return required, optional


def _available_columns(path: Path) -> tuple[list[str], str, str | None]:
    suffix = path.suffix.lower()
    if suffix == ".csv":
        errors: list[str] = []
        for encoding in ("utf-8-sig", "cp949"):
            try:
                header = pd.read_csv(path, nrows=0, encoding=encoding)
            except (UnicodeError, pd.errors.ParserError) as exc:
                errors.append(f"{encoding}: {exc}")
                continue
            return [str(column) for column in header.columns], "csv", encoding
        raise ValueError(f"Could not read CSV header for {path.name}: {' | '.join(errors)}")
    pyreadstat = _load_pyreadstat()
    if suffix == ".sas7bdat":
        _, metadata = pyreadstat.read_sas7bdat(str(path), metadataonly=True)
        return list(metadata.column_names), "sas7bdat", None
    if suffix == ".xpt":
        _, metadata = pyreadstat.read_xport(str(path), metadataonly=True)
        return list(metadata.column_names), "xpt", None
    if suffix == ".sav":
        _, metadata = pyreadstat.read_sav(str(path), metadataonly=True)
        return list(metadata.column_names), "sav", None
    raise ValueError(
        f"Unsupported raw file format '{suffix}'. Supported formats: {', '.join(sorted(SUPPORTED_SUFFIXES))}."
    )


def read_value_columns(path: Path, required: set[str], optional: set[str]) -> tuple[pd.DataFrame, dict[str, Any]]:
    """Read only configured columns and fail before row loading if a required column is absent."""
    path = path.resolve()
    if not path.is_file():
        raise FileNotFoundError(f"Raw data file does not exist: {path}")
    if path.suffix.lower() not in SUPPORTED_SUFFIXES:
        raise ValueError(f"Unsupported raw file format: {path.suffix}")
    available, file_format, encoding = _available_columns(path)
    available_set = set(available)
    missing_required = sorted(required - available_set)
    if missing_required:
        raise ValueError(f"Required value-audit columns missing from {path.name}: {missing_required}")
    selected = sorted((required | optional) & available_set)
    if file_format == "csv":
        frame = pd.read_csv(path, usecols=selected, encoding=encoding, low_memory=False)
    else:
        pyreadstat = _load_pyreadstat()
        if file_format == "sas7bdat":
            frame, _ = pyreadstat.read_sas7bdat(str(path), usecols=selected)
        elif file_format == "xpt":
            frame, _ = pyreadstat.read_xport(str(path), usecols=selected)
        else:
            frame, _ = pyreadstat.read_sav(str(path), usecols=selected)
    return frame, {
        "file_format": file_format,
        "encoding": encoding,
        "selected_column_count": len(selected),
        "missing_optional_columns": sorted(optional - available_set),
    }


def _json_scalar(value: Any) -> str:
    if pd.isna(value):
        return "(missing)"
    if isinstance(value, (np.integer, int)):
        return str(int(value))
    if isinstance(value, (np.floating, float)) and float(value).is_integer():
        return str(int(value))
    return str(value)


def _private_frequency(series: pd.Series, minimum: int, complementary: bool) -> list[dict[str, Any]]:
    counts = series.map(_json_scalar).value_counts(dropna=False).sort_index()
    suppress = {label for label, count in counts.items() if 0 < int(count) < minimum}
    if suppress and complementary:
        visible = [(label, int(count)) for label, count in counts.items() if label not in suppress]
        if visible:
            suppress.add(min(visible, key=lambda item: (item[1], item[0]))[0])
    return [
        {
            "value": label,
            "count": None if label in suppress else int(count),
            "suppressed": label in suppress,
        }
        for label, count in counts.items()
    ]


def _special_codes(definition: Mapping[str, Any]) -> Sequence[Any]:
    return definition.get("special_codes", definition.get("candidate_special_codes", []))


def _candidate_special_mask(series: pd.Series, definition: Mapping[str, Any]) -> pd.Series:
    codes = _special_codes(definition)
    if not codes:
        return pd.Series(False, index=series.index)
    numeric = pd.to_numeric(series, errors="coerce")
    return numeric.isin(codes)


def _analysis_series(frame: pd.DataFrame, column: str, by_column: Mapping[str, Mapping[str, Any]]) -> pd.Series:
    series = frame[column]
    definition = by_column.get(column)
    if definition is not None and definition.get("kind") == "numeric":
        numeric = pd.to_numeric(series, errors="coerce")
        return numeric.mask(_candidate_special_mask(series, definition))
    return series


def _private_count(value: int, minimum: int) -> int | None:
    return None if 0 < value < minimum else value


def _private_partition_count(value: int, total: int, minimum: int) -> int | None:
    """Suppress either side of a binary partition when one side is a small cell."""
    complement = total - value
    if (0 < value < minimum) or (0 < complement < minimum):
        return None
    return value


def _rate(numerator: int, denominator: int, digits: int) -> float | None:
    return None if denominator == 0 else round(numerator / denominator, digits)


def audit_variable(
    frame: pd.DataFrame,
    definition: Mapping[str, Any],
    *,
    year: int,
    minimum: int,
    complementary: bool,
    digits: int,
) -> dict[str, Any]:
    column = definition["column"]
    series = frame[column]
    missing_count = int(series.isna().sum())
    private_missing_count = _private_partition_count(missing_count, len(series), minimum)
    report: dict[str, Any] = {
        "column": column,
        "kind": definition["kind"],
        "row_count": int(len(series)),
        "missing_count": private_missing_count,
        "missing_rate": (
            _rate(missing_count, len(series), digits) if private_missing_count is not None else None
        ),
    }
    special_mask = _candidate_special_mask(series, definition)
    special_values = series[special_mask]
    special_codes = _special_codes(definition)
    if special_codes:
        key_prefix = "special" if definition.get("special_codes") is not None else "candidate_special"
        report[f"{key_prefix}_codes"] = list(special_codes)
        report[f"{key_prefix}_code_counts"] = _private_frequency(
            special_values, minimum, complementary
        )
        report[f"{key_prefix}_codes_excluded_from_numeric_checks"] = True

    if definition["kind"] == "categorical":
        report["observed_code_counts"] = _private_frequency(series, minimum, complementary)
        expected_by_year = definition.get("expected_codes_by_year")
        expected = (
            expected_by_year.get(year, expected_by_year.get(str(year)))
            if expected_by_year is not None
            else definition.get("expected_codes")
        )
        if expected is not None:
            numeric = pd.to_numeric(series, errors="coerce")
            unexpected = int((series.notna() & ~numeric.isin(expected)).sum())
            report["expected_codes"] = list(expected)
            nonmissing_count = int(series.notna().sum())
            report["unexpected_code_count"] = _private_partition_count(
                unexpected, nonmissing_count, minimum
            )
        return report

    numeric = pd.to_numeric(series, errors="coerce")
    conversion_failures = int((series.notna() & numeric.isna()).sum())
    analysis = numeric.mask(special_mask).dropna()
    report["numeric_conversion_failure_count"] = _private_count(conversion_failures, minimum)
    report["analysis_value_count"] = int(len(analysis))
    if len(analysis) >= minimum:
        quantiles = analysis.quantile([0.01, 0.25, 0.5, 0.75, 0.99])
        report["quantiles"] = {
            "p01": round(float(quantiles.loc[0.01]), digits),
            "p25": round(float(quantiles.loc[0.25]), digits),
            "p50": round(float(quantiles.loc[0.5]), digits),
            "p75": round(float(quantiles.loc[0.75]), digits),
            "p99": round(float(quantiles.loc[0.99]), digits),
        }
    confirmed_bounds = definition.get("valid_range")
    bounds = confirmed_bounds if confirmed_bounds is not None else definition.get("candidate_valid_range")
    if bounds is not None:
        below = int((analysis < bounds[0]).sum())
        above = int((analysis > bounds[1]).sum())
        if confirmed_bounds is not None:
            report["valid_range"] = list(bounds)
            report["below_valid_range_count"] = _private_count(below, minimum)
            report["above_valid_range_count"] = _private_count(above, minimum)
        else:
            report["candidate_valid_range"] = list(bounds)
            report["below_candidate_range_count"] = _private_count(below, minimum)
            report["above_candidate_range_count"] = _private_count(above, minimum)
    if definition.get("positive_only"):
        nonpositive = int((analysis <= 0).sum())
        report["nonpositive_count"] = _private_partition_count(
            nonpositive, len(analysis), minimum
        )
    thresholds: dict[str, Any] = {}
    for threshold in definition.get("thresholds", []):
        mask = _compare(analysis, threshold["operator"], threshold.get("value"))
        count = int(mask.sum())
        private_threshold_count = _private_partition_count(count, len(analysis), minimum)
        thresholds[threshold["name"]] = {
            "operator": threshold["operator"],
            "value": threshold.get("value"),
            "count": private_threshold_count,
            "rate_among_analysis_values": (
                _rate(count, len(analysis), digits)
                if private_threshold_count is not None
                else None
            ),
        }
    if thresholds:
        report["thresholds"] = thresholds
    return report


def _compare(series: pd.Series, operator: str, value: Any = None) -> pd.Series:
    if operator == "missing":
        return series.isna()
    if operator == "not_missing":
        return series.notna()
    if operator in {"in", "not_in"}:
        values = value if isinstance(value, list) else [value]
        result = series.isin(values)
        return ~result if operator == "not_in" else result
    if operator == "eq":
        return series == value
    if operator == "ne":
        return series != value
    numeric = pd.to_numeric(series, errors="coerce")
    if operator == "ge":
        return numeric >= value
    if operator == "gt":
        return numeric > value
    if operator == "le":
        return numeric <= value
    if operator == "lt":
        return numeric < value
    raise ValueError(f"Unsupported operator: {operator}")


def _condition_group(
    frame: pd.DataFrame,
    group: Mapping[str, Any],
    by_column: Mapping[str, Mapping[str, Any]],
) -> pd.Series:
    mode, conditions = next(iter(group.items()))
    masks = [
        _compare(
            _analysis_series(frame, condition["column"], by_column),
            condition["operator"],
            condition.get("value"),
        ).fillna(False)
        for condition in conditions
    ]
    result = masks[0]
    for mask in masks[1:]:
        result = (result & mask) if mode == "all" else (result | mask)
    return result


def audit_rule_count(
    frame: pd.DataFrame,
    rule: Mapping[str, Any],
    by_column: Mapping[str, Mapping[str, Any]],
    minimum: int,
    digits: int,
) -> dict[str, Any]:
    eligible = _condition_group(frame, rule["eligibility"], by_column)
    eligible_count = int(eligible.sum())
    private_eligible_count = _private_partition_count(eligible_count, len(frame), minimum)
    report: dict[str, Any] = {
        "pregnancy_exclusion_applied": bool(rule.get("pregnancy_exclusion_applied", False)),
        "eligible_count": private_eligible_count,
        "eligible_rate": (
            _rate(eligible_count, len(frame), digits)
            if private_eligible_count is not None
            else None
        ),
    }
    if "positive" in rule:
        positive = eligible & _condition_group(frame, rule["positive"], by_column)
        positive_count = int(positive.sum())
        private_positive_count = _private_partition_count(
            positive_count, eligible_count, minimum
        )
        report["positive_count"] = private_positive_count
        report["positive_rate_among_eligible"] = (
            _rate(positive_count, eligible_count, digits)
            if private_eligible_count is not None and private_positive_count is not None
            else None
        )
    return report


def audit_missingness_pattern(
    frame: pd.DataFrame, columns: Sequence[str], minimum: int, complementary: bool
) -> dict[str, Any]:
    missing = frame.loc[:, columns].isna()
    labels = missing.apply(
        lambda row: "|".join(
            f"{column}={'missing' if bool(row[column]) else 'present'}" for column in columns
        ),
        axis=1,
    )
    return {"columns": list(columns), "patterns": _private_frequency(labels, minimum, complementary)}


def audit_cross_tab(
    frame: pd.DataFrame, columns: Sequence[str], minimum: int, complementary: bool
) -> dict[str, Any]:
    labels = frame.loc[:, columns].apply(
        lambda row: "|".join(f"{column}={_json_scalar(row[column])}" for column in columns),
        axis=1,
    )
    return {"columns": list(columns), "cells": _private_frequency(labels, minimum, complementary)}


def audit_formula_check(
    frame: pd.DataFrame,
    check: Mapping[str, Any],
    by_column: Mapping[str, Mapping[str, Any]],
    minimum: int,
    digits: int,
) -> dict[str, Any]:
    inputs = [pd.to_numeric(_analysis_series(frame, column, by_column), errors="coerce") for column in check["input_columns"]]
    output = pd.to_numeric(_analysis_series(frame, check["output_column"], by_column), errors="coerce")
    operation = check["operation"]
    if operation == "bmi":
        if len(inputs) != 2:
            raise ValueError("BMI formula check requires [height_cm, weight_kg].")
        calculated = inputs[1] / ((inputs[0] / 100.0) ** 2)
    else:
        calculated = pd.concat(inputs, axis=1).mean(axis=1, skipna=False)
    valid = output.notna() & calculated.notna() & np.isfinite(calculated)
    difference = (output[valid] - calculated[valid]).abs()
    mismatch = int((difference > float(check["tolerance"])).sum())
    eligible_count = int(valid.sum())
    private_mismatch_count = _private_partition_count(mismatch, eligible_count, minimum)
    return {
        "operation": operation,
        "output_column": check["output_column"],
        "input_columns": list(check["input_columns"]),
        "tolerance": check["tolerance"],
        "eligible_count": eligible_count,
        "mismatch_count": private_mismatch_count,
        "match_rate": (
            _rate(eligible_count - mismatch, eligible_count, digits)
            if private_mismatch_count is not None
            else None
        ),
        "mean_absolute_difference": (
            round(float(difference.mean()), digits) if eligible_count >= minimum else None
        ),
    }


def _audit_one_year(frame: pd.DataFrame, spec: Mapping[str, Any], year: int) -> dict[str, Any]:
    privacy = spec["privacy"]
    minimum = int(privacy["minimum_cell_count"])
    complementary = bool(privacy["complementary_suppression"])
    digits = int(privacy.get("round_digits", 4))
    id_column = spec["source"]["participant_id_column"]
    id_missing = int(frame[id_column].isna().sum())
    id_duplicates = int(frame[id_column].notna().sum() - frame[id_column].dropna().nunique())
    by_column = {definition["column"]: definition for definition in spec["variables"].values()}

    variables = {
        name: audit_variable(
            frame,
            definition,
            year=year,
            minimum=minimum,
            complementary=complementary,
            digits=digits,
        )
        for name, definition in spec["variables"].items()
        if definition["column"] in frame.columns
    }
    missingness_patterns = {
        item["name"]: audit_missingness_pattern(frame, item["columns"], minimum, complementary)
        for item in spec.get("missingness_patterns", [])
        if set(item["columns"]).issubset(frame.columns)
    }
    cross_tabs = {
        item["name"]: audit_cross_tab(frame, item["columns"], minimum, complementary)
        for item in spec.get("cross_tabs", [])
        if set(item["columns"]).issubset(frame.columns)
    }
    formula_checks = {
        check["name"]: audit_formula_check(frame, check, by_column, minimum, digits)
        for check in spec.get("formula_checks", [])
        if {check["output_column"], *check["input_columns"]}.issubset(frame.columns)
    }
    rule_counts = {
        rule["name"]: audit_rule_count(frame, rule, by_column, minimum, digits)
        for rule in spec.get("rule_counts", [])
    }
    return {
        "row_count": int(len(frame)),
        "id_quality": {
            "column": id_column,
            "missing_count": _private_partition_count(id_missing, len(frame), minimum),
            "duplicate_count": _private_count(id_duplicates, minimum),
            "raw_identifiers_included": False,
        },
        "variables": variables,
        "missingness_patterns": missingness_patterns,
        "cross_tabs": cross_tabs,
        "formula_checks": formula_checks,
        "rule_counts": rule_counts,
    }


def render_markdown_report(report: Mapping[str, Any]) -> str:
    """Render a compact human-reviewable report without participant rows."""
    lines = [
        "# KNHANES 2019~2023 값 수준 감사 보고서",
        "",
        f"> 감사 버전: `{report.get('audit_version')}`  ",
        "> 상태: 코드 실행 결과 — 모델 학습 결과 아님  ",
        "> 개인정보 정책: 집계값만 포함, 원시 행·ID 미포함",
        "",
        "## 실행 정책",
        "",
        f"- 감사 연도: {', '.join(map(str, report['policy']['audit_years']))}",
        f"- 금지 연도: {', '.join(map(str, report['policy']['forbidden_years']))}",
        f"- 최소 셀 크기: {report['policy']['minimum_cell_count']}",
        f"- 누락된 설정 연도: {report['missing_configured_years'] or '없음'}",
        "",
    ]
    for year, source in report["sources"].items():
        lines.extend(
            [
                f"## {year}년",
                "",
                f"- 파일: `{source['filename']}`",
                f"- 행 수: {source['row_count']}",
                f"- 선택 컬럼 수: {source['selected_column_count']}",
                f"- 선택적 누락 컬럼: {source['missing_optional_columns'] or '없음'}",
                "",
                "### 변수 요약",
                "",
                "| 개념 | 컬럼 | 유형 | 결측 수 | 결측률 | 범위 아래 | 범위 위 |",
                "|---|---|---|---:|---:|---:|---:|",
            ]
        )
        for name, variable in source["variables"].items():
            lines.append(
                "| {name} | `{column}` | {kind} | {missing} | {rate} | {below} | {above} |".format(
                    name=name,
                    column=variable["column"],
                    kind=variable["kind"],
                    missing=variable["missing_count"],
                    rate=variable["missing_rate"],
                    below=variable.get("below_candidate_range_count", "-") ,
                    above=variable.get("above_candidate_range_count", "-"),
                )
            )
        lines.extend(["", "### 공식값 재현 검사", ""])
        for name, check in source["formula_checks"].items():
            lines.append(
                f"- {name}: eligible={check['eligible_count']}, mismatch={check['mismatch_count']}, "
                f"match_rate={check['match_rate']}"
            )
        lines.extend(["", "### 후보 cohort·측정 이상 집계", ""])
        for name, rule in source["rule_counts"].items():
            detail = f"eligible={rule['eligible_count']}"
            if "positive_count" in rule:
                detail += (
                    f", positive={rule['positive_count']}, "
                    f"positive_rate={rule['positive_rate_among_eligible']}"
                )
            lines.append(
                f"- {name}: {detail}; pregnancy_exclusion_applied="
                f"{str(rule['pregnancy_exclusion_applied']).lower()}"
            )
        lines.append("")
    lines.extend(
        [
            "## 해석 제한",
            "",
            "- 본 보고서는 코드·결측·범위·공식 파생값 재현을 위한 값 감사다.",
            "- `pregnancy_exclusion_applied=false`인 후보 집계는 최종 cohort가 아니다.",
            "- 결과를 모델 성능, 임상 진단 또는 미래 위험으로 해석하지 않는다.",
            "- 이 보고서만으로 `do_not_train`을 해제하지 않는다.",
            "",
        ]
    )
    return "\n".join(lines)


def audit_value_files(
    spec_path: Path,
    year_files: Sequence[tuple[int, Path]],
    output_json: Path,
    output_markdown: Path,
    *,
    allow_partial: bool = False,
    force: bool = False,
) -> tuple[Path, Path]:
    """Run an aggregate-only value audit and write deterministic JSON and Markdown reports."""
    spec_path = spec_path.resolve()
    output_json = output_json.resolve()
    output_markdown = output_markdown.resolve()
    if output_json == output_markdown:
        raise ValueError("JSON and Markdown outputs must be different files.")
    for output in (output_json, output_markdown):
        if output.exists() and not force:
            raise FileExistsError(f"Audit output already exists: {output}. Use --force intentionally.")

    spec = load_value_audit_spec(spec_path)
    expected = set(spec["years"])
    forbidden = set(spec["forbidden_years"])
    supplied: dict[int, Path] = {}
    for year, path in year_files:
        if year in supplied:
            raise ValueError(f"Duplicate --year-file entry for {year}.")
        if year in forbidden:
            raise ValueError(f"Year {year} is forbidden by the value-audit specification.")
        if year not in expected:
            raise ValueError(f"Year {year} is not configured for this value audit.")
        supplied[year] = path.resolve()
    if not supplied:
        raise ValueError("At least one --year-file entry is required.")
    missing_years = sorted(expected - set(supplied))
    if missing_years and not allow_partial:
        raise ValueError(
            f"Missing files for configured years: {missing_years}. Supply all years or use --allow-partial."
        )
    input_paths = set(supplied.values())
    if output_json in input_paths or output_markdown in input_paths:
        raise ValueError("Audit outputs cannot overwrite raw input files.")

    required, optional = _columns_from_spec(spec)
    sources: dict[str, Any] = {}
    for year in sorted(supplied):
        source_path = supplied[year]
        frame, metadata = read_value_columns(source_path, required, optional)
        year_report = _audit_one_year(frame, spec, year)
        sources[str(year)] = {
            "filename": source_path.name,
            "sha256": sha256_file(source_path),
            **metadata,
            **year_report,
        }

    implementation_path = Path(__file__).resolve()
    report: dict[str, Any] = {
        "schema_version": 2,
        "audit_version": spec.get("audit_version"),
        "audit_implementation": {
            "version": AUDIT_IMPLEMENTATION_VERSION,
            "filename": implementation_path.name,
            "sha256": sha256_file(implementation_path),
        },
        "survey": spec.get("survey"),
        "specification": {"filename": spec_path.name, "sha256": sha256_file(spec_path)},
        "policy": {
            "aggregate_only": True,
            "participant_rows_included": False,
            "participant_identifiers_included": False,
            "model_training_performed": False,
            "audit_years": list(spec["years"]),
            "forbidden_years": list(spec["forbidden_years"]),
            "minimum_cell_count": spec["privacy"]["minimum_cell_count"],
            "complementary_suppression": True,
            "binary_partition_complementary_suppression": True,
        },
        "missing_configured_years": missing_years,
        "sources": sources,
    }
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_markdown.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    output_markdown.write_text(render_markdown_report(report), encoding="utf-8-sig")
    LOGGER.info("Aggregate value audit written to %s and %s", output_json, output_markdown)
    return output_json, output_markdown


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Audit KNHANES participant values using aggregate-only reports. This command "
            "does not export rows or IDs and does not split, train, tune, or evaluate models."
        )
    )
    parser.add_argument("--spec", required=True, type=Path, help="Value audit YAML path.")
    parser.add_argument(
        "--year-file",
        required=True,
        action="append",
        type=parse_year_file,
        metavar="YEAR=PATH",
        help="Raw development-year file. Repeat once per configured year.",
    )
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-md", required=True, type=Path)
    parser.add_argument("--allow-partial", action="store_true")
    parser.add_argument("--force", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    args = build_argument_parser().parse_args(argv)
    try:
        audit_value_files(
            args.spec,
            args.year_file,
            args.output_json,
            args.output_md,
            allow_partial=args.allow_partial,
            force=args.force,
        )
    except (FileNotFoundError, FileExistsError, ValueError, RuntimeError) as exc:
        LOGGER.error("%s", exc)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
