from app.models.health import ExerciseHabitSnapshot


class ExerciseHabitRepository:
    def __init__(self):
        self._model = ExerciseHabitSnapshot

    async def create(self, user_id, **fields) -> ExerciseHabitSnapshot:
        """append-only — 항상 새 row (health_input_snapshots와 동일 원칙)."""

        return await self._model.create(user_id=user_id, **fields)

    async def get_latest(self, user_id) -> ExerciseHabitSnapshot | None:
        return await self._model.filter(user_id=user_id).order_by("-recorded_at").first()
