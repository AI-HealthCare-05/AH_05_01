"""Custodian-only 2023 extension of the frozen canonical ETL v0.1 rules.

The aggregate-audited ``src.canonical_etl`` file is intentionally unchanged. This
module opens only the year gate while holding a process-local lock, calls the same
transformation, and restores the frozen gate immediately afterward. It does not read
or write files, split data, train models, or calculate performance.
"""

from __future__ import annotations

from threading import Lock

import pandas as pd

from . import canonical_etl as frozen


LOCKED_2023_EXTENSION_VERSION = "v0.1"
_YEAR_GATE_LOCK = Lock()


def build_locked_2023_canonical_frame(frame: pd.DataFrame) -> pd.DataFrame:
    """Apply frozen v0.1 transformations to 2023 without changing the frozen file."""

    with _YEAR_GATE_LOCK:
        original_years = frozen.ALLOWED_YEARS
        try:
            frozen.ALLOWED_YEARS = set(original_years) | {2023}
            canonical = frozen.build_canonical_frame(frame, year=2023)
        finally:
            frozen.ALLOWED_YEARS = original_years

    if not canonical["source_year"].eq(2023).all():
        raise RuntimeError("Locked 2023 canonical output contains a non-2023 year.")
    if not canonical["hba1c_harmonization_version"].eq(
        "knhanes_2022_plus_sensitivity_v1"
    ).all():
        raise RuntimeError("Unexpected 2023 HbA1c harmonization branch.")
    if not canonical["bp_harmonization_version"].eq(
        "knhanes_adult_microlife_to_greenlight_v1"
    ).all():
        raise RuntimeError("Unexpected 2023 blood-pressure harmonization branch.")

    canonical["locked_materialization_version"] = LOCKED_2023_EXTENSION_VERSION
    return canonical
