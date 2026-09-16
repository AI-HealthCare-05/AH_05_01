from fastapi import HTTPException, status

from app.dtos.exercise_habits import ExerciseHabitsRequest, ExerciseHabitsResponse
from app.models.users import User
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.repositories.health_repository import HealthInputRepository
from app.services.waist_estimate_service import WaistEstimateService

VALID_INTENSITIES = {"LIGHT", "MODERATE", "HARD"}


class ExerciseHabitService:
    def __init__(self):
        self.repo = ExerciseHabitRepository()

    async def create(self, user: User, request: ExerciseHabitsRequest) -> ExerciseHabitsResponse:
        if request.strength_intensity is not None and request.strength_intensity not in VALID_INTENSITIES:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail=f"알 수 없는 strength_intensity: {request.strength_intensity}",
            )

        snapshot = await self.repo.create(
            user_id=user.id,
            strength_weekly_count=request.strength_weekly_count,
            strength_intensity=request.strength_intensity,
            aerobic_low_minutes=request.aerobic_low_minutes,
            aerobic_moderate_minutes=request.aerobic_moderate_minutes,
            aerobic_high_minutes=request.aerobic_high_minutes,
        )
        # ⚠️ 2026-09-10 추가 - health_service.py와 동일한 이유. 운동습관만 바뀐 경우라
        # 신체정보 스냅샷은 새로 안 만들어지니, 최신 신체정보 스냅샷의 id를 그대로 가져다
        # 씀(input_snapshot_id는 "이번 계산에 실제로 쓰인 신체정보"를 가리키는 게 맞고,
        # 이번에 새로 생긴 건 운동습관 쪽이라 신체정보 스냅샷 자체는 최신 걸 그대로 참조).
        try:
            health_snapshot = await HealthInputRepository().get_latest(user.id)
            if health_snapshot is not None:
                await WaistEstimateService().recompute_and_save(user, health_snapshot.id)
        except Exception:  # noqa: BLE001 - 운동습관 저장 자체엔 영향 주지 않음
            pass
        return ExerciseHabitsResponse.model_validate(snapshot)

    async def get_latest(self, user: User) -> ExerciseHabitsResponse:
        snapshot = await self.repo.get_latest(user.id)
        if snapshot is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="입력된 운동습관이 없습니다.")
        return ExerciseHabitsResponse.model_validate(snapshot)
