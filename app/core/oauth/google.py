"""구글 ID 토큰 검증 (2026-09-09 신규).

안드로이드가 Credential Manager로 로그인하면 구글이 서명한 ID 토큰(JWT)을 돌려줍니다.
이 모듈은 그 토큰이 **진짜 구글이 우리 앱에 발급한 것인지**만 확인하고, 신원 정보를
뽑아서 돌려줍니다. 우리 서비스의 로그인 처리(계정 생성·연결·JWT 발급)는 여기서 하지
않고 services/auth.py가 합니다 - 검증과 정책을 섞지 않기 위함.

⚠️ 반드시 서버에서 검증해야 합니다. 앱이 "이 사람 이메일은 hong@gmail.com이에요"라고
말한 걸 그대로 믿으면 아무나 남의 계정으로 로그인할 수 있습니다. 앱은 토큰 원문만
전달하고, 신뢰의 근거는 오직 구글의 서명입니다.

검증 항목(google-auth 라이브러리가 처리):
  - 서명이 구글 공개키와 맞는가
  - aud(수신자)가 우리 클라이언트 ID인가  <- 이게 없으면 남의 앱 토큰도 통과함
  - exp(만료)가 지나지 않았는가
여기서 추가로 확인하는 것:
  - iss(발급자)가 구글인가
  - email이 있고 email_verified가 true인가  <- 계정 연결의 전제라 가장 중요

⚠️ 2026-09-10 수정 (서버가 안 뜨던 원인):
google-auth는 서명 검증 로직만 갖고 있고 **HTTP 통신 수단은 직접 갖고 있지 않습니다.**
공식 예제가 쓰는 `google.auth.transport.requests`는 `requests` 패키지를 따로 요구해서,
그게 없으면 import 시점에 ImportError가 나고 앱 전체가 기동에 실패합니다.
`requests`를 새로 넣는 대신, 이미 쓰고 있는 httpx로 어댑터를 만들어 씁니다
(google.auth.transport.Request가 그러라고 열어둔 인터페이스입니다). 운영 의존성이
늘지 않고, 라이브러리가 실제로 쓰는 건 "구글 공개키 JSON 한 번 GET" 하나뿐입니다.
"""

import threading
import time
from dataclasses import dataclass

import httpx
from fastapi import HTTPException, status
from google.auth import exceptions as google_exceptions
from google.auth import transport as google_transport
from google.oauth2 import id_token as google_id_token

from app.core import config
from app.core.logger import default_logger

_VALID_ISSUERS = ("accounts.google.com", "https://accounts.google.com")

# 기기 시계가 몇 초 어긋나도 방금 발급된 토큰이 "아직 유효하지 않음"으로 튕기지 않게 하는 여유.
# 너무 크게 잡으면 만료된 토큰도 통과하므로 10초만 둡니다.
_CLOCK_SKEW_SECONDS = 10

# 구글 공개키를 받아올 때의 제한시간. 이 호출이 늦어지면 로그인 응답이 통째로 늦어지므로
# 짧게 잡고, 실패하면 502로 명확히 알립니다(무한정 기다리지 않음).
_HTTP_TIMEOUT_SECONDS = 5.0

# 공개키 캐시 수명. google-auth는 검증할 때마다 공개키를 새로 받아오는데, 그대로 두면
# 로그인 1회 = 구글로 나가는 왕복 1회가 되고, 구글이 잠깐만 느려도 로그인 전체가 막힙니다.
# 응답의 Cache-Control: max-age를 존중하되 아래 범위로 자릅니다.
# 위쪽을 6시간으로 제한하는 이유: 구글이 키를 교체(rotate)했을 때 낡은 키를 오래 들고
# 있지 않기 위해서입니다. 참고로 캐시가 낡으면 "검증 실패"(로그인 거절) 쪽으로 기울지,
# 통과되는 방향으로 틀리지 않습니다.
_CERTS_CACHE_MIN_SECONDS = 300
_CERTS_CACHE_MAX_SECONDS = 6 * 3600
_CERTS_CACHE_FALLBACK_SECONDS = 3600


@dataclass(frozen=True)
class GoogleIdentity:
    """검증을 통과한 구글 계정 신원."""

    subject: str  # 구글이 부여한 영구 식별자(sub). 이메일과 달리 절대 안 바뀜 - 계정 연결의 기준.
    email: str
    email_verified: bool
    name: str | None
    picture: str | None


class _HttpxResponse(google_transport.Response):
    """google-auth가 기대하는 응답 모양(status/headers/data)으로 감싸주는 얇은 껍데기."""

    def __init__(self, status_code: int, headers: dict[str, str], data: bytes) -> None:
        self._status = status_code
        self._headers = headers
        self._data = data

    @property
    def status(self) -> int:
        return self._status

    @property
    def headers(self) -> dict[str, str]:
        return self._headers

    @property
    def data(self) -> bytes:
        return self._data


class _HttpxRequest(google_transport.Request):
    """httpx로 구현한 google-auth 전송 계층.

    google-auth가 이 객체를 실제로 쓰는 곳은 공개키 조회(GET) 한 군데뿐입니다.
    같은 URL을 반복해서 부르므로 여기서 짧게 캐시합니다.
    """

    _cache: dict[str, tuple[float, int, dict[str, str], bytes]] = {}
    _lock = threading.Lock()

    def __call__(
        self,
        url: str,
        method: str = "GET",
        body: bytes | None = None,
        headers: dict[str, str] | None = None,
        timeout: float | None = None,
        **kwargs: object,
    ) -> _HttpxResponse:
        cacheable = method.upper() == "GET"

        if cacheable:
            with self._lock:
                hit = self._cache.get(url)
            if hit is not None and hit[0] > time.monotonic():
                return _HttpxResponse(hit[1], hit[2], hit[3])

        response = httpx.request(
            method=method,
            url=url,
            content=body,
            headers=headers,
            timeout=timeout if timeout is not None else _HTTP_TIMEOUT_SECONDS,
            follow_redirects=True,
        )
        result = _HttpxResponse(response.status_code, dict(response.headers), response.content)

        # 실패 응답은 절대 캐시하지 않습니다 - 구글이 잠깐 500을 냈다고 한 시간 동안
        # 로그인이 막히면 안 됩니다.
        if cacheable and response.status_code == 200:
            ttl = _parse_max_age(response.headers.get("cache-control"))
            with self._lock:
                self._cache[url] = (
                    time.monotonic() + ttl,
                    response.status_code,
                    dict(response.headers),
                    response.content,
                )

        return result


def _parse_max_age(cache_control: str | None) -> float:
    """Cache-Control 헤더에서 max-age를 꺼내 우리 범위로 자름. 없거나 이상하면 기본값."""

    seconds = _CERTS_CACHE_FALLBACK_SECONDS
    if cache_control:
        for part in cache_control.split(","):
            part = part.strip().lower()
            if part.startswith("max-age="):
                try:
                    seconds = int(part.split("=", 1)[1])
                except ValueError:
                    seconds = _CERTS_CACHE_FALLBACK_SECONDS
                break
    return float(max(_CERTS_CACHE_MIN_SECONDS, min(_CERTS_CACHE_MAX_SECONDS, seconds)))


def is_google_login_enabled() -> bool:
    return bool(config.GOOGLE_CLIENT_ID)


def verify_google_id_token(raw_token: str) -> GoogleIdentity:
    """구글 ID 토큰을 검증하고 신원을 돌려줌. 실패하면 401을 던짐.

    ⚠️ 이 함수는 동기(blocking)입니다. 공개키 캐시가 비어 있을 때 네트워크를 타므로,
    async 코드에서 그냥 부르면 그동안 서버 전체가 멈춥니다. 반드시 스레드풀로
    감싸서 부르세요(services/auth.py의 run_in_threadpool 참고).
    """

    if not is_google_login_enabled():
        # 클라이언트 ID가 없으면 aud 검증을 못 합니다. 그 상태로 통과시키면 아무 구글
        # 토큰이나 받아주는 셈이라, 검증을 느슨하게 하느니 기능 자체를 꺼둡니다.
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="구글 로그인이 아직 설정되지 않았습니다.",
        )

    try:
        payload = google_id_token.verify_oauth2_token(
            raw_token,
            _HttpxRequest(),
            config.GOOGLE_CLIENT_ID,
            clock_skew_in_seconds=_CLOCK_SKEW_SECONDS,
        )
    except google_exceptions.TransportError as exc:
        # 공개키를 못 받아온 경우. GoogleAuthError의 하위 클래스라 아래 401 블록보다
        # **반드시 먼저** 와야 합니다(순서를 바꾸면 네트워크 장애가 401로 둔갑함).
        default_logger.exception("구글 공개키 조회 실패")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="구글 인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        ) from exc
    except (ValueError, google_exceptions.GoogleAuthError) as exc:
        # 서명 불일치·aud 불일치·만료는 ValueError로 올라옵니다.
        # ⚠️ 2026-09-10 수정: iss(발급자) 불일치만 ValueError가 아니라 GoogleAuthError로
        # 올라옵니다. 전에는 이게 아래 502 블록에 걸려서, 발급자를 위조한 토큰이
        # "구글 서버에 연결하지 못했습니다"로 응답됐습니다(거절은 됐지만 원인이 반대로
        # 보였고, 장애 알림도 잘못 울렸음).
        # 사용자에게는 이유를 나누지 않고(공격자에게 힌트가 됨) 하나로 응답하되,
        # 서버 로그에는 남깁니다.
        default_logger.warning("구글 ID 토큰 검증 실패: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="구글 로그인 정보를 확인할 수 없습니다. 다시 시도해 주세요.",
        ) from exc
    except Exception as exc:  # noqa: BLE001 - httpx 타임아웃·DNS 실패 등
        # 이건 사용자 잘못이 아니라 서버가 구글에 못 붙은 경우라 502로 구분합니다.
        default_logger.exception("구글 공개키 조회 실패")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="구글 인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        ) from exc

    if payload.get("iss") not in _VALID_ISSUERS:
        default_logger.warning("구글 ID 토큰 iss 불일치: %s", payload.get("iss"))
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="구글 로그인 정보를 확인할 수 없습니다. 다시 시도해 주세요.",
        )

    subject = payload.get("sub")
    email = payload.get("email")
    # 구글은 이 값을 bool로 주기도 하고 문자열 "true"로 주기도 해서 둘 다 받아줍니다.
    raw_verified = payload.get("email_verified", False)
    email_verified = raw_verified is True or str(raw_verified).lower() == "true"

    if not subject or not email:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="구글 계정에서 이메일 정보를 받지 못했습니다.",
        )

    if not email_verified:
        # ⚠️ 여기를 건너뛰면 안 됩니다. 아래 services/auth.py가 "같은 이메일이면 기존
        # 계정에 연결"하는데, 확인되지 않은 이메일을 믿으면 남의 이메일 주소를 등록한
        # 구글 계정으로 그 사람 계정을 통째로 가져갈 수 있습니다.
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="이메일 인증이 완료되지 않은 구글 계정입니다.",
        )

    return GoogleIdentity(
        subject=str(subject),
        email=str(email).strip().lower(),
        email_verified=True,
        name=payload.get("name"),
        picture=payload.get("picture"),
    )
