from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.health import HealthInputCreateRequest, HealthInputResponse
from app.models.users import User
from app.services.health_service import HealthInputService

health_router = APIRouter(prefix="/health-inputs", tags=["health-inputs"])


@health_router.post("", response_model=HealthInputResponse, status_code=status.HTTP_201_CREATED)
async def create_health_input(
    request: HealthInputCreateRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[HealthInputService, Depends(HealthInputService)],
) -> HealthInputResponse:
    """append-only — 항상 새 row (UPDATE 금지)."""

    return await service.create(user, request)


@health_router.get("/latest", response_model=HealthInputResponse, status_code=status.HTTP_200_OK)
async def get_latest_health_input(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[HealthInputService, Depends(HealthInputService)],
) -> HealthInputResponse:
    return await service.get_latest(user)


@health_router.get("", response_model=list[HealthInputResponse], status_code=status.HTTP_200_OK)
async def get_health_input_history(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[HealthInputService, Depends(HealthInputService)],
) -> list[HealthInputResponse]:
    return await service.get_history(user)
