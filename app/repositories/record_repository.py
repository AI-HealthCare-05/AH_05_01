from datetime import date

from app.models.cards import DailyCardSet
from app.models.records import DailyRecordNote


class RecordRepository:
    def __init__(self):
        self._card_set_model = DailyCardSet
        self._note_model = DailyRecordNote

    async def get_card_sets_in_range(self, user_id, start: date, end: date) -> list[DailyCardSet]:
        """selection -> card_option -> mission_template_version, selection -> challenge까지
        한 번에 미리 가져옴 (날짜별로 매번 쿼리 안 날리려고)."""

        return (
            await self._card_set_model.filter(user_id=user_id, service_date__gte=start, service_date__lte=end)
            .prefetch_related("selection__challenge", "selection__card_option__mission_template_version")
        )

    async def get_card_set_by_date(self, user_id, service_date: date) -> DailyCardSet | None:
        return (
            await self._card_set_model.filter(user_id=user_id, service_date=service_date)
            .prefetch_related("selection__challenge", "selection__card_option__mission_template_version")
            .first()
        )

    async def get_earliest_card_set_date(self, user_id) -> date | None:
        first = await self._card_set_model.filter(user_id=user_id).order_by("service_date").first()
        return first.service_date if first else None

    async def get_notes_in_range(self, user_id, start: date, end: date) -> dict[date, DailyRecordNote]:
        notes = await self._note_model.filter(user_id=user_id, service_date__gte=start, service_date__lte=end)
        return {note.service_date: note for note in notes}

    async def get_or_create_note(self, user_id, service_date: date) -> DailyRecordNote:
        note, _ = await self._note_model.get_or_create(user_id=user_id, service_date=service_date)
        return note

    async def count_rest_days_in_range(self, user_id, start: date, end: date) -> int:
        return await self._note_model.filter(
            user_id=user_id, service_date__gte=start, service_date__lte=end, is_rest_day=True
        ).count()
