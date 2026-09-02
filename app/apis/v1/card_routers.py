from datetime import date
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, status

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

    today = date.today()  # TODO: 사용자 timezone 기준 "하루" 판정 로직으로 교체 예정 (RESET_SCHEDULES 미구현)
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
