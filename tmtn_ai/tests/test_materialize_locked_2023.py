"""Synthetic-only tests for custodian 2023 materialization."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import pytest
import yaml

from src.activity_features import ACTIVITY_BRANCHES
from src.canonical_etl import ALLOWED_YEARS, CanonicalETLError, build_canonical_frame
from src.file_integrity import sha256_file
from src.locked_2023_canonical import build_locked_2023_canonical_frame
from src.materialize_locked_2023 import materialize_locked_2023


BASE_CONTRACT = Path("contracts/locked_2023_materialization_contract_v0_1.yaml")


def _raw_frame(rows: int = 10) -> pd.DataFrame:
    payload: dict[str, list[object]] = {
        "ID": [f"SECRET_2023_{i}" for i in range(rows)],
        "age": [40 + i for i in range(rows)],
        "sex": [2] * rows,
        "wt_itvex": [1.5] * rows,
        "kstrata": [101] * rows,
        "psu": [1] * rows,
        "HE_prg": [0] * rows,
        "HE_ht": [165.0] * rows,
        "HE_wt": [65.0] * rows,
        "HE_wc": [80.0 + i for i in range(rows)],
        "HE_fst": [8.0] * rows,
        "HE_glu": [130.0 if i % 2 else 100.0 for i in range(rows)],
        "HE_HbA1c": [6.6 if i % 2 else 5.5 for i in range(rows)],
        "HE_DM_HbA1c": [3 if i % 2 else 1 for i in range(rows)],
        "HE_sbp": [145.0 if i % 2 else 120.0 for i in range(rows)],
        "HE_dbp": [95.0 if i % 2 else 75.0 for i in range(rows)],
        "HE_sbp2": [145.0 if i % 2 else 120.0 for i in range(rows)],
        "HE_sbp3": [145.0 if i % 2 else 120.0 for i in range(rows)],
        "HE_dbp2": [95.0 if i % 2 else 75.0 for i in range(rows)],
        "HE_dbp3": [95.0 if i % 2 else 75.0 for i in range(rows)],
        "HE_HP": [4 if i % 2 else 1 for i in range(rows)],
        "BE5_1": [3] * rows,
        "pa_aerobic": [0] * rows,
    }
    for participation, days, hours, minutes in ACTIVITY_BRANCHES.values():
        payload[participation] = [2] * rows
        payload[days] = [8] * rows
        payload[hours] = [88] * rows
        payload[minutes] = [88] * rows
    return pd.DataFrame(payload)


def _authorized_contract(tmp_path: Path) -> Path:
    payload = yaml.safe_load(BASE_CONTRACT.read_text(encoding="utf-8-sig"))
    payload["do_not_materialize"] = False
    payload["status"] = "synthetic_test_only"
    payload["materialization_version"] = "synthetic_locked_2023_v1"
    path = tmp_path / "authorized_synthetic.yaml"
    path.write_text(
        yaml.safe_dump(payload, sort_keys=False, allow_unicode=True), encoding="utf-8"
    )
    return path


def test_locked_extension_uses_2023_and_restores_frozen_year_gate() -> None:
    before = set(ALLOWED_YEARS)
    with pytest.raises(CanonicalETLError, match="not allowed"):
        build_canonical_frame(_raw_frame(), year=2023)
    canonical = build_locked_2023_canonical_frame(_raw_frame())
    assert canonical["source_year"].eq(2023).all()
    assert canonical["locked_materialization_version"].eq("v0.1").all()
    assert set(ALLOWED_YEARS) == before


def test_custodian_packages_separate_features_and_labels(tmp_path: Path) -> None:
    contract = _authorized_contract(tmp_path)
    raw = tmp_path / "hn23_synthetic.csv"
    _raw_frame().to_csv(raw, index=False, encoding="utf-8-sig")
    before = sha256_file(raw)

    developer, locked = materialize_locked_2023(
        contract,
        raw,
        tmp_path / "developer",
        tmp_path / "custodian_only",
    )

    for task, label in {
        "waist": "waist_cm",
        "diabetes": "diabetes_measurement_label_raw",
        "hypertension": "hypertension_measurement_label_raw",
    }.items():
        features = pd.read_csv(developer / task / "test_2023_features.csv")
        labels = pd.read_csv(locked / task / "test_2023_labels.csv")
        assert label not in features.columns
        assert list(labels.columns) == ["participant_id", "source_year", label]
        assert set(features["participant_id"]) == set(labels["participant_id"])

    developer_manifest_text = (developer / "developer_manifest.json").read_text(
        encoding="utf-8"
    )
    locked_manifest_text = (locked / "locked_manifest.json").read_text(encoding="utf-8")
    assert "SECRET_2023" not in developer_manifest_text + locked_manifest_text
    assert "positive_count" not in developer_manifest_text + locked_manifest_text
    assert "label_distribution" not in developer_manifest_text + locked_manifest_text
    manifest = json.loads(developer_manifest_text)
    assert manifest["policy"]["full_canonical_rows_exported"] is False
    assert manifest["policy"]["model_evaluation_performed"] is False
    assert sha256_file(raw) == before


def test_review_contract_blocks_before_missing_raw_is_read(tmp_path: Path) -> None:
    with pytest.raises(RuntimeError, match="do_not_materialize: true"):
        materialize_locked_2023(
            BASE_CONTRACT,
            tmp_path / "missing.csv",
            tmp_path / "developer",
            tmp_path / "locked",
        )


def test_output_roots_must_be_separate(tmp_path: Path) -> None:
    contract = _authorized_contract(tmp_path)
    with pytest.raises(ValueError, match="separate and non-nested"):
        materialize_locked_2023(
            contract,
            tmp_path / "missing.csv",
            tmp_path / "same",
            tmp_path / "same" / "locked",
        )


def test_exact_version_cannot_be_overwritten(tmp_path: Path) -> None:
    contract = _authorized_contract(tmp_path)
    raw = tmp_path / "hn23_synthetic.csv"
    _raw_frame().to_csv(raw, index=False, encoding="utf-8-sig")
    developer_root = tmp_path / "developer"
    locked_root = tmp_path / "locked"
    materialize_locked_2023(contract, raw, developer_root, locked_root)
    with pytest.raises(FileExistsError, match="overwrite is prohibited"):
        materialize_locked_2023(contract, raw, developer_root, locked_root)
