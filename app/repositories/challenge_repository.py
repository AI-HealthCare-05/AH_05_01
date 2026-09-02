from app.models.challenges import Challenge, ChallengeEvent, ChallengeState, PointLedger


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
            target_duration_seconds = template.target_value
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

    async def try_transition(
        self, challenge: Challenge, *, from_states: list[str], to_state: str
    ) -> bool:
        """ERD 문서 §6의 낙관적 잠금 그대로 구현.
        UPDATE ... WHERE id=? AND version=? AND state IN (...) — affected rows 0이면 실패."""

        current_version = challenge.version
        updated_count = await self._model.filter(
            id=challenge.id, version=current_version, state__in=from_states
        ).update(state=to_state, version=current_version + 1)
        return updated_count > 0

    async def update_started_at(self, challenge_id, started_at) -> None:
        """C02/C09 "시작하기" - 지금 구간이 시작된 시각 기록."""
        await self._model.filter(id=challenge_id).update(started_at=started_at)

    async def add_accumulated_duration_and_clear_start(self, challenge_id, extra_seconds: int) -> None:
        """C03 "일시정지" - 이번 구간에서 흐른 시간을 누적값에 더하고, started_at은 비움
        (PAUSED 동안엔 시간이 안 흐르니까)."""
        challenge = await self._model.get(id=challenge_id)
        await self._model.filter(id=challenge_id).update(
            accumulated_duration_seconds=challenge.accumulated_duration_seconds + max(extra_seconds, 0),
            started_at=None,
        )

    async def set_final_duration(self, challenge_id, final_seconds: int) -> None:
        """완료(complete) 순간 - 그때까지의 진짜 경과 시간을 그대로 확정 저장."""
        await self._model.filter(id=challenge_id).update(accumulated_duration_seconds=max(final_seconds, 0))

    async def create_event(
        self, challenge_id, event_type, idempotency_key, version, payload=None, occurred_at=None
    ):
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

    async def get_event_by_idempotency_key(self, idempotency_key: str) -> ChallengeEvent | None:
        return await self._event_model.get_or_none(idempotency_key=idempotency_key).prefetch_related(
            "point_ledger_entry"
        )

    async def create_point_ledger(self, user_id, source_event_id, delta: int, element: str) -> PointLedger:
        """source_event_id UNIQUE(uq_point_per_event)가 중복 보상을 막아줌."""

        return await self._ledger_model.create(
            user_id=user_id, source_event_id=source_event_id, delta=delta, element=element
        )
