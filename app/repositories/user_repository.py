from datetime import datetime
from typing import Any

from pydantic import EmailStr

from app.core import config
from app.models.users import User

# v2: birthday -> birth_year/birth_month, name/gender/phone_number 전부 온보딩 후반부에 채워짐
ALLOWED_UPDATE_FIELDS = ["name", "nickname", "phone_number", "gender", "birth_year", "birth_month"]
UPDATED_AT_FIELD = "updated_at"


class UserRepository:
    def __init__(self):
        self._model = User

    async def get_all(self):
        return await self._model.all()

    async def get_user(self, user_id: int) -> User | None:
        return await self._model.get_or_none(id=user_id)

    async def create_user_minimal(self, email: str | EmailStr, hashed_password: str) -> User:
        """v2: 이메일 인증 완료 직후 생성되는 계정. 이 시점엔 이메일+비밀번호뿐이고
        나머지(이름/성별/생년월일 등)는 온보딩 후속 단계(PATCH /users/me)에서 채워짐."""

        return await self._model.create(email=email, hashed_password=hashed_password)

    async def get_user_by_email(self, email: str) -> User | None:
        return await self._model.get_or_none(email=email)

    async def exists_by_email(self, email: str) -> bool:
        return await self._model.filter(email=email).exists()

    async def exists_by_phone_number(self, phone_number: str) -> bool:
        return await self._model.filter(phone_number=phone_number).exists()

    async def update_last_login(self, user_id: int) -> None:
        await self._model.filter(id=user_id).update(last_login=datetime.now(config.TIMEZONE))

    async def update_instance(self, user: User, data: dict[str, Any]) -> None:
        update_fields = []
        for key, value in data.items():
            if value is not None:
                setattr(user, key, value)
                update_fields.append(key)
        if update_fields:
            user.updated_at = datetime.now(config.TIMEZONE)
            update_fields.append(UPDATED_AT_FIELD)
            await user.save(update_fields=update_fields)
