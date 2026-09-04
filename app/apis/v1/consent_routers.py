from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.dependencies.security import get_request_user
from app.dtos.consents import ConsentRequest, ConsentResponse
from app.models.users import User
from app.services.consent_service import ConsentService

consent_router = APIRouter(prefix="/consents", tags=["consents"])


@consent_router.get("", response_model=list[ConsentResponse], status_code=status.HTTP_200_OK)
async def list_consents(
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ConsentService, Depends(ConsentService)],
) -> list[ConsentResponse]:
    return await service.list_consents(user)


@consent_router.post("", response_model=ConsentResponse, status_code=status.HTTP_201_CREATED)
async def agree_consent(
    request: ConsentRequest,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ConsentService, Depends(ConsentService)],
) -> ConsentResponse:
    return await service.agree(user, request.purpose, request.document_version)


@consent_router.delete("/{purpose}", response_model=ConsentResponse, status_code=status.HTTP_200_OK)
async def withdraw_consent(
    purpose: str,
    user: Annotated[User, Depends(get_request_user)],
    service: Annotated[ConsentService, Depends(ConsentService)],
) -> ConsentResponse:
    """row 삭제 아님 — withdrawn_at만 기록 (ERD append-only 원칙)."""

    return await service.withdraw(user, purpose)
