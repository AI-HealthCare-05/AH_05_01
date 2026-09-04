from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.tuntun_score import (
    ScoreInputsResponse,
    TmtnScoreResponse,
    TuntunScoreOrEligibilityResponse,
    TuntunScoreV2Response,
)
from app.models.users import User
from app.services.tuntun_score_service import TuntunScoreService

tuntun_score_router = APIRouter(prefix="/tuntun-score", tags=["tuntun-score"])


@tuntun_score_router.get("", response_model=TuntunScoreOrEligibilityResponse, status_code=status.HTTP_200_OK)
async def get_tuntun_score(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[TuntunScoreService, Depends(TuntunScoreService)],
) -> TuntunScoreOrEligibilityResponse:
    """E01/E02(요약·자세히) 공통 진입점. eligible=false면 E05(산출 불가)로 분기.
    ⚠️ MOCK — 실제 예측 모델 연결 전 뼈대 단계(response.score.is_mock 항상 true)."""

    return await service.get_score(user)


@tuntun_score_router.get("/v2", response_model=TuntunScoreV2Response, status_code=status.HTTP_200_OK)
async def get_tuntun_score_v2(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[TuntunScoreService, Depends(TuntunScoreService)],
) -> TuntunScoreV2Response:
    """종합점수와 신체·당뇨·고혈압·생활습관 점수를 함께 제공하는 Mock 연동 API.

    실제 배포 모델의 예측값이나 질환 확률을 제공하지 않는다. ``isMock``은 production
    모델 패키지가 연결되기 전까지 항상 true다.
    """

    return await service.get_score_v2(user)


@tuntun_score_router.get("/about", response_model=TmtnScoreResponse, status_code=status.HTTP_200_OK)
async def get_tuntun_score_about(
    service: Annotated[TuntunScoreService, Depends(TuntunScoreService)],
) -> TmtnScoreResponse:
    """E06 "이 지수에 대하여" — 정적 안내, 로그인 여부와 무관."""

    return await service.get_score_about()


@tuntun_score_router.get("/inputs", response_model=ScoreInputsResponse, status_code=status.HTTP_200_OK)
async def get_tuntun_score_inputs(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[TuntunScoreService, Depends(TuntunScoreService)],
) -> ScoreInputsResponse:
    """E04 "계산에 쓰인 값" — 새 저장소 없이 기존 프로필/신체입력/운동습관 데이터를 조립해서 보여줌.
    각 필드의 "값 고치고 다시 계산" 진입점은 F그룹(내정보)/A그룹(온보딩) 기존 수정 화면을 그대로 씀
    (FIELDS.csv의 edit-profile-*/edit-activity-* 액션 — 새 입력 화면을 따로 만들지 않음)."""

    return await service.get_score_inputs(user)
