from tortoise.expressions import F

from app.models.challenges import (
    Challenge,
    ChallengeEvent,
    ChallengeState,
    PointLedger,
    duration_seconds_from_target,
)


class ChallengeRepository:
    def __init__(self):
        self._model = Challenge
        self._event_model = ChallengeEvent
        self._ledger_model = PointLedger

    async def get_by_id(self, challenge_id) -> Challenge | None:
        return await self._model.get_or_none(id=challenge_id).prefetch_related(
            "selection__card_option__mission_template_version"
        )

    async def create_from_selection(self, selection, template) -> Challenge:
        """카드 확정 시 challenge를 생성. exec_type별로 target 필드를 다르게 채움."""

        target_duration_seconds = None
        target_count = None
        if template.exec_type in ("TIMER", "SENSOR_RUNNING_DURATION", "SENSOR_WALKING_DURATION"):
            # ⚠️ 2026-09-07 반영: 예전엔 template.target_value를 그대로 넣어서, 단위가 "분"인
            # 미션(SELF_TIMER 42개 중 38개)이 "1분 -> 1초"로 저장됐음. 이 컬럼은 이름 그대로
            # 항상 초 단위여야 함 - duration_seconds_from_target() 주석에 원인 전체 설명.
            target_duration_seconds = duration_seconds_from_target(template.target_value, template.unit)
        elif template.exec_type in (
            "SENSOR_STEPS",
            "SENSOR_FLOORS_CLIMBED",
            "SENSOR_STEPS_IN_PLACE",
            "SENSOR_RUNNING_DISTANCE",
        ):
            target_count = template.target_value
        # CHECK은 별도 목표값 없이 완료 버튼만으로 처리

        return await self._model.create(
            selection=selection,
            exec_type=template.exec_type,
            mission_snapshot={
                "title": template.title,
                "guide_text": template.guide_text,
                "five_element": template.five_element,
                "target_value": template.target_value,
                "unit": template.unit,
            },
            state=ChallengeState.READY,  # ⚠️ 2026-09-01: 카드 확정=시작이 아님. "시작하기"를
            # 눌러야만(challenge_service.start) ACTIVE로 바뀜 — 실제 GPS/센서 접근도 그때부터.
            target_duration_seconds=target_duration_seconds,
            target_count=target_count,
        )

    async def try_transition(self, challenge: Challenge, *, from_states: list[str], to_state: str) -> bool:
        """ERD 문서 §6의 낙관적 잠금 그대로 구현.
        UPDATE ... WHERE id=? AND version=? AND state IN (...) — affected rows 0이면 실패."""

        current_version = challenge.version
        updated_count = await self._model.filter(
            id=challenge.id, version=current_version, state__in=from_states
        ).update(state=to_state, version=current_version + 1)
        return updated_count > 0

    #: 자정 정산 대상 상태. settle_past_days()의 try_transition(from_states=...)과 반드시
    #: 같은 집합이어야 함 - 여기서 찾아놓고 저기서 못 넘기면 매번 조회만 하고 아무것도 안 됨.
    UNSETTLED_STATES = [ChallengeState.READY, ChallengeState.ACTIVE, ChallengeState.PAUSED]

    async def find_unsettled_before(self, user_id, service_date) -> list[Challenge]:
        """⚠️ 2026-09-08 추가(자정 정산): 지난 날짜인데 아직 안 끝난 챌린지들.

        스케줄러·배치가 없어서 "어제 시작하고 안 끝낸 것"이 계속 살아있었음 - 실제로 DB에
        9/4에 시작한 챌린지가 9/8까지 ACTIVE로 남아 시간을 계속 쌓고 있었음. 요청이 들어올 때
        이 목록을 찾아서 그 자리에서 마감하면 별도 워커 없이도 같은 효과를 냄.
        COMPLETED/SKIPPED는 이미 끝난 것이라 대상이 아님.

        ⚠️ 2026-09-08 2차 반영: 처음엔 ACTIVE/PAUSED만 봤는데, 그러면 "카드만 뽑고 시작은
        안 한 날"(READY)이 자정을 넘겨도 영원히 READY로 남았음 - 어제 것이 끝나지 않은 채로
        DB에 계속 쌓임. 시작하지 않은 것도 그날이 지나면 끝난 것이므로 정산 대상에 포함함.
        """

        return await self._model.filter(
            state__in=self.UNSETTLED_STATES,
            selection__card_set__user_id=user_id,
            selection__card_set__service_date__lt=service_date,
        ).prefetch_related("selection__card_set")

    async def reset_progress(self, challenge_id) -> None:
        """⚠️ 2026-09-08 추가(포기 = 진행값 폐기): 포기(skip)한 챌린지의 진행값을 0으로 되돌림.

        예전엔 skip()이 state만 바꾸고 진행값은 손대지 않았는데, 그러면 "포기 전에 일시정지를
        눌렀는지"에 따라 결과가 달라졌음:
          - 진행 8분 -> 그냥 포기       : accumulated가 0인 채 굳음(한 번도 확정 저장이 안 됨)
          - 진행 8분 -> 일시정지 -> 포기 : accumulated=480이 그대로 남아, 다시 도전하면 8분부터 시작
        "포기하면 그때까지 달성한 건 없앤다"는 정책에 맞춰 두 경로 모두 0에서 다시 시작하도록
        명시적으로 초기화함. 없앤 값 자체는 SKIP 이벤트 payload에 남기므로 기록은 보존됨
        (challenge_events는 append-only - challenge_service.skip() 참고).
        """

        await self._model.filter(id=challenge_id).update(
            accumulated_duration_seconds=0,
            accumulated_count=0,
            started_at=None,
            last_paused_at=None,
        )

    async def update_started_at(self, challenge_id, started_at) -> None:
        """C02/C09 "시작하기" - 지금 구간이 시작된 시각 기록."""
        await self._model.filter(id=challenge_id).update(started_at=started_at)

    async def add_accumulated_duration_and_clear_start(self, challenge_id, extra_seconds: int) -> None:
        """C03 "일시정지" - 이번 구간에서 흐른 시간을 누적값에 더하고, started_at은 비움
        (PAUSED 동안엔 시간이 안 흐르니까).

        ⚠️ 2026-09-03 리뷰 반영: 예전엔 현재값을 Python에서 읽어서(challenge = await
        self._model.get(...)) +extra_seconds 계산한 뒤 그 결과를 그대로 UPDATE했음(읽고-
        고쳐-쓰기). 거의 동시에 두 요청이 들어오면 나중 요청이 먼저 요청의 증가분을 덮어써서
        시간이 사라질 수 있었음. F() 식으로 DB가 직접 "현재값+extra_seconds"를 한 번에
        계산하게 바꿔서 중간에 읽은 값을 아예 안 씀 - 레이스 자체가 불가능해짐."""

        extra = max(extra_seconds, 0)
        await self._model.filter(id=challenge_id).update(
            accumulated_duration_seconds=F("accumulated_duration_seconds") + extra,
            started_at=None,
        )

    async def set_final_duration(self, challenge_id, final_seconds: int) -> None:
        """완료(complete) 순간 - 그때까지의 진짜 경과 시간을 그대로 확정 저장."""
        await self._model.filter(id=challenge_id).update(accumulated_duration_seconds=max(final_seconds, 0))

    async def set_final_count(self, challenge_id, final_count: int) -> None:
        """⚠️ 2026-09-08 반영: manual_check(직접 체크로 완료) 전용 - COUNT형(target_count가
        있는 SENSOR_STEPS 등) 완료 순간 목표치를 그대로 확정 저장."""
        await self._model.filter(id=challenge_id).update(accumulated_count=max(final_count, 0))

    async def set_accumulated_duration_and_clear_start(self, challenge_id, final_seconds: int) -> None:
        """⚠️ 2026-09-07 반영(N2 타이머 리셋 버그 수정): 일시정지(pause) 시 절대값으로
        확정 저장 - add_accumulated_duration_and_clear_start(F()+extra 더하기 방식)를
        쓰면, "이번 구간 경과"를 캡 적용된 값에서 역산하다가 음수가 나올 수 있는
        구조적 결함이 있었음(아래 challenge_service.pause() 주석 참고)."""

        await self._model.filter(id=challenge_id).update(
            accumulated_duration_seconds=max(final_seconds, 0),
            started_at=None,
        )

    async def create_event(self, challenge_id, event_type, idempotency_key, version, payload=None, occurred_at=None):
        """idempotency_key UNIQUE 위반 시 tortoise.exceptions.IntegrityError가 그대로 올라감.
        호출부(서비스 레이어)에서 잡아서 기존 이벤트를 반환하는 방식으로 처리할 것.
        occurred_at을 안 보내면 None으로 저장 -> 조회 시 server_at으로 대체해서 보여줌."""

        return await self._event_model.create(
            challenge_id=challenge_id,
            event_type=event_type,
            idempotency_key=idempotency_key,
            version=version,
            payload=payload,
            occurred_at=occurred_at,
        )

    async def get_event_by_idempotency_key(self, idempotency_key: str, user_id=None) -> ChallengeEvent | None:
        """⚠️ 2026-09-03 리뷰 반영(P2, 부분 조치): idempotency_key가 DB에서 전역 UNIQUE라서,
        user_id 없이 조회하면 "남이 쓴 키"의 존재 여부까지 그대로 알 수 있었음(다른 사용자
        challenge_id로 이어지는 이벤트가 조회됨). user_id를 넘기면 그 사용자 소유 챌린지의
        이벤트만 보이게 좁힘.
        ⚠️ 다만 이건 "조회만" 좁힌 것이고, DB의 idempotency_key UNIQUE 제약 자체는 여전히
        전역이라 서로 다른 사용자가 우연히 같은 키를 쓰면 INSERT 자체가 막히는 근본 문제는
        남아있음(안드로이드가 매번 새 랜덤 UUID를 써서 실제 충돌 확률은 극히 낮지만, 계약상
        허점임 - 완전한 수정은 user_id 컬럼 추가 + unique_together 마이그레이션이 필요해서
        별도 확인 후 진행하기로 함).
        """

        query = self._event_model.filter(idempotency_key=idempotency_key)
        if user_id is not None:
            query = query.filter(challenge__selection__card_set__user_id=user_id)
        return await query.prefetch_related("point_ledger_entry").first()

    async def create_point_ledger(self, user_id, source_event_id, delta: int, element: str) -> PointLedger:
        """source_event_id UNIQUE(uq_point_per_event)가 중복 보상을 막아줌."""

        return await self._ledger_model.create(
            user_id=user_id, source_event_id=source_event_id, delta=delta, element=element
        )
