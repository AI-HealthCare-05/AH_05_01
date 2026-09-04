from fastapi import HTTPException, status

from app.dtos.challenges import ChallengeProgressResponse
from app.dtos.sensor import SensorMeasurementBatchResponse, SensorMeasurementItem
from app.models.challenges import ChallengeState
from app.models.users import User
from app.repositories.challenge_repository import ChallengeRepository
from app.repositories.sensor_repository import SensorRepository

# 안드로이드 MissionSensorService.kt 기준(2026-08-26) 6종류.
# 전부 "0에서 시작해 계속 커지는 누적값을 주기적으로 스냅샷 저장" 방식이라
# 서버는 배치 안에서 제일 큰 값 하나만 뽑아 기존 값과 비교해 갱신하면 됨 (합산 아님).
COUNTER_TYPES = {
    "STEP": "SENSOR_STEPS",
    "STAIR": "SENSOR_FLOORS_CLIMBED",
    "STEP_IN_PLACE": "SENSOR_STEPS_IN_PLACE",
    "RUN_DISTANCE_M": "SENSOR_RUNNING_DISTANCE",
}
DURATION_TYPES = {
    "RUN_DURATION": "SENSOR_RUNNING_DURATION",
    "WALK_DURATION": "SENSOR_WALKING_DURATION",
}
EXEC_TYPE_BY_MEASUREMENT_TYPE = {**COUNTER_TYPES, **DURATION_TYPES}


class SensorMeasurementService:
    def __init__(self):
        self.challenge_repo = ChallengeRepository()
        self.sensor_repo = SensorRepository()

    async def ingest_batch(
        self, user: User, challenge_id, records: list[SensorMeasurementItem]
    ) -> SensorMeasurementBatchResponse:
        challenge = await self._get_owned_active_challenge(user, challenge_id)

        accepted = 0
        rejected = 0
        reasons: list[str] = []
        best_value_by_type: dict[str, int] = {}

        for item in records:
            expected_exec_type = EXEC_TYPE_BY_MEASUREMENT_TYPE.get(item.measurement_type)

            if expected_exec_type is None:
                rejected += 1
                reasons.append(f"알 수 없는 measurement_type: {item.measurement_type}")
                continue
            if expected_exec_type != challenge.exec_type:
                rejected += 1
                reasons.append(
                    f"챌린지 타입({challenge.exec_type})과 measurement_type({item.measurement_type})이 안 맞음"
                )
                continue
            if item.value is None:
                rejected += 1
                reasons.append(f"{item.measurement_type} 레코드에 value가 없음")
                continue

            await self.sensor_repo.create_event(
                challenge_id=challenge.id,
                measurement_type=item.measurement_type,
                value=item.value,
                recorded_at=item.recorded_at,
            )
            accepted += 1

            current_best = best_value_by_type.get(item.measurement_type, -1)
            if item.value > current_best:
                best_value_by_type[item.measurement_type] = item.value

        for measurement_type, best_value in best_value_by_type.items():
            if measurement_type in COUNTER_TYPES:
                await self.sensor_repo.set_counter_if_higher(challenge, best_value)
            elif measurement_type in DURATION_TYPES:
                await self.sensor_repo.set_duration_if_higher(challenge, best_value)

        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        return SensorMeasurementBatchResponse(
            challenge=ChallengeProgressResponse.model_validate(refreshed),
            accepted_count=accepted,
            rejected_count=rejected,
            rejected_reasons=reasons,
        )

    async def _get_owned_active_challenge(self, user: User, challenge_id):
        challenge = await self.challenge_repo.get_by_id(challenge_id)
        if challenge is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="챌린지를 찾을 수 없습니다.")

        selection = await challenge.selection
        card_set = await selection.card_set
        if card_set.user_id != user.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="본인의 챌린지만 접근할 수 있습니다.")

        if challenge.state not in (ChallengeState.ACTIVE, ChallengeState.READY):
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"측정 데이터를 받을 수 없는 상태입니다: {challenge.state}",
            )
        return challenge
