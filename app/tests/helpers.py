"""테스트 전용 공용 헬퍼.

⚠️ 2026-09-02: 구 POST /auth/signup(phone_number/birth_date 필드)이 v2 온보딩 개편으로
완전히 제거되면서, 그 엔드포인트를 호출하던 기존 테스트 6개(test_signup_api.py 2개,
test_login_api.py 1개, test_token_api.py 1개, test_user_me_apis.py 2개)가 전부 CI에서
실패하고 있었음. 새 가입 흐름(이메일 인증 요청 -> 코드 확인+계정 생성)을 여기 한 곳에
모아두고 각 테스트가 재사용하도록 함.
"""

from httpx import AsyncClient


async def signup_via_email_verification(client: AsyncClient, email: str, password: str) -> None:
    """A03(이메일 인증 요청) -> A04(코드 확인 -> 계정 생성) 두 단계를 한 번에 실행.
    dev_only_code는 ENV=PROD가 아닐 때만 응답에 포함되므로(email_verification.py 참고),
    테스트는 항상 non-PROD 설정으로 도는 것을 전제로 함."""

    request_response = await client.post("/api/v1/auth/email-verification/request", json={"email": email})
    code = request_response.json()["dev_only_code"]
    await client.post(
        "/api/v1/auth/email-verification/confirm",
        json={"email": email, "code": code, "password": password},
    )
