from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.tuntun_score import (
    ScoreInputsResponse,
    TmtnScoreResponse,
    TuntunScoreOrEligibilityResponse,
    TuntunScoreV2Response,
)
from app.dtos.tuntun_score_peer import TuntunScorePeerV2Response
from app.models.users import User
from app.services.tuntun_score_peer_service import TuntunScorePeerService
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


@tuntun_score_router.get("/peer/v2", response_model=TuntunScorePeerV2Response, status_code=status.HTTP_200_OK)
async def get_tuntun_score_peer_v2(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[TuntunScorePeerService, Depends(TuntunScorePeerService)],
) -> TuntunScorePeerV2Response:
    """⚠️ 2026-09-09 추가 — LOCAL_REVIEW_CANDIDATE, 팀 검토 전용. 별도 Python 3.14.7
    모델 브릿지(tuntun_peer_bridge/)에 HTTP로 요청해서 실제 모델 추론(또래 백분위) 결과를
    받아온다. 기존 /tuntun-score, /tuntun-score/v2(둘 다 Mock)는 이 라우트와 전혀
    무관하며 그대로 유지된다.

    앱 홈/카드 화면에 아직 연결하지 않는다 — 팀이 UI 최종 결정 전까지는 이 경로를 직접
    호출해서 확인하는 용도로만 쓴다. 운영 서버에는 TUNTUN_PEER_BRIDGE_URL을 절대
    채우지 않는다(안 채우면 503을 그대로 반환하므로 존재 자체가 운영에 영향 없음).
    """

    return await service.get_score(user)
