from typing import Annotated

from fastapi import APIRouter, Depends, Response, status

from app.dependencies.security import get_request_user
from app.dtos.companion import (
    CardCollectionResponse,
    CompanionResponse,
    FirstRepairResponse,
    MaterialHistoryResponse,
    StageUpPendingResponse,
)
from app.models.users import User
from app.services.companion_service import CompanionService

companion_router = APIRouter(prefix="/companion", tags=["companion"])


@companion_router.get("", response_model=CompanionResponse, status_code=status.HTTP_200_OK)
async def get_companion_status(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[CompanionService, Depends(CompanionService)],
) -> CompanionResponse:
    """G01(댐 홈) + G02(재료 도감) + G03(댐 단계 안내) 화면 데이터를 한 번에 반환."""

    return await service.get_dam_status(user)


# ⚠️ 2026-09-18 추가(UI/UX 핸드오프 FR01~08 "첫 복구") - PR #21 소스 기준 이식.
@companion_router.get("/first-repair", response_model=FirstRepairResponse, status_code=status.HTTP_200_OK)
async def get_first_repair(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[CompanionService, Depends(CompanionService)],
) -> FirstRepairResponse:
    """FR01~08: 새 가입자 전용 첫 복구 상태 조회. 레코드가 없으면(기존 사용자) UNAVAILABLE."""

    return await service.get_first_repair(user)


@companion_router.post("/first-repair/gift", response_model=FirstRepairResponse, status_code=status.HTTP_200_OK)
async def receive_first_gift(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[CompanionService, Depends(CompanionService)],
) -> FirstRepairResponse:
    """FR02: 첫 재료 수령. 같은 계정이 다시 호출해도 중복 지급되지 않는다."""

    return await service.advance_first_repair(user, complete=False)


@companion_router.post("/first-repair/complete", response_model=FirstRepairResponse, status_code=status.HTTP_200_OK)
async def complete_first_repair(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[CompanionService, Depends(CompanionService)],
) -> FirstRepairResponse:
    """FR04: 첫 복구 완료 확정. 선물을 아직 안 받았으면 409(GIFT_REQUIRED 사유)."""

    return await service.advance_first_repair(user, complete=True)


@companion_router.get(
    "/materials/{element}/history", response_model=MaterialHistoryResponse, status_code=status.HTTP_200_OK
)
async def get_material_history(
    element: str,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[CompanionService, Depends(CompanionService)],
) -> MaterialHistoryResponse:
    """G05: 재료 하나(예: WOOD)를 눌렀을 때 - 총 개수 + 최근 5개 이력."""

    return await service.get_material_history(user, element)


@companion_router.get("/cards", response_model=CardCollectionResponse, status_code=status.HTTP_200_OK)
async def get_card_collection(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[CompanionService, Depends(CompanionService)],
    element: str | None = None,
) -> CardCollectionResponse:
    """G06: 카드첩 - element로 필터 가능(안 주면 전체)."""

    return await service.get_card_collection(user, element)


@companion_router.get("/stage-up-pending", response_model=None, status_code=status.HTTP_200_OK)
async def get_stage_up_pending(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[CompanionService, Depends(CompanionService)],
) -> StageUpPendingResponse | Response:
    """G07: 아직 못 본 단계 상승이 있으면 그 내용, 없으면 204(본문 없음)."""

    result = await service.get_stage_up_pending(user)
    if result is None:
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    return result


@companion_router.post("/stage-up-seen", status_code=status.HTTP_204_NO_CONTENT)
async def mark_stage_up_seen(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[CompanionService, Depends(CompanionService)],
) -> None:
    """G07 화면을 실제로 봤다고 표시."""

    await service.mark_stage_seen(user)
