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
        # ⚠️ 2026-09-08 반영: 이 저장소만 datetime.now(UTC)를 쓰고 있었음. DB에는 "UTC 숫자"가
        # 들어가는데 조회할 때 Asia/Seoul 라벨이 붙어서 9시간 어긋난 시각으로 읽힘
        # (email_verification.request_code() 주석에 원인 전체 설명). verified_at은 지금 비교에
        # 쓰이진 않지만, 다른 저장소들과 같은 기준으로 맞춰둬야 나중에 이 값을 읽는 코드가
        # 생겼을 때 같은 버그가 반복되지 않음.
        from datetime import datetime

        from app.core import config

        instance.verified_at = datetime.now(config.TIMEZONE)
        await instance.save(update_fields=["verified_at"])
