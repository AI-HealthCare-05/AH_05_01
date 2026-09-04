from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.sensor import SensorMeasurementBatchRequest, SensorMeasurementBatchResponse
from app.models.users import User
from app.services.sensor_service import SensorMeasurementService

sensor_router = APIRouter(prefix="/challenges", tags=["sensor-measurements"])


@sensor_router.post(
    "/{challenge_id}/sensor-measurements/batch",
    response_model=SensorMeasurementBatchResponse,
    status_code=status.HTTP_201_CREATED,
)
async def submit_sensor_measurements(
    challenge_id: UUID,
    request: SensorMeasurementBatchRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[SensorMeasurementService, Depends(SensorMeasurementService)],
) -> SensorMeasurementBatchResponse:
    """안드로이드가 오프라인 중 쌓아둔 걸음/계단/달리기 기록을 한 번에 동기화.

    구 mission_records_routers.py를 대체. 이제 daily_card_id 대신 challenge_id를
    경로에 명시해서, 이 측정값이 어느 챌린지 진행 상황에 반영될지 명확히 함.
    """

    return await service.ingest_batch(user, challenge_id, request.records)
