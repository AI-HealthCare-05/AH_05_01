from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.assessments import AssessmentJobResponse
from app.models.users import User
from app.services.assessment_service import AssessmentService

assessment_router = APIRouter(prefix="/assessments", tags=["assessments"])


@assessment_router.post("", response_model=AssessmentJobResponse, status_code=status.HTTP_201_CREATED)
async def create_assessment(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[AssessmentService, Depends(AssessmentService)],
) -> AssessmentJobResponse:
    """최신 health_input_snapshot 기준으로 즉시 계산 후 status=DONE으로 반환.
    (부트캠프 규모에서 큐 없이 동기 처리하기로 한 결정 — 지난 대화 참고)"""

    return await service.create_assessment(user)


@assessment_router.get("/latest", response_model=AssessmentJobResponse, status_code=status.HTTP_200_OK)
async def get_latest_assessment(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[AssessmentService, Depends(AssessmentService)],
) -> AssessmentJobResponse:
    return await service.get_latest(user)
