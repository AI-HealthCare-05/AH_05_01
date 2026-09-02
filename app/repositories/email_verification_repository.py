from app.models.accounts import EmailVerificationRequest


class EmailVerificationRepository:
    def __init__(self):
        self._model = EmailVerificationRequest

    async def create(self, email_normalized: str, code_hash: str, expires_at) -> EmailVerificationRequest:
        return await self._model.create(email_normalized=email_normalized, code_hash=code_hash, expires_at=expires_at)

    async def get_latest_unverified(self, email_normalized: str) -> EmailVerificationRequest | None:
        return (
            await self._model.filter(email_normalized=email_normalized, verified_at=None)
            .order_by("-created_at")
            .first()
        )

    async def increment_attempt(self, instance: EmailVerificationRequest) -> None:
        instance.attempt_count += 1
        await instance.save(update_fields=["attempt_count"])

    async def mark_verified(self, instance: EmailVerificationRequest) -> None:
        from datetime import UTC, datetime

        instance.verified_at = datetime.now(UTC)
        await instance.save(update_fields=["verified_at"])
