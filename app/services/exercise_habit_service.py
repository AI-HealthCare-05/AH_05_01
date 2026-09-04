from fastapi import HTTPException, status

from app.dtos.exercise_habits import ExerciseHabitsRequest, ExerciseHabitsResponse
from app.models.users import User
from app.repositories.exercise_habit_repository import ExerciseHabitRepository

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
        return ExerciseHabitsResponse.model_validate(snapshot)

    async def get_latest(self, user: User) -> ExerciseHabitsResponse:
        snapshot = await self.repo.get_latest(user.id)
        if snapshot is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="입력된 운동습관이 없습니다.")
        return ExerciseHabitsResponse.model_validate(snapshot)
