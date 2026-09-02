from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.exercise_habits import ExerciseHabitsRequest, ExerciseHabitsResponse
from app.models.users import User
from app.services.exercise_habit_service import ExerciseHabitService

exercise_habit_router = APIRouter(prefix="/exercise-habits", tags=["exercise-habits"])


@exercise_habit_router.post("", response_model=ExerciseHabitsResponse, status_code=status.HTTP_201_CREATED)
async def create_exercise_habits(
    request: ExerciseHabitsRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ExerciseHabitService, Depends(ExerciseHabitService)],
) -> ExerciseHabitsResponse:
    """A08: 온보딩 운동습관 입력. append-only — 항상 새 row."""

    return await service.create(user, request)


@exercise_habit_router.get("/latest", response_model=ExerciseHabitsResponse, status_code=status.HTTP_200_OK)
async def get_latest_exercise_habits(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ExerciseHabitService, Depends(ExerciseHabitService)],
) -> ExerciseHabitsResponse:
    return await service.get_latest(user)
