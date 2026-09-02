from fastapi.exceptions import HTTPException
from pydantic import EmailStr
from starlette import status

from app.core.jwt.tokens import AccessToken, RefreshToken
from app.core.utils.security import hash_password, verify_password
from app.dtos.auth import LoginRequest
from app.models.users import User
from app.repositories.user_repository import UserRepository
from app.services.email_verification import EmailVerificationService
from app.services.jwt import JwtService


class AuthService:
    def __init__(self):
        self.user_repo = UserRepository()
        self.jwt_service = JwtService()
        self.email_verification_service = EmailVerificationService()

    async def signup_after_email_verification(self, email: str, code: str, password: str) -> User:
        """v2: A03(이메일+비밀번호) -> A04(인증번호) 흐름의 마지막 단계.
        인증번호가 맞아야만 계정이 실제로 생성됨. 이름/성별/생년월일 등은 아직 없음
        (온보딩 후속 단계에서 PATCH /users/me로 채워짐)."""

        await self.check_email_exists(email)
        await self.email_verification_service.verify_code(email, code)

        user = await self.user_repo.create_user_minimal(
            email=email,
            hashed_password=hash_password(password),
        )
        return user

    async def authenticate(self, data: LoginRequest) -> User:
        email = str(data.email)
        user = await self.user_repo.get_user_by_email(email)
        if not user:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST, detail="이메일 또는 비밀번호가 올바르지 않습니다."
            )

        if not verify_password(data.password, user.hashed_password):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST, detail="이메일 또는 비밀번호가 올바르지 않습니다."
            )

        if not user.is_active:
            raise HTTPException(status_code=status.HTTP_423_LOCKED, detail="비활성화된 계정입니다.")

        return user

    async def login(self, user: User) -> dict[str, AccessToken | RefreshToken]:
        await self.user_repo.update_last_login(user.id)
        return self.jwt_service.issue_jwt_pair(user)

    async def check_email_exists(self, email: str | EmailStr) -> None:
        if await self.user_repo.exists_by_email(email):
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 사용중인 이메일입니다.")

    async def check_phone_number_exists(self, phone_number: str) -> None:
        if await self.user_repo.exists_by_phone_number(phone_number):
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 사용중인 휴대폰 번호입니다.")
