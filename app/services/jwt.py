from typing import Literal, overload

from fastapi import HTTPException

from app.core.jwt.exceptions import ExpiredTokenError, TokenError
from app.core.jwt.tokens import AccessToken, RefreshToken
from app.models.users import User


class JwtService:
    access_token_class = AccessToken
    refresh_token_class = RefreshToken

    def create_access_token(self, user: User) -> AccessToken:
        return self.access_token_class.for_user(user)

    def create_refresh_token(self, user: User) -> RefreshToken:
        return self.refresh_token_class.for_user(user)

    @overload
    def verify_jwt(
        self,
        token: str,
        token_type: Literal["access"],
    ) -> AccessToken: ...

    @overload
    def verify_jwt(
        self,
        token: str,
        token_type: Literal["refresh"],
    ) -> RefreshToken: ...

    def verify_jwt(self, token: str, token_type: Literal["access", "refresh"]) -> AccessToken | RefreshToken:
        token_class: type[AccessToken | RefreshToken]
        if token_type == "access":
            token_class = self.access_token_class
        else:
            token_class = self.refresh_token_class

        try:
            verified = token_class(token=token)
            return verified
        except ExpiredTokenError as err:
            raise HTTPException(status_code=401, detail=f"{token_type} token has expired.") from err
        except TokenError as err:
            raise HTTPException(status_code=400, detail="Provided invalid token.") from err

    def refresh_jwt(self, refresh_token: str) -> dict[str, AccessToken | RefreshToken]:
        """⚠️ 2026-09-08 반영(rotation): 예전엔 액세스 토큰만 새로 주고 리프레시 토큰은
        로그인 때 받은 것을 계속 썼음. 그러면 리프레시 토큰 수명 14일이 "마지막 사용 후
        14일"이 아니라 "로그인 후 14일"이라, 매일 쓰는 사용자도 2주째에 강제 로그아웃됐음.
        이제 갱신할 때마다 리프레시 토큰도 새로 발급해서(RefreshToken.rotated) 만료 시계가
        다시 시작됨 - 14일 넘게 앱을 안 연 세션만 만료됨.

        호출부(auth_routers.token_refresh)는 issue_jwt_pair와 같은 모양의 dict를 받아서
        _issue_login_response로 새 쿠키까지 같이 내려줘야 함 - 새 리프레시 토큰을 쿠키로
        안 보내면 rotation이 아무 의미가 없음.
        """

        verified_rt = self.verify_jwt(token=refresh_token, token_type="refresh")
        rotated_rt = verified_rt.rotated
        return {"access_token": rotated_rt.access_token, "refresh_token": rotated_rt}

    def issue_jwt_pair(self, user: User) -> dict[str, AccessToken | RefreshToken]:
        rt = self.create_refresh_token(user)
        at = rt.access_token
        return {"access_token": at, "refresh_token": rt}
