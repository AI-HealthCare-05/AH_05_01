from app.models.challenges import Challenge, SensorMeasurementEvent


class SensorRepository:
    def __init__(self):
        self._event_model = SensorMeasurementEvent

    async def create_event(
        self,
        challenge_id,
        measurement_type: str,
        recorded_at,
        value: int,
    ) -> SensorMeasurementEvent:
        """원본 스냅샷을 그대로 기록에 남긴다 (감사·디버깅용). 실제 챌린지 진행 반영은
        서비스 레이어에서 최댓값 비교 후 별도로 challenge에 적용."""

        return await self._event_model.create(
            challenge_id=challenge_id,
            measurement_type=measurement_type,
            value=value,
            recorded_at=recorded_at,
        )

    async def set_counter_if_higher(self, challenge: Challenge, candidate_value: int) -> bool:
        """안드로이드가 30초마다 "지금까지 누적 총합" 스냅샷을 보내므로,
        더하는(sum) 게 아니라 더 큰 값으로만 갱신한다(max). 이러면:
        - 같은 스냅샷을 중복 전송해도 안전 (idempotent)
        - 배치 순서가 뒤바뀌어 와도 값이 줄어들지 않음

        ⚠️ 2026-09-16 버그 수정(QA F10) - 예전엔 "메모리에 이미 읽어둔 challenge 객체의
        값과 비교 → save()"였는데, 두 요청이 동시에 같은 오래된 값을 읽으면 나중에
        저장하는 쪽이 먼저 저장된 더 큰 값을 덮어써서 누적값이 줄어들 수 있었음
        (원인분석 문서에서 두 오래된 객체로 격리 실행해 100→50으로 줄어드는 걸 재현함).
        이제 WHERE 절에 조건(accumulated_count__lt)을 걸어서 DB가 원자적으로 판단하게
        함 - 두 요청이 동시에 와도 "그 순간 DB에 저장된 값보다 큰 경우"에만 실제로
        갱신되고, 그렇지 않으면 이 UPDATE 자체가 0행에 적용돼 아무 효과가 없음.
        """

        updated = await Challenge.filter(id=challenge.id, accumulated_count__lt=candidate_value).update(
            accumulated_count=candidate_value
        )
        if updated:
            challenge.accumulated_count = candidate_value  # 호출부가 보는 메모리 값도 동기화
        return bool(updated)

    async def set_duration_if_higher(self, challenge: Challenge, candidate_seconds: int) -> bool:
        """RUN_DURATION/WALK_DURATION도 케이던스 매니저가 이미 계산해둔 누적 초를
        그대로 스냅샷으로 보내므로 카운터와 동일하게 최댓값 갱신 방식을 씀.
        ⚠️ 2026-09-16 버그 수정(QA F10) - 위와 같은 원자적 조건부 UPDATE로 교체."""

        updated = await Challenge.filter(
            id=challenge.id, accumulated_duration_seconds__lt=candidate_seconds
        ).update(accumulated_duration_seconds=candidate_seconds)
        if updated:
            challenge.accumulated_duration_seconds = candidate_seconds
        return bool(updated)
