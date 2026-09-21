from datetime import date

from app.models.cards import CardOption, DailyCardSelection, DailyCardSet


class CardRepository:
    def __init__(self):
        self._set_model = DailyCardSet
        self._option_model = CardOption
        self._selection_model = DailyCardSelection

    async def get_set_by_date(self, user_id, service_date: date) -> DailyCardSet | None:
        return await self._set_model.get_or_none(user_id=user_id, service_date=service_date).prefetch_related(
            "options__mission_template_version", "selection__card_option"
        )

    async def create_set_with_options(self, user_id, service_date: date, templates: list) -> DailyCardSet:
        """문서 §6: "daily_card_sets와 card_options는 한 트랜잭션으로 저장한다."
        호출하는 쪽(서비스 레이어)에서 in_transaction()으로 감싸서 써야 함."""

        card_set = await self._set_model.create(user_id=user_id, service_date=service_date)
        for index, template in enumerate(templates, start=1):
            await self._option_model.create(card_set=card_set, mission_template_version=template, option_index=index)
        return card_set

    async def get_option(self, option_id) -> CardOption | None:
        return await self._option_model.get_or_none(id=option_id).prefetch_related(
            "mission_template_version", "card_set"
        )

    async def create_selection(self, card_set_id, card_option_id) -> DailyCardSelection:
        """uq_winner_per_set(card_set OneToOne)이 동시 확정을 막아줌.
        호출부에서 tortoise.exceptions.IntegrityError를 잡아서 409로 변환할 것."""

        return await self._selection_model.create(card_set_id=card_set_id, card_option_id=card_option_id)

    async def get_selection_by_set(self, card_set_id) -> DailyCardSelection | None:
        return await self._selection_model.get_or_none(card_set_id=card_set_id).prefetch_related(
            "card_option__mission_template_version"
        )
