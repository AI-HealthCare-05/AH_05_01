from datetime import date

from fastapi import HTTPException, status
from tortoise.exceptions import IntegrityError
from tortoise.transactions import in_transaction

from app.core.logger import default_logger
from app.dtos.cards import CardRevealResponse, CardWindowResponse
from app.models.cards import DailyCardSet
from app.models.users import User
from app.repositories.card_repository import CardRepository
from app.repositories.challenge_repository import ChallengeRepository
from app.repositories.mission_repository import MissionTemplateRepository
from app.services.challenge_service import (
    ChallengeService,
    _effective_duration_seconds,
    _target_duration_seconds,
)


class CardService:
    def __init__(self):
        self.card_repo = CardRepository()
        self.mission_repo = MissionTemplateRepository()
        self.challenge_repo = ChallengeRepository()
        # ⚠️ 2026-09-08 추가: 자정 정산(settle_past_days)을 홈 진입에서 부르기 위해 필요.
        self.challenge_service = ChallengeService()

    async def get_or_create_today(self, user: User, service_date: date) -> CardWindowResponse:
        """문서 §6: "카드 세트 생성 — daily_card_sets · card_options 3건 (일부 실패 시 전체 롤백)"."""

        # ⚠️ 2026-09-08 추가(자정 정산): 홈에 들어올 때마다 지난 날짜의 안 끝난 챌린지를 먼저
        # 마감함. 스케줄러가 없어서 "어제 시작하고 안 끝낸 것"이 계속 ACTIVE로 남아 오늘도
        # 시간을 쌓고 있었음(challenge_service.settle_past_days 주석 참고). 정산이 실패해도
        # 오늘 카드를 못 보여줄 이유는 없으므로 조용히 넘어가고 로그만 남김.
        try:
            await self.challenge_service.settle_past_days(user, service_date)
        except Exception:  # noqa: BLE001 - 정산 실패가 홈 진입을 막지 않게 함
            default_logger.exception("자정 정산 실패: user_id=%s service_date=%s", user.id, service_date)

        card_set = await self.card_repo.get_set_by_date(user.id, service_date)
        if card_set is None:
            try:
                async with in_transaction():
                    templates = await self.mission_repo.pick_weighted_three(user.id)
                    card_set = await self.card_repo.create_set_with_options(user.id, service_date, templates)
            except IntegrityError:
                # ⚠️ 2026-09-03 리뷰 반영: daily_card_sets에 unique_together(user, service_date)가
                # 걸려있는데, 동시에 두 요청이 들어오면 뒤 요청이 이 제약 위반으로 500이 났음.
                # select_option()에는 이미 같은 패턴의 방어가 있었는데(uq_winner_per_set) 여기만
                # 빠져 있었음. 뒤 요청은 에러 대신, 먼저 커밋된 세트를 그냥 다시 읽어서 정상 응답.
                card_set = await self.card_repo.get_set_by_date(user.id, service_date)
            # 방금 만든(또는 경합에서 진 뒤 다시 읽은) 세트를 관계까지 다시 로드
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
                challenge = await self.challenge_repo.create_from_selection(selection, option.mission_template_version)
                # ⚠️ 2026-09-07 반영: 상태전이 정책(G3) - "포기(카드 미선택 상태)" 후 마음을
                # 바꿔 카드를 뽑으면(B19 -> "그래도 진행"), 이제부터는 challenge.state가
                # "포기" 표시를 대신하므로 note.is_given_up을 그대로 두면 challenge는
                # 정상 진행 중인데 홈 화면엔 여전히 "포기"로 남는 모순이 생김 - 여기서 같이 끔.
                from app.models.records import DailyRecordNote

                note = await DailyRecordNote.get_or_none(user_id=user.id, service_date=card_set.service_date)
                if note is not None and note.is_given_up:
                    note.is_given_up = False
                    await note.save(update_fields=["is_given_up", "updated_at"])
        except IntegrityError as exc:
            # uq_winner_per_set 위반 — 이미 다른 옵션이 확정된 상태 (동시 확정 경합)
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 확정된 카드가 있습니다.") from exc

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
                template.line_text_template.replace("{place}", lucky_location or "")
                .replace("{num}", str(template.target_value))
                .replace("{unit}", template.unit)
            )

        # ⚠️ 2026-09-04 반영: 타이머 "이어하기" 시 멈춰있는 것처럼 보이던 버그 - 클라이언트
        # 로컬 카운트 대신 서버가 실제 경과 시간을 계산해서 내려줌.
        # challenge_service._effective_duration_seconds()와 완전히 같은 계산이라(ACTIVE면
        # accumulated_duration_seconds + (지금 - started_at), UTC 기준) 새로 만들지 않고
        # 그대로 재사용 - 두 곳에서 각자 구현하면 타임존 등이 어긋날 위험이 있음.
        elapsed_seconds = _effective_duration_seconds(challenge)

        # ⚠️ 2026-09-07 QA(N2/타이머 리셋) 진단 로그 - "화면 다시 들어가면 1초로 바뀐다"는
        # 재현 보고 확인용. 원인은 확정됐음: target_duration_seconds에 "분" 숫자가 그대로
        # 저장돼서(1분짜리가 1) 여기 elapsed_seconds가 min(실제경과, 1) = 1로 잘렸던 것
        # (models/challenges.py의 duration_seconds_from_target() 주석에 전체 설명).
        # 고친 뒤 확인용으로 target도 같이 찍어둠 - 1분짜리면 target_seconds=60이 나와야 정상.
        # 며칠 굴려보고 문제 없으면 이 로그는 지워도 됨.
        default_logger.warning(
            "[TIMER-DEBUG] reveal: challenge_id=%s state=%s accumulated=%s started_at=%s "
            "target_column=%s target_seconds=%s -> elapsed_seconds=%s",
            challenge.id,
            challenge.state,
            challenge.accumulated_duration_seconds,
            challenge.started_at,
            challenge.target_duration_seconds,
            _target_duration_seconds(challenge),
            elapsed_seconds,
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
            elapsed_seconds=elapsed_seconds,
            accumulated_count=challenge.accumulated_count,
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

        note = await DailyRecordNote.get_or_none(user_id=card_set.user_id, service_date=card_set.service_date)
        is_rest_day = note.is_rest_day if note else False
        # ⚠️ 2026-09-07 반영: 상태전이 정책(G3) - is_rest_day와 같은 자리에서 같이 읽음.
        is_given_up = note.is_given_up if note else False

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
                is_given_up=is_given_up,
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
            is_given_up=is_given_up,
        )
