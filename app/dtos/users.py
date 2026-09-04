from datetime import datetime
from typing import Annotated

from pydantic import BaseModel, EmailStr, Field, model_validator

from app.core.validators import optional_after_validator, validate_phone_number
from app.dtos.base import BaseSerializerModel
from app.models.users import Gender


class UserUpdateRequest(BaseModel):
    """v2: birthday(전체 날짜) -> birth_year/birth_month로 교체, nickname 추가.
    A07(필수 입력) 화면은 name·gender·birth_year·birth_month를 한 번에 이 API로 보냄
    (전부 채워서 보내는 게 화면 흐름상 맞지만, 서버는 부분 수정도 허용 — 유연하게 둠)."""

    name: Annotated[str | None, Field(None, min_length=1, max_length=20)]
    nickname: Annotated[str | None, Field(None, max_length=20)]
    email: Annotated[EmailStr | None, Field(None, max_length=40)]
    phone_number: Annotated[
        str | None,
        Field(None, description="Available Format: +8201011112222, 01011112222, 010-1111-2222"),
        optional_after_validator(validate_phone_number),
    ]
    birth_year: Annotated[int | None, Field(None, ge=1900, le=2100)]
    birth_month: Annotated[int | None, Field(None, ge=1, le=12)]
    gender: Annotated[Gender | None, Field(None, description="'MALE' or 'FEMALE'")]

    @model_validator(mode="after")
    def _validate_birth_year_month_pair(self) -> "UserUpdateRequest":
        # 연도만 있고 월이 없거나 그 반대인 경우를 막음 (같이 들어와야 의미가 있음)
        if (self.birth_year is None) != (self.birth_month is None):
            raise ValueError("birth_year와 birth_month는 함께 입력해야 합니다.")
        return self


class UserInfoResponse(BaseSerializerModel):
    id: int
    name: str | None = None
    nickname: str | None = None
    email: str
    phone_number: str | None = None
    birth_year: int | None = None
    birth_month: int | None = None
    gender: Gender | None = None
    created_at: datetime


class PasswordChangeRequest(BaseModel):
    """F16: 비밀번호 변경. 현재 비밀번호 확인 후 새 비밀번호로 교체."""

    current_password: str
    new_password: Annotated[str, Field(min_length=8)]


class EmailChangeRequest(BaseModel):
    """F15: 이메일 변경. A04와 같은 인증번호 검증(EmailVerificationService)을 재사용해서
    새 이메일 소유를 확인한 뒤에만 실제로 email 컬럼을 바꿈."""

    new_email: EmailStr
    code: Annotated[str, Field(min_length=6, max_length=6)]


class AccountDeleteRequest(BaseModel):
    """F17: 계정 삭제 재인증. 비밀번호 재확인 후 삭제 진행."""

    password: str
