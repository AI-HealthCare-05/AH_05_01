from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.dependencies.security import get_request_user
from app.dtos.practice_score import PracticeScoreResponse
from app.models.users import User
from app.services.practice_score_service import PracticeScoreService

practice_score_router = APIRouter(tags=["practice-score"])


@practice_score_router.get("/practice-score", response_model=PracticeScoreResponse, status_code=status.HTTP_200_OK)
async def get_practice_score(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[PracticeScoreService, Depends(PracticeScoreService)],
) -> PracticeScoreResponse:
    """⚠️ 2026-09-15 신규 - 미션 실천 점수. 전체 지수(compositeScore)는 아직 못 채움 -
    practice_score_service.py 상단 docstring 참고."""

    try:
        result = await service.get_practice_score(user)
    except ValueError as e:
        # ⚠️ practice_formula.py의 산식이 방어적으로 던지는 예외들(INVALID_MINUTES,
        # IMPOSSIBLE_DAILY_DURATION 등) - 잘못된 데이터가 들어간 것이지 서버 버그가
        # 아니라서 500 대신 422로 명확히 알림.
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e)) from e
    return PracticeScoreResponse(**result)
