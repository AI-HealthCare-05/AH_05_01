"""한 주의 가장 최근 계산만 보관하고 동시 응답의 역순 저장을 막는다."""

from datetime import date, timedelta

from app.dtos.personal_xai import PersonalXaiSnapshot
from app.models.personal_xai import WeeklyXaiSnapshot


class WeeklyXaiRepository:
    async def save(self, user_id: int, snapshot: PersonalXaiSnapshot):
        observed = date.fromisoformat(snapshot.reference_date)
        monday = observed - timedelta(days=observed.weekday())
        values = {
            "observed_on": observed,
            "computed_at": snapshot.computed_at,
            "snapshot": snapshot.model_dump(mode="json"),
        }
        row, created = await WeeklyXaiSnapshot.get_or_create(user_id=user_id, week_start=monday, defaults=values)
        if not created:
            await WeeklyXaiSnapshot.filter(id=row.id, computed_at__lt=snapshot.computed_at).update(**values)

    async def list_since(self, user_id: int, start: date, end: date):
        return await WeeklyXaiSnapshot.filter(user_id=user_id, week_start__gte=start, week_start__lte=end).order_by(
            "week_start"
        )
