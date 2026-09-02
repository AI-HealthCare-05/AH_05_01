from datetime import datetime

from app.core import config
from app.models.accounts import ConsentStatus, UserConsent


class ConsentRepository:
    def __init__(self):
        self._model = UserConsent

    async def get_all_for_user(self, user_id) -> list[UserConsent]:
        return await self._model.filter(user_id=user_id).order_by("-agreed_at")

    async def get_latest_by_purpose(self, user_id, purpose: str) -> UserConsent | None:
        return await self._model.filter(user_id=user_id, purpose=purpose).order_by("-agreed_at").first()

    async def create(self, user_id, purpose: str, document_version: str) -> UserConsent:
        """UNIQUE(user, purpose, document_version) — 같은 버전에 두 번 동의하면 DB가 막아줌.
        호출부에서 tortoise.exceptions.IntegrityError를 잡아 409/기존 row 반환 처리할 것."""

        return await self._model.create(user_id=user_id, purpose=purpose, document_version=document_version)

    async def withdraw(self, consent: UserConsent) -> UserConsent:
        """append-only 원칙: row를 지우지 않고 withdrawn_at만 채움 (ERD 문서 근거)."""

        consent.status = ConsentStatus.WITHDRAWN
        consent.withdrawn_at = datetime.now(config.TIMEZONE)
        await consent.save(update_fields=["status", "withdrawn_at"])
        return consent
