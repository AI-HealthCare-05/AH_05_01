from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Cookie, Depends, HTTPException, Request, status
from fastapi.responses import JSONResponse as Response

from app.core import config, default_logger
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


def _cookie_domain_for(http_request: Request) -> str | None:
    """실제 요청이 들어온 호스트에 실제로 붙일 수 있는 Domain만 돌려준다(아니면 None).

    ⚠️ 2026-09-08 반영 — "로그인하고 한 시간쯤 지나면 로그아웃된다"의 진짜 원인.

    설정이 COOKIE_DOMAIN=localhost 였는데 앱은 ngrok 주소(last-broiling-tartly.ngrok-free.dev)로
    붙고 있었음. 그래서 로그인 응답이 이렇게 나갔음:

        Set-Cookie: refresh_token=...; Domain=localhost; Path=/; HttpOnly; expires=...

    RFC 6265 규칙상 Domain 속성이 붙어 있으면 요청 호스트와 domain-match 해야만 저장된다.
    'last-broiling-tartly.ngrok-free.dev' 는 'localhost' 와 매치되지 않으므로 OkHttp의
    Cookie.parse()가 null을 돌려주고, 쿠키는 CookieJar에 **도착조차 하지 않았음**.
    (브라우저도 똑같이 버림 - 안드로이드만의 문제가 아님)

    결과적으로 refresh_token이 클라이언트에 한 번도 저장된 적이 없었고,
    액세스 토큰 수명(ACCESS_TOKEN_EXPIRE_MINUTES=60분)이 끝나는 순간 첫 401에서
    /auth/token/refresh가 쿠키 없이 호출돼 401 -> 세션 만료 화면으로 튕겼음.
    PersistentCookieJar도, 리프레시 토큰 rotation도 전부 정상이었는데 저장할 쿠키
    자체가 없었던 것.

    이제는 설정값이 요청 호스트와 맞을 때만 Domain을 붙이고, 안 맞으면 아예 생략해서
    host-only 쿠키로 내려보낸다(= 그 호스트에 정확히 붙는 쿠키). localhost로 접속하면
    예전과 똑같이 동작하고, ngrok/사내망 IP/실제 도메인 어디로 붙어도 항상 저장된다.
    """

    configured = (config.COOKIE_DOMAIN or "").strip().lstrip(".").lower()
    if not configured:
        return None

    host = (http_request.url.hostname or "").lower()
    if host == configured or host.endswith(f".{configured}"):
        return configured

    default_logger.warning(
        "COOKIE_DOMAIN=%r 이(가) 요청 호스트 %r 와 맞지 않아 Domain 속성을 생략함(host-only 쿠키로 발급). "
        "이 값이 안 맞으면 클라이언트가 refresh_token을 저장하지 못해 액세스 토큰 만료 시 바로 로그아웃됨.",
        configured,
        host,
    )
    return None


def _issue_login_response(http_request: Request, tokens: dict) -> Response:
    resp = Response(
        content=LoginResponse(access_token=str(tokens["access_token"])).model_dump(), status_code=status.HTTP_200_OK
    )
    resp.set_cookie(
        key="refresh_token",
        value=str(tokens["refresh_token"]),
        httponly=True,
        # ⚠️ 2026-09-08: 예전엔 ENV==PROD 일 때만 secure=True 였는데, 지금 개발도 ngrok(https)
        # 으로 붙기 때문에 https 요청이면 항상 secure를 켠다. http로 붙는 로컬 테스트에서는
        # secure를 켜면 쿠키가 아예 저장되지 않으므로 그때만 끔.
        secure=http_request.url.scheme == "https" or config.ENV == Env.PROD,
        domain=_cookie_domain_for(http_request),
        # ⚠️ 2026-09-08 반영: 두 군데가 틀려 있었음.
        # 1) 리프레시 토큰 쿠키인데 access_token의 만료값을 넣고 있었음(수명이 서로 다름).
        # 2) 그 값이 에포크 초(예: 1788843927)인데, 파이썬 http.cookies는 정수 expires를
        #    "지금부터 그만큼 초 뒤"로 해석함 - 결과적으로 Expires에 2083년이 찍혔음.
        # datetime을 넘기면 그대로 날짜로 포맷되므로, 리프레시 토큰의 실제 만료 시각을 씀.
        expires=datetime.fromtimestamp(tokens["refresh_token"].payload["exp"], tz=UTC),
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
    http_request: Request,
    request: EmailVerificationConfirmRequest,
    auth_service: Annotated[AuthService, Depends(AuthService)],
) -> Response:
    """A04: 인증번호 6자리 확인 -> 계정 생성 + 자동 로그인.
    (이름/성별/생년월일 등은 아직 없음, 다음 온보딩 단계에서 PATCH /users/me로 채움)"""

    user = await auth_service.signup_after_email_verification(
        email=str(request.email), code=request.code, password=request.password
    )
    tokens = await auth_service.login(user)
    resp = _issue_login_response(http_request, tokens)
    resp.status_code = status.HTTP_201_CREATED
    return resp


@auth_router.post("/login", response_model=LoginResponse, status_code=status.HTTP_200_OK)
async def login(
    http_request: Request,
    request: LoginRequest,
    auth_service: Annotated[AuthService, Depends(AuthService)],
) -> Response:
    user = await auth_service.authenticate(request)
    tokens = await auth_service.login(user)
    return _issue_login_response(http_request, tokens)


@auth_router.get("/token/refresh", response_model=TokenRefreshResponse, status_code=status.HTTP_200_OK)
async def token_refresh(
    http_request: Request,
    jwt_service: Annotated[JwtService, Depends(JwtService)],
    refresh_token: Annotated[str | None, Cookie()] = None,
) -> Response:
    if not refresh_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh token is missing.")
    # ⚠️ 2026-09-08 반영(rotation): refresh_jwt()가 이제 액세스·리프레시를 한 쌍으로 돌려줌.
    # 로그인 응답과 똑같이 _issue_login_response로 내보내서 **새 리프레시 토큰 쿠키까지**
    # 같이 내려감 - 이걸 안 하면 클라이언트가 옛 쿠키를 계속 써서 rotation이 무의미해짐.
    # 응답 본문 모양은 로그인과 같으므로(TokenRefreshResponse는 LoginResponse를 그대로 상속)
    # response_model도 그대로 맞음.
    tokens = jwt_service.refresh_jwt(refresh_token)
    return _issue_login_response(http_request, tokens)
