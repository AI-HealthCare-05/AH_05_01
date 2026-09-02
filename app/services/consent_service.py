from fastapi import HTTPException, status
from tortoise.exceptions import IntegrityError

from app.dtos.consents import ConsentResponse
from app.models.users import User
from app.repositories.consent_repository import ConsentRepository


class ConsentService:
    def __init__(self):
        self.repo = ConsentRepository()

    async def list_consents(self, user: User) -> list[ConsentResponse]:
        consents = await self.repo.get_all_for_user(user.id)
        return [ConsentResponse.model_validate(c) for c in consents]

    async def agree(self, user: User, purpose: str, document_version: str) -> ConsentResponse:
        try:
            consent = await self.repo.create(user.id, purpose, document_version)
        except IntegrityError as exc:
            # UNIQUE(user, purpose, document_version) — 이미 같은 버전에 동의한 이력이 있음
            existing = await self.repo.get_latest_by_purpose(user.id, purpose)
            if existing and existing.document_version == document_version:
                return ConsentResponse.model_validate(existing)
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 처리된 동의입니다.") from exc
        return ConsentResponse.model_validate(consent)

    async def withdraw(self, user: User, purpose: str) -> ConsentResponse:
        consent = await self.repo.get_latest_by_purpose(user.id, purpose)
        if consent is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="동의 이력이 없습니다.")
        if consent.status == "WITHDRAWN":
            return ConsentResponse.model_validate(consent)
        consent = await self.repo.withdraw(consent)
        return ConsentResponse.model_validate(consent)
