from fastapi import HTTPException, status
from tortoise.exceptions import IntegrityError

from app.dtos.consents import ConsentResponse
from app.models.accounts import ConsentStatus
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
                # ⚠️ 2026-09-12 버그 수정: "껐다가 다시 켬" - 기존 row가 WITHDRAWN이면
                # 그대로 반환하지 않고 재활성화함(안 그러면 스위치가 안 켜지는 것처럼 보임).
                # 이미 AGREED면(중복 클릭 등) 멱등하게 그대로 반환.
                if existing.status == ConsentStatus.WITHDRAWN:
                    consent = await self.repo.reagree(existing)
                else:
                    return ConsentResponse.model_validate(existing)
            else:
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
