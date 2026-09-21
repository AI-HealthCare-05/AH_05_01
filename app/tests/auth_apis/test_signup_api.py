from httpx import ASGITransport, AsyncClient
from starlette import status
from tortoise.contrib.test import TestCase

from app.main import app


class TestSignupAPI(TestCase):
    """⚠️ 2026-09-02: 구 POST /auth/signup(phone_number/birth_date 필드)은 v2 온보딩
    개편으로 완전히 제거됨(이름/성별/생년월일은 이제 온보딩 후속 단계에서 PATCH /users/me로
    채움). 이메일 인증 요청 -> 코드 확인 2단계 흐름으로 테스트를 다시 작성함."""

    async def test_signup_success(self):
        email = "test@example.com"
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            request_response = await client.post("/api/v1/auth/email-verification/request", json={"email": email})
            assert request_response.status_code == status.HTTP_200_OK
            code = request_response.json()["dev_only_code"]

            confirm_response = await client.post(
                "/api/v1/auth/email-verification/confirm",
                json={"email": email, "code": code, "password": "Password123!"},
            )
        assert confirm_response.status_code == status.HTTP_201_CREATED
        assert "access_token" in confirm_response.json()

    async def test_signup_invalid_email(self):
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post("/api/v1/auth/email-verification/request", json={"email": "invalid-email"})
        assert response.status_code == status.HTTP_422_UNPROCESSABLE_CONTENT

    async def test_signup_wrong_code(self):
        email = "wrong_code@example.com"
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await client.post("/api/v1/auth/email-verification/request", json={"email": email})
            response = await client.post(
                "/api/v1/auth/email-verification/confirm",
                json={"email": email, "code": "000000", "password": "Password123!"},
            )
        assert response.status_code == status.HTTP_400_BAD_REQUEST
