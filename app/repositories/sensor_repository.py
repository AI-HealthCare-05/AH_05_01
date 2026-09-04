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
        """

        if candidate_value > challenge.accumulated_count:
            challenge.accumulated_count = candidate_value
            await challenge.save(update_fields=["accumulated_count"])
            return True
        return False

    async def set_duration_if_higher(self, challenge: Challenge, candidate_seconds: int) -> bool:
        """RUN_DURATION/WALK_DURATION도 케이던스 매니저가 이미 계산해둔 누적 초를
        그대로 스냅샷으로 보내므로 카운터와 동일하게 최댓값 갱신 방식을 씀."""

        if candidate_seconds > challenge.accumulated_duration_seconds:
            challenge.accumulated_duration_seconds = candidate_seconds
            await challenge.save(update_fields=["accumulated_duration_seconds"])
            return True
        return False
