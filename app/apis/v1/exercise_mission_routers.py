from datetime import date
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Query, status

from app.dependencies.security import get_request_user
from app.dtos.exercise_missions import (
    CompleteExerciseMissionSessionRequest,
    CompleteExerciseMissionSessionResponse,
    CreateExerciseMissionSessionRequest,
    ExerciseMissionActionRequest,
    ExerciseMissionRecordsResponse,
    ExerciseMissionSessionResponse,
    ExerciseMissionsTodayResponse,
)
from app.models.users import User
from app.services.exercise_mission_service import ExerciseMissionService

exercise_mission_router = APIRouter(tags=["exercise-missions"])


@exercise_mission_router.get(
    "/exercise-missions/today", response_model=ExerciseMissionsTodayResponse, status_code=status.HTTP_200_OK
)
async def get_exercise_missions_today(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ExerciseMissionService, Depends(ExerciseMissionService)],
) -> ExerciseMissionsTodayResponse:
    """⚠️ 2026-09-11 신규 - TMtn_UI_V17 §5 "틈새 운동". 오늘의 카드 완료 후에만 실제로
    시작 가능(card_completed로 프론트가 판단). 오늘 이미 완료한 운동은
    already_completed_today=true로 내려가서 다시 못 고르게 함."""

    return await service.get_today(user)


@exercise_mission_router.post(
    "/exercise-mission-sessions", response_model=ExerciseMissionSessionResponse, status_code=status.HTTP_201_CREATED
)
async def create_exercise_mission_session(
    request: CreateExerciseMissionSessionRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ExerciseMissionService, Depends(ExerciseMissionService)],
) -> ExerciseMissionSessionResponse:
    return await service.create_session(user, request.catalog_entry_id, request.idempotency_key)


@exercise_mission_router.patch(
    "/exercise-mission-sessions/{session_id}",
    response_model=ExerciseMissionSessionResponse,
    status_code=status.HTTP_200_OK,
)
async def patch_exercise_mission_session(
    session_id: UUID,
    request: ExerciseMissionActionRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ExerciseMissionService, Depends(ExerciseMissionService)],
) -> ExerciseMissionSessionResponse:
    """action=pause/resume. 센서 진행값을 동기화하려면 accumulated_count도 같이 보냄."""

    return await service.patch_session(user, session_id, request.action, request.accumulated_count)


@exercise_mission_router.post(
    "/exercise-mission-sessions/{session_id}/complete",
    response_model=CompleteExerciseMissionSessionResponse,
    status_code=status.HTTP_200_OK,
)
async def complete_exercise_mission_session(
    session_id: UUID,
    request: CompleteExerciseMissionSessionRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ExerciseMissionService, Depends(ExerciseMissionService)],
) -> CompleteExerciseMissionSessionResponse:
    """목표 재검증 + 원자적 지급(reward_slot 확정). 같은 idempotency_key로 재시도하면
    동일 결과를 그대로 반환함(중복 지급 없음)."""

    return await service.complete_session(
        user, session_id, request.idempotency_key, request.manual_check, request.accumulated_count
    )


@exercise_mission_router.post(
    "/exercise-mission-sessions/{session_id}/cancel", status_code=status.HTTP_204_NO_CONTENT
)
async def cancel_exercise_mission_session(
    session_id: UUID,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ExerciseMissionService, Depends(ExerciseMissionService)],
) -> None:
    """⚠️ 중단해도 오늘의 카드 기록·쉼·연속 기록에 불이익 없음 - 이 세션만 취소됨."""

    await service.cancel_session(user, session_id)


@exercise_mission_router.get(
    "/exercise-mission-records", response_model=ExerciseMissionRecordsResponse, status_code=status.HTTP_200_OK
)
async def get_exercise_mission_records(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ExerciseMissionService, Depends(ExerciseMissionService)],
    from_date: Annotated[date, Query(alias="from")],
    to_date: Annotated[date, Query(alias="to")],
) -> ExerciseMissionRecordsResponse:
    """하루·주간 집계용(틈튼일보 D17/D18에서 사용 예정)."""

    return await service.get_records(user, from_date, to_date)
