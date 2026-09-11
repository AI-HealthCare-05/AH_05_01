from datetime import datetime
from typing import Any

from pydantic import EmailStr

from app.core import config
from app.core.logger import default_logger
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

    # ===== 2026-09-09 추가: 구글 계정 연동 로그인 =====

    async def get_user_by_google_sub(self, google_sub: str) -> User | None:
        """구글 sub로 조회. google_sub가 UNIQUE라 결과는 0개 또는 1개."""

        return await self._model.get_or_none(google_sub=google_sub)

    async def find_user_for_google_link(self, email: str) -> User | None:
        """구글 최초 로그인 시 "같은 이메일로 이미 가입한 계정"을 찾음.

        ⚠️ 일부러 get_or_none()이 아니라 filter().first()를 씁니다. users.email에는 아직
        UNIQUE 제약이 없어서(중복 방지가 애플리케이션 코드로만 되어 있음) 이론상 같은
        이메일 행이 둘일 수 있는데, get_or_none()은 그 경우 MultipleObjectsReturned로
        터집니다. 로그인 경로가 예외로 죽는 것보다 가장 오래된 계정을 쓰고 경고를 남기는
        쪽이 낫습니다. (근본 해결은 email에 UNIQUE 인덱스를 거는 것 - 별도 작업)

        대소문자: 가입 시엔 사용자가 입력한 그대로 저장되고 구글은 소문자로 내려주므로,
        iexact로 비교해야 "Hong@gmail.com"으로 가입한 계정을 찾을 수 있습니다.
        """

        matches = await self._model.filter(email__iexact=email).order_by("id")
        if len(matches) > 1:
            default_logger.warning(
                "구글 연결 대상 이메일이 중복되어 있음(가장 오래된 계정을 사용): email=%s count=%s",
                email,
                len(matches),
            )
        return matches[0] if matches else None

    async def create_user_from_google(self, email: str, google_sub: str, name: str | None) -> User:
        """구글로 처음 들어온 사용자의 계정 생성.

        비밀번호는 아예 없습니다(hashed_password=None). 이름은 구글 프로필에서 받아오되,
        users.name이 20자 제한이라 넘치면 잘라서 넣습니다 - 여기서 500이 나면 로그인
        자체가 실패하는데, 이름은 온보딩에서 어차피 다시 확인받는 값이라 잘라도 무방합니다.
        """

        safe_name = name.strip()[:20] if name and name.strip() else None
        return await self._model.create(
            email=email,
            hashed_password=None,
            google_sub=google_sub,
            name=safe_name,
        )

    async def link_google_sub(self, user_id: int, google_sub: str) -> None:
        """이미 있는 계정(이메일 가입)에 구글 계정을 연결."""

        await self._model.filter(id=user_id).update(
            google_sub=google_sub,
            updated_at=datetime.now(config.TIMEZONE),
        )
