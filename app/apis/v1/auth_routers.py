from typing import Annotated

from fastapi import APIRouter, Cookie, Depends, HTTPException, status
from fastapi.responses import JSONResponse as Response

from app.core import config
from app.core.config import Env
from app.dtos.auth import (
    EmailVerificationConfirmRequest,
    EmailVerificationRequestRequest,
    LoginRequest,
    LoginResponse,
    TokenRefreshResponse,
)
from app.services.auth import AuthService
from app.services.email_verification import EmailVerificationService
from app.services.jwt import JwtService

auth_router = APIRouter(prefix="/auth", tags=["auth"])


def _issue_login_response(tokens: dict) -> Response:
    resp = Response(
        content=LoginResponse(access_token=str(tokens["access_token"])).model_dump(), status_code=status.HTTP_200_OK
    )
    resp.set_cookie(
        key="refresh_token",
        value=str(tokens["refresh_token"]),
        httponly=True,
        secure=True if config.ENV == Env.PROD else False,
        domain=config.COOKIE_DOMAIN or None,
        expires=tokens["access_token"].payload["exp"],
    )
    return resp


@auth_router.post("/email-verification/request", status_code=status.HTTP_200_OK)
async def request_email_verification(
    request: EmailVerificationRequestRequest,
    auth_service: Annotated[AuthService, Depends(AuthService)],
    email_verification_service: Annotated[EmailVerificationService, Depends(EmailVerificationService)],
) -> Response:
    """A03: 이메일 입력 후 인증번호 요청. (아직 계정은 안 만들어짐)

    ⚠️ 2026-09-03 리뷰 반영: 이미 가입된 이메일이면 예전엔 즉시 409를 돌려줘서, 공격자가
    이메일 주소를 넣어보며 "이 사람이 이 앱을 쓰는지" 알아낼 수 있었음(만성질환 관리
    앱이라 가입 여부 자체가 민감정보). 이제 가입 여부와 무관하게 항상 같은 200을 주고,
    이미 가입된 주소면 인증번호 대신 "이미 가입된 계정입니다" 안내 메일만 보냄.
    """

    if await auth_service.email_exists(str(request.email)):
        result = await email_verification_service.notify_already_registered(str(request.email))
    else:
        result = await email_verification_service.request_code(str(request.email))
    return Response(content=result, status_code=status.HTTP_200_OK)


@auth_router.post("/email-verification/confirm", status_code=status.HTTP_201_CREATED)
async def confirm_email_verification(
    request: EmailVerificationConfirmRequest,
    auth_service: Annotated[AuthService, Depends(AuthService)],
) -> Response:
    """A04: 인증번호 6자리 확인 -> 계정 생성 + 자동 로그인.
    (이름/성별/생년월일 등은 아직 없음, 다음 온보딩 단계에서 PATCH /users/me로 채움)"""

    user = await auth_service.signup_after_email_verification(
        email=str(request.email), code=request.code, password=request.password
    )
    tokens = await auth_service.login(user)
    resp = _issue_login_response(tokens)
    resp.status_code = status.HTTP_201_CREATED
    return resp


@auth_router.post("/login", response_model=LoginResponse, status_code=status.HTTP_200_OK)
async def login(
    request: LoginRequest,
    auth_service: Annotated[AuthService, Depends(AuthService)],
) -> Response:
    user = await auth_service.authenticate(request)
    tokens = await auth_service.login(user)
    return _issue_login_response(tokens)


@auth_router.get("/token/refresh", response_model=TokenRefreshResponse, status_code=status.HTTP_200_OK)
async def token_refresh(
    jwt_service: Annotated[JwtService, Depends(JwtService)],
    refresh_token: Annotated[str | None, Cookie()] = None,
) -> Response:
    if not refresh_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token is missing.")
    access_token = jwt_service.refresh_jwt(refresh_token)
    return Response(
        content=TokenRefreshResponse(access_token=str(access_token)).model_dump(), status_code=status.HTTP_200_OK
    )
