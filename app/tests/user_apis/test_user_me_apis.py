from httpx import ASGITransport, AsyncClient
from starlette import status
from tortoise.contrib.test import TestCase

from app.main import app
from app.tests.helpers import signup_via_email_verification


class TestUserMeApis(TestCase):
    async def test_get_user_me_success(self):
        # ⚠️ 2026-09-02: v2 가입 흐름에서는 이름/성별/생년월일이 가입 시점에 없음
        # (온보딩 후속 단계에서 PATCH /users/me로 채워짐) - 그래서 여기서도 가입 직후
        # name을 바로 검증하지 않고, PATCH로 채운 뒤에 GET으로 확인함.
        email = "me@example.com"
        password = "Password123!"
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await signup_via_email_verification(client, email, password)

            login_response = await client.post("/api/v1/auth/login", json={"email": email, "password": password})
            access_token = login_response.json()["access_token"]
            headers = {"Authorization": f"Bearer {access_token}"}

            await client.patch("/api/v1/users/me", json={"name": "내정보테스터"}, headers=headers)

            # 내 정보 조회
            response = await client.get("/api/v1/users/me", headers=headers)
        assert response.status_code == status.HTTP_200_OK
        assert response.json()["email"] == email
        assert response.json()["name"] == "내정보테스터"

    async def test_update_user_me_success(self):
        email = "update_me@example.com"
        password = "Password123!"
        update_data = {"name": "수정후"}
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await signup_via_email_verification(client, email, password)

            login_response = await client.post("/api/v1/auth/login", json={"email": email, "password": password})
            access_token = login_response.json()["access_token"]

            # 내 정보 수정
            headers = {"Authorization": f"Bearer {access_token}"}
            response = await client.patch("/api/v1/users/me", json=update_data, headers=headers)
        assert response.status_code == status.HTTP_200_OK
        assert response.json()["name"] == "수정후"

    async def test_get_user_me_unauthorized(self):
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/v1/users/me")
        assert response.status_code == status.HTTP_401_UNAUTHORIZED
