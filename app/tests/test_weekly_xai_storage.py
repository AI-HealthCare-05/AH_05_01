"""임시 SQLite DB에서 주간 결과의 저장·최신값 유지·계정 분리를 확인한다."""

import json
from datetime import UTC, date, datetime, timedelta
from pathlib import Path

import pytest
from tortoise import Tortoise

from app.core.db.databases import TORTOISE_APP_MODELS
from app.dtos.personal_xai import PersonalXaiSnapshot
from app.models.users import User
from app.repositories.weekly_xai_repository import WeeklyXaiRepository


@pytest.fixture(scope="session", autouse=True)
def initialize():
    yield


@pytest.mark.asyncio
async def test_weekly_storage_keeps_newest_rejects_late_response_and_survives_new_repository():
    await Tortoise.init(db_url="sqlite://:memory:", modules={"models": TORTOISE_APP_MODELS}, use_tz=True)
    try:
        await Tortoise.generate_schemas()
        first = await User.create(email="history-one@example.invalid")
        second = await User.create(email="history-two@example.invalid")
        source = json.loads((Path(__file__).parent / "fixtures/personal_xai.synthetic.json").read_text(encoding="utf8"))
        source["reference_date"] = "2026-09-20"
        if source.get("activity_comparison"):
            source["activity_comparison"]["reference_date"] = "2026-09-20"
        original = PersonalXaiSnapshot.model_validate(source).model_copy(
            update={"computed_at": datetime(2026, 9, 20, tzinfo=UTC)}
        )
        repo = WeeklyXaiRepository()
        await repo.save(first.id, original)
        await repo.save(first.id, original)
        newer = original.model_copy(
            update={"snapshot_id": "newer", "computed_at": original.computed_at + timedelta(seconds=30)}
        )
        await repo.save(first.id, newer)
        await repo.save(first.id, original)
        await repo.save(second.id, original)
        saved = await WeeklyXaiRepository().list_since(first.id, date(2026, 9, 14), date(2026, 9, 14))
        assert len(saved) == 1 and saved[0].snapshot["snapshot_id"] == "newer"
        other = await repo.list_since(second.id, date(2026, 9, 14), date(2026, 9, 14))
        assert len(other) == 1 and other[0].snapshot["snapshot_id"] == original.snapshot_id
        await User.filter(id=first.id).delete()
        assert not await repo.list_since(first.id, date(2026, 9, 14), date(2026, 9, 14))
        assert len(await repo.list_since(second.id, date(2026, 9, 14), date(2026, 9, 14))) == 1
    finally:
        await Tortoise.close_connections()
