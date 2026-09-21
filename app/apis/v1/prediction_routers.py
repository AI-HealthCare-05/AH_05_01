from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.prediction import (
    ApproveModelVersionRequest,
    PredictionResultBatchRequest,
    PredictionResultBatchResponse,
    PredictionResultResponse,
)
from app.models.users import User
from app.services.prediction_service import PredictionService

prediction_router = APIRouter(prefix="/prediction-results", tags=["prediction-results"])


@prediction_router.post("", response_model=PredictionResultBatchResponse, status_code=status.HTTP_201_CREATED)
async def save_prediction_results(
    request: PredictionResultBatchRequest,
    caller: Annotated[User, Depends(get_request_user)],
    service: Annotated[PredictionService, Depends(PredictionService)],
) -> PredictionResultBatchResponse:
    """모델 파이프라인이 계산 결과를 저장할 때 호출. 관리자 계정만 호출 가능."""

    return await service.save_batch(caller, request)


@prediction_router.post("/approve-model-version", status_code=status.HTTP_200_OK)
async def approve_model_version(
    request: ApproveModelVersionRequest,
    caller: Annotated[User, Depends(get_request_user)],
    service: Annotated[PredictionService, Depends(PredictionService)],
) -> dict:
    """특정 (submodel_type, model_version)을 사용자에게 노출 가능하도록 승인/취소."""

    return await service.approve_model_version(caller, request)


@prediction_router.get("/latest", response_model=list[PredictionResultResponse], status_code=status.HTTP_200_OK)
async def get_latest_prediction_results(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[PredictionService, Depends(PredictionService)],
) -> list[PredictionResultResponse]:
    """내 최신 결과 조회. 계산됐어도 승인 안 된 model_version이면 여기 안 나옴."""

    return await service.get_latest_visible(user)
