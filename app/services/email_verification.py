import asyncio
import hashlib
import secrets
import string
from datetime import UTC, datetime, timedelta

from fastapi.exceptions import HTTPException
from starlette import status

from app.core import config
from app.core.config import Env
from app.core.email.sender import EmailSendError, send_already_registered_email, send_verification_email
from app.repositories.email_verification_repository import EmailVerificationRepository

CODE_LENGTH = 6
CODE_EXPIRE_MINUTES = 10
MAX_ATTEMPTS = 5


def _hash_code(code: str) -> str:
    # 비밀번호가 아니라 10분짜리 1회용 코드라 bcrypt는 과함. sha256로 충분.
    return hashlib.sha256(code.encode()).hexdigest()


def _generate_code() -> str:
    # ⚠️ 2026-09-02 리뷰 반영: random은 예측 가능한 PRNG라 인증번호처럼 보안이 필요한
    # 값에는 부적합함. secrets(CSPRNG)로 교체.
    return "".join(secrets.choice(string.digits) for _ in range(CODE_LENGTH))


def _smtp_configured() -> bool:
    return bool(config.SMTP_USERNAME and config.SMTP_APP_PASSWORD)


class EmailVerificationService:
    """A03(이메일 회원가입) -> A04(인증번호 입력) 화면 흐름 대응.

    Gmail SMTP 발송 연동됨. .env에 SMTP_USERNAME·SMTP_APP_PASSWORD가 채워져 있으면
    실제로 이메일을 보내고, 안 채워져 있으면(로컬 개발 초기 등) 예전처럼 dev_only_code로만
    동작함 — 이메일 서버 설정 없이도 계속 개발할 수 있게 하기 위함.
    """

    def __init__(self):
        self.repo = EmailVerificationRepository()

    async def request_code(self, email: str) -> dict:
        email_normalized = email.strip().lower()
        code = _generate_code()
        expires_at = datetime.now(UTC) + timedelta(minutes=CODE_EXPIRE_MINUTES)

        await self.repo.create(email_normalized=email_normalized, code_hash=_hash_code(code), expires_at=expires_at)

        email_sent = False
        if _smtp_configured():
            try:
                # smtplib는 동기 라이브러리라 to_thread로 감싸서 이벤트 루프를 막지 않게 함
                await asyncio.to_thread(send_verification_email, email_normalized, code)
                email_sent = True
            except EmailSendError as exc:
                if config.ENV == Env.PROD:
                    raise HTTPException(
                        status_code=status.HTTP_502_BAD_GATEWAY,
                        detail="이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.",
                    ) from exc
                # 개발 환경이면 발송 실패해도 dev_only_code로 계속 테스트 가능하게 그냥 넘어감
        elif config.ENV == Env.PROD:
            # ⚠️ 2026-09-02 리뷰 반영: 예전엔 여기서 그냥 넘어가서, PROD인데 SMTP 환경변수를
            # 안 채워도(배포 설정 실수) 조용히 dev_only_code가 응답에 그대로 노출됐음(아래
            # "if config.ENV != Env.PROD" 조건이 실수로 "or not email_sent"까지 포함해서
            # PROD+미발송 케이스를 걸러내지 못했던 버그). 이제는 배포 설정 오류를 응답에 인증
            # 번호를 흘리는 대신 500으로 즉시 실패시켜서 운영에서 바로 알아채게 함.
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="이메일 발송 설정이 되어 있지 않습니다.",
            )

        result = {
            "message": "인증번호를 이메일로 보냈습니다." if email_sent else "인증번호를 생성했습니다.",
            "expires_in_seconds": CODE_EXPIRE_MINUTES * 60,
        }
        # ⚠️ PROD에서는 이제 절대 노출 안 함 (위에서 PROD+SMTP미설정은 이미 500으로 막았고,
        # PROD+SMTP설정+발송실패는 위에서 이미 502로 막아서 여기까지 오지 않음).
        if config.ENV != Env.PROD:
            result["dev_only_code"] = code
        return result

    async def notify_already_registered(self, email: str) -> dict:
        """⚠️ 2026-09-03 리뷰 반영: request_email_verification에서 "이미 가입된 이메일"을
        즉시 409로 알려주면, 공격자가 이메일 주소를 넣어보며 "이 사람이 이 앱(만성질환
        관리 앱)을 쓰는지" 알아낼 수 있음(가입 여부 자체가 민감정보). 이 경우 인증번호
        코드는 만들지 않고 안내 메일만 보내되, request_code()와 응답 모양(키)은 최대한
        똑같이 맞춰서 신규 가입 요청과 구분이 안 되게 함.
        """

        email_normalized = email.strip().lower()
        email_sent = False
        if _smtp_configured():
            try:
                await asyncio.to_thread(send_already_registered_email, email_normalized)
                email_sent = True
            except EmailSendError:
                # 이미 가입된 계정 안내 메일 발송 실패는 사용자에게 알릴 수 없음(그러면
                # "가입된 계정이 있다"는 게 드러남) - 그냥 조용히 넘어감. 운영 모니터링은
                # 로그 레벨에서 별도로 봐야 함.
                pass

        return {
            "message": "인증번호를 이메일로 보냈습니다." if email_sent else "인증번호를 생성했습니다.",
            "expires_in_seconds": CODE_EXPIRE_MINUTES * 60,
        }

    async def verify_code(self, email: str, code: str) -> None:
        """검증만 하고 verified_at은 여기서 안 찍음 — 실제 계정 생성 성공까지 확인한 뒤
        confirm_and_consume()에서 최종 소비 처리."""

        email_normalized = email.strip().lower()
        request = await self.repo.get_latest_unverified(email_normalized)

        if request is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="인증 요청을 찾을 수 없습니다.")
        if request.expires_at < datetime.now(UTC):
            raise HTTPException(status_code=status.HTTP_410_GONE, detail="인증번호가 만료되었습니다.")
        if request.attempt_count >= MAX_ATTEMPTS:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="시도 횟수를 초과했습니다. 다시 요청해주세요."
            )

        if not secrets.compare_digest(request.code_hash, _hash_code(code)):
            # ⚠️ 2026-09-03 리뷰 반영(P3): 단순 != 비교는 타이밍 공격에 노출될 수 있음.
            # secrets.compare_digest로 상수 시간 비교.
            await self.repo.increment_attempt(request)
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="인증번호가 올바르지 않습니다.")

        await self.repo.mark_verified(request)
