from typing import Annotated

from pydantic import AfterValidator, BaseModel, EmailStr, Field

from app.core.validators import validate_password


class EmailVerificationRequestRequest(BaseModel):
    """A03: 이메일 회원가입 첫 화면 - 이메일+비밀번호 입력, 인증번호 요청."""

    email: Annotated[EmailStr, Field(max_length=40)]


class EmailVerificationConfirmRequest(BaseModel):
    """A04: 인증번호 6자리 입력 -> 확인되면 이 정보로 계정 생성.
    비밀번호는 A03에서 이미 입력했지만, 재전송/새로고침 등에도 안전하게 여기서 다시 받음."""

    email: Annotated[EmailStr, Field(max_length=40)]
    code: Annotated[str, Field(min_length=6, max_length=6)]
    password: Annotated[str, Field(min_length=8), AfterValidator(validate_password)]


class LoginRequest(BaseModel):
    email: EmailStr
    password: Annotated[str, Field(min_length=8)]


class LoginResponse(BaseModel):
    access_token: str


class TokenRefreshResponse(LoginResponse): ...
