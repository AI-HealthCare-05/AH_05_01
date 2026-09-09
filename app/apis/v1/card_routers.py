from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, status

from app.core.time_utils import service_today
from app.dependencies.security import get_request_user
from app.dtos.cards import CardRevealResponse, CardWindowResponse
from app.models.users import User
from app.services.card_service import CardService

card_router = APIRouter(prefix="/daily-cards", tags=["daily-cards"])


@card_router.get("/today", response_model=CardWindowResponse, status_code=status.HTTP_200_OK)
async def get_today_cards(
    user: Annotated[User, Depends(get_request_user)],
    card_service: Annotated[CardService, Depends(CardService)],
) -> CardWindowResponse:
    """없으면 서버가 생성 후 반환 — 문서: "service_date당 1세트"."""

    # ⚠️ 2026-09-03 리뷰 반영: date.today()는 서버 로컬(컨테이너) 시간대 기준이라, UTC로
    # 뜨는 배포 환경에서는 00~09시 KST 사이 사용자에게 "어제"로 판정되는 문제가 있었음.
    # TODO는 여전히 유효(사용자별 timezone·RESET_SCHEDULES는 아직 없음) - 다만 최소한
    # 서버 로컬시간이 아니라 KST 고정으로는 지금 맞춰둠.
    today = service_today(user.id)  # ⚠️ 2026-09-08: 계정별 오프셋 적용
    return await card_service.get_or_create_today(user, today)


@card_router.post(
    "/{set_id}/options/{option_id}/select",
    response_model=CardRevealResponse,
    status_code=status.HTTP_201_CREATED,
)
async def select_card_option(
    set_id: UUID,
    option_id: UUID,
    user: Annotated[User, Depends(get_request_user)],
    card_service: Annotated[CardService, Depends(CardService)],
) -> CardRevealResponse:
    """세트당 선택 1건 제한(uq_winner_per_set). 이미 확정된 세트면 409."""

    return await card_service.select_option(user, set_id, option_id)
