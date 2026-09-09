from datetime import datetime, timedelta
from typing import TYPE_CHECKING, Any, Self
from uuid import uuid4

from app.core import config
from app.core.jwt.exceptions import ExpiredTokenError, TokenBackendError, TokenBackendExpiredError, TokenError
from app.core.jwt.state import token_backend
from app.models.users import User

if TYPE_CHECKING:
    from app.core.jwt.backends import TokenBackend


class Token:
    token_type: str | None = None
    lifetime: timedelta | None = None
    _token_backend: "TokenBackend" = token_backend

    def __init__(self, token: str | None = None, verify: bool = True) -> None:
        if not self.token_type:
            raise TokenError("token_type must be set")
        if not self.lifetime:
            raise TokenError("lifetime must be set")

        self.token = token
        self.current_time = datetime.now(tz=config.TIMEZONE)
        self.payload: dict[str, Any] = {}

        if token is not None:
            try:
                self.payload = token_backend.decode(token, verify=verify)
            except TokenBackendExpiredError as err:
                raise ExpiredTokenError("Token is expired") from err
            except TokenBackendError as err:
                raise TokenError("Token is invalid") from err
        else:
            self.payload = {"type": self.token_type}
            self.set_exp(from_time=self.current_time, lifetime=self.lifetime)
            self.set_jti()

    def __repr__(self) -> str:
        return repr(self.payload)

    def __getitem__(self, key: str):
        return self.payload[key]

    def __setitem__(self, key: str, value: Any) -> None:
        self.payload[key] = value

    def __delitem__(self, key: str) -> None:
        del self.payload[key]

    def __contains__(self, key: str) -> Any:
        return key in self.payload

    def __str__(self) -> str:
        """
        Signs and returns a token as a base64 encoded string.
        """
        return self._token_backend.encode(self.payload)

    def set_exp(self, from_time: datetime | None = None, lifetime: timedelta | None = None) -> None:
        if from_time is None:
            from_time = self.current_time

        if lifetime is None:
            lifetime = self.lifetime

        assert lifetime is not None

        dt = from_time + lifetime
        # ⚠️ 2026-09-08 반영: 예전엔 timegm(dt.timetuple())을 썼는데, timetuple()은 타임존을
        # 버리고 벽시계 숫자만 넘기고 timegm()은 그 숫자를 UTC로 해석함. current_time이
        # KST(datetime.now(tz=config.TIMEZONE))라서 exp가 항상 9시간 뒤로 밀렸음 - 액세스
        # 토큰이 설정값 60분이 아니라 약 9시간 30분씩 살아 있었고, 리프레시 토큰도 마찬가지.
        # aware datetime의 .timestamp()는 타임존을 반영해서 올바른 epoch을 돌려줌.
        self.payload["exp"] = int(dt.timestamp())

    def set_jti(self) -> None:
        self.payload["jti"] = uuid4().hex

    @classmethod
    def for_user(cls, user: User) -> Self:
        token = cls()
        token["user_id"] = user.id
        return token


class AccessToken(Token):
    token_type = "access"
    lifetime = timedelta(minutes=config.ACCESS_TOKEN_EXPIRE_MINUTES)


class RefreshToken(Token):
    token_type = "refresh"
    # ⚠️ 2026-09-08 반영: 설정값 이름은 REFRESH_TOKEN_EXPIRE_MINUTES(= 14*24*60 = 20160분,
    # 즉 14일 의도)인데 timedelta(days=...)에 넣고 있어서 실제로는 20160일(약 55년)짜리
    # 리프레시 토큰이 발급되고 있었음 - 사실상 만료되지 않는 토큰이라 유출되면 회수할 방법이
    # 없었음. 바로 위 AccessToken은 minutes=로 맞게 쓰고 있어서 이 줄만 단위가 어긋나 있었음.
    lifetime = timedelta(minutes=config.REFRESH_TOKEN_EXPIRE_MINUTES)
    no_copy_claims = ("type", "exp", "jti")

    @property
    def rotated(self) -> "RefreshToken":
        """⚠️ 2026-09-08 추가(리프레시 토큰 재발급 = rotation): 갱신할 때마다 만료 시각을
        새로 잡은 리프레시 토큰을 발급함.

        예전엔 refresh_jwt()가 액세스 토큰만 새로 주고 리프레시 토큰은 로그인 때 받은 것을
        계속 썼음. 그러면 14일이 "마지막 사용 후 14일"이 아니라 "로그인 후 14일"이라, 매일
        쓰는 사용자도 2주째 되는 날 갑자기 로그아웃됐음.
        이제 갱신할 때마다 시계가 다시 시작되므로, 14일 안에 한 번이라도 앱을 열면 재로그인이
        없고 14일 넘게 안 쓴 세션만 만료됨.

        exp·jti는 no_copy_claims라 새로 만들어지고(=새 만료 시각, 새 식별자), user_id 같은
        나머지 클레임만 그대로 옮김 - access_token 프로퍼티와 같은 방식.

        ⚠️ 한계: 예전 리프레시 토큰을 무효화(블랙리스트)하지는 않음. 그러려면 서버에 토큰
        저장소가 필요해서 별도 작업임. 지금은 만료 전까지는 옛 토큰도 계속 유효함.
        """

        fresh = RefreshToken()
        no_copy = self.no_copy_claims
        for claim, value in self.payload.items():
            if claim in no_copy:
                continue
            fresh[claim] = value
        return fresh

    @property
    def access_token(self) -> AccessToken:
        access = AccessToken()
        access.set_exp(from_time=self.current_time)

        no_copy = self.no_copy_claims
        for claim, value in self.payload.items():
            if claim in no_copy:
                continue
            access[claim] = value

        return access
