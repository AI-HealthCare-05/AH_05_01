from datetime import datetime

from app.core import config
from app.models.health import HealthInputSnapshot


class HealthInputRepository:
    def __init__(self):
        self._model = HealthInputSnapshot

    async def create(self, user_id, input_values: dict, units: dict, source: str, measured_at=None):
        """append-only — UPDATE 없이 항상 새 row (ERD 문서 근거)."""

        return await self._model.create(
            user_id=user_id,
            input_values=input_values,
            units=units,
            source=source,
            measured_at=measured_at or datetime.now(config.TIMEZONE),
        )

    async def get_latest(self, user_id) -> HealthInputSnapshot | None:
        return await self._model.filter(user_id=user_id).order_by("-measured_at").first()

    async def get_history(self, user_id, limit: int = 20) -> list[HealthInputSnapshot]:
        return await self._model.filter(user_id=user_id).order_by("-measured_at").limit(limit)
