from datetime import date

from fastapi import HTTPException, status
from tortoise.exceptions import IntegrityError
from tortoise.transactions import in_transaction

from app.dtos.cards import CardRevealResponse, CardWindowResponse
from app.models.cards import DailyCardSet
from app.models.users import User
from app.repositories.card_repository import CardRepository
from app.repositories.challenge_repository import ChallengeRepository
from app.repositories.mission_repository import MissionTemplateRepository


class CardService:
    def __init__(self):
        self.card_repo = CardRepository()
        self.mission_repo = MissionTemplateRepository()
        self.challenge_repo = ChallengeRepository()

    async def get_or_create_today(self, user: User, service_date: date) -> CardWindowResponse:
        """문서 §6: "카드 세트 생성 — daily_card_sets · card_options 3건 (일부 실패 시 전체 롤백)"."""

        card_set = await self.card_repo.get_set_by_date(user.id, service_date)
        if card_set is None:
            async with in_transaction():
                templates = await self.mission_repo.pick_weighted_three(user.id)
                card_set = await self.card_repo.create_set_with_options(user.id, service_date, templates)
            # 방금 만든 세트를 관계까지 다시 로드
            card_set = await self.card_repo.get_set_by_date(user.id, service_date)

        return await self._build_window_response(card_set)

    async def select_option(self, user: User, set_id, option_id) -> CardRevealResponse:
        card_set = await DailyCardSet.get_or_none(id=set_id, user_id=user.id)
        if card_set is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="카드 세트를 찾을 수 없습니다.")

        option = await self.card_repo.get_option(option_id)
        if option is None or option.card_set_id != card_set.id:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="카드 옵션을 찾을 수 없습니다.")

        try:
            async with in_transaction():
                selection = await self.card_repo.create_selection(card_set.id, option.id)
                challenge = await self.challenge_repo.create_from_selection(
                    selection, option.mission_template_version
                )
        except IntegrityError as exc:
            # uq_winner_per_set 위반 — 이미 다른 옵션이 확정된 상태 (동시 확정 경합)
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT, detail="이미 확정된 카드가 있습니다."
            ) from exc

        template = option.mission_template_version
        return self._build_reveal_response(challenge, template)

    def _build_reveal_response(self, challenge, template) -> CardRevealResponse:
        """select_option과 재조회(F: 미션 이어하기) 둘 다 이걸 씀 - 로직을 하나로 통일.
        "행운의 위치"는 CSV 후보 목록 중 첫 번째로 고정해서 뽑아서(재현 가능), 별도 저장 없이
        나중에 다시 조회해도 매번 같은 값이 나옴."""

        location_candidates = template.location_candidates or []
        lucky_location = location_candidates[0] if location_candidates else None

        line_text = None
        if template.line_text_template:
            line_text = (
                template.line_text_template
                .replace("{place}", lucky_location or "")
                .replace("{num}", str(template.target_value))
                .replace("{unit}", template.unit)
            )

        return CardRevealResponse(
            challenge_id=challenge.id,
            exec_type=challenge.exec_type,
            title=template.title,
            guide_text=template.guide_text,
            five_element=template.five_element,
            domain=template.domain,
            target_value=template.target_value,
            unit=template.unit,
            state=challenge.state,
            fortune_text=template.fortune_text,
            lucky_location=lucky_location,
            line_text=line_text,
        )

    async def get_reveal_for_challenge(self, user: User, challenge_id) -> CardRevealResponse:
        """F: "미션 이어하기" - 이미 확정된 챌린지의 카드 내용을 다시 보여줌.
        앱을 껐다 켜도(또는 화면을 벗어났다 돌아와도) B06과 같은 내용을 다시 볼 수 있음."""

        from app.models.challenges import Challenge

        challenge = await Challenge.get_or_none(id=challenge_id).prefetch_related(
            "selection__card_option__mission_template_version", "selection__card_set"
        )
        if challenge is None or challenge.selection.card_set.user_id != user.id:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="챌린지를 찾을 수 없습니다.")

        template = challenge.selection.card_option.mission_template_version
        return self._build_reveal_response(challenge, template)

    async def _build_window_response(self, card_set: DailyCardSet) -> CardWindowResponse:
        from app.models.records import DailyRecordNote

        options = sorted(card_set.options, key=lambda o: o.option_index)
        selection = getattr(card_set, "selection", None)

        note = await DailyRecordNote.get_or_none(
            user_id=card_set.user_id, service_date=card_set.service_date
        )
        is_rest_day = note.is_rest_day if note else False

        if selection is None:
            return CardWindowResponse(
                draw_state="AWAITING_SELECTION",
                service_date=card_set.service_date,
                set_id=card_set.id,
                option_back_ids=[o.id for o in options],
                selected_option_id=None,
                challenge_id=None,
                challenge_state=None,
                is_rest_day=is_rest_day,
            )

        from app.models.challenges import Challenge

        challenge = await Challenge.get_or_none(selection_id=selection.id)

        return CardWindowResponse(
            draw_state="SELECTED",
            service_date=card_set.service_date,
            set_id=card_set.id,
            option_back_ids=[o.id for o in options],
            selected_option_id=selection.card_option_id,
            challenge_id=challenge.id if challenge else None,
            challenge_state=challenge.state if challenge else None,
            is_rest_day=is_rest_day,
        )
