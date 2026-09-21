"""PR21의 마지막 마이그레이션에 nullable 단위 컬럼만 추가했는지 확인한다."""

import ast
from copy import deepcopy
from pathlib import Path

import pytest
from aerich.utils import decompress_dict


@pytest.fixture(scope="session", autouse=True)
def initialize():
    yield


def state(path):
    tree = ast.parse(path.read_text(encoding="utf-8-sig"))
    encoded = next(
        ast.literal_eval(n.value)
        for n in tree.body
        if isinstance(n, ast.Assign) and any(getattr(t, "id", None) == "MODELS_STATE" for t in n.targets)
    )
    return decompress_dict(encoded)


def test_new_migration_preserves_all_pr21_schema_and_history():
    folder = Path(__file__).resolve().parents[1] / "core/db/migrations/models"
    old = state(folder / "22_20260918071716_add_notification_original_times.py")
    new_path = next(folder.glob("23_*_add_xai_strength_unit.py"))
    new = state(new_path)
    key = next(key for key in new if key.endswith(".ExerciseHabitSnapshot"))
    field = next(field for field in new[key]["data_fields"] if field["name"] == "strength_frequency_unit")
    assert field["nullable"] and field["default"] is None
    restored = deepcopy(new)
    restored[key]["data_fields"].remove(field)
    assert restored == old
    assert "UPDATE " not in new_path.read_text(encoding="utf-8")
