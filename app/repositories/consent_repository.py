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

    # ⚠️ 2026-09-12 버그 수정 - "철회했다가 다시 동의" 시나리오. UNIQUE(user, purpose,
    # document_version)라 같은 버전으로는 새 row를 못 만드는데, 기존 코드는 그 충돌이 나면
    # 옛 WITHDRAWN row를 그냥 그대로 반환해버려서 스위치가 다시 안 켜지는 것처럼 보였음
    # (실제로는 API가 200을 내려줘서 에러도 안 남 - 그래서 "로딩만 돌고 그대로"로 보임).
    # append-only 원칙(row를 지우지 않음)은 유지하되, 같은 (user, purpose, document_version)
    # 조합은 UNIQUE라 row가 하나뿐이어야 하므로, 그 하나의 row를 재활성화하는 방식으로 고침.
    async def reagree(self, consent: UserConsent) -> UserConsent:
        consent.status = ConsentStatus.AGREED
        consent.agreed_at = datetime.now(config.TIMEZONE)
        consent.withdrawn_at = None
        await consent.save(update_fields=["status", "agreed_at", "withdrawn_at"])
        return consent

    async def withdraw(self, consent: UserConsent) -> UserConsent:
        """append-only 원칙: row를 지우지 않고 withdrawn_at만 채움 (ERD 문서 근거)."""

        consent.status = ConsentStatus.WITHDRAWN
        consent.withdrawn_at = datetime.now(config.TIMEZONE)
        await consent.save(update_fields=["status", "withdrawn_at"])
        return consent
