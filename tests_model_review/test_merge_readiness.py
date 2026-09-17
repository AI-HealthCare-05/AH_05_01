"""PR #15의 계정·동의·모델 승인 리뷰 지적에 대한 회귀 검사."""

import time
from datetime import UTC, datetime
from decimal import Decimal
from unittest.mock import AsyncMock, patch

import pytest
import pytest_asyncio
from fastapi import HTTPException
from pydantic import ValidationError
from tortoise import Tortoise

from app.core import config
from app.core.db.databases import TORTOISE_APP_MODELS
from app.core.oauth import google
from app.dtos.auth import GoogleLoginRequest, GoogleSignupConsent
from app.dtos.users import AccountDeleteRequest, PasswordChangeRequest, UserInfoResponse
from app.models.accounts import ConsentPurpose, UserConsent
from app.models.health import HealthInputSnapshot
from app.models.prediction import ApprovedModelVersion
from app.models.users import User
from app.repositories.prediction_repository import PredictionRepository
from app.services.auth import AuthService
from app.services.users import UserManageService
from app.services.waist_estimate_service import MODEL_VERSION, SUBMODEL_TYPE, WaistEstimateService

IDENTITY = google.GoogleIdentity("google-sub", "qa@example.com", True, "QA", None)
REQUIRED = [
    ConsentPurpose.TERMS_OF_SERVICE,
    ConsentPurpose.PRIVACY_POLICY,
    ConsentPurpose.AGE_OVER_14,
    ConsentPurpose.HEALTH_DATA_USAGE,
]


@pytest_asyncio.fixture(loop_scope="function")
async def database():
    # 별도 메모리 DB에서 트랜잭션 롤백과 제약조건을 검증한다.
    await Tortoise.init(db_url="sqlite://:memory:", modules={"models": TORTOISE_APP_MODELS})
    await Tortoise.generate_schemas()
    yield
    await Tortoise.close_connections()


def agreements(purposes=REQUIRED):
    return [GoogleSignupConsent(purpose=p, document_version="v1") for p in purposes]


@pytest.mark.parametrize("missing", REQUIRED)
async def test_google_signup_rejects_each_missing_required_consent(database, missing):
    with patch("app.services.auth.verify_google_id_token", return_value=IDENTITY):
        with pytest.raises(HTTPException) as exc:
            await AuthService().login_with_google(
                "signed-token",
                signup_confirmed=True,
                consents=agreements([p for p in REQUIRED if p != missing]),
            )
    assert exc.value.status_code == 422
    assert await User.all().count() == 0


async def test_google_signup_commits_account_and_only_chosen_consents(database):
    with patch("app.services.auth.verify_google_id_token", return_value=IDENTITY):
        user, created = await AuthService().login_with_google(
            "signed-token",
            signup_confirmed=True,
            consents=agreements([*REQUIRED, ConsentPurpose.HEALTH_REFERENCE_ANALYSIS]),
        )
    assert created and user.requires_google_reauth
    assert UserInfoResponse.model_validate(user).requires_google_reauth
    rows = await UserConsent.filter(user=user)
    assert {r.purpose for r in rows} == {*REQUIRED, ConsentPurpose.HEALTH_REFERENCE_ANALYSIS}
    assert all(r.document_version == "v1" and r.status == "AGREED" for r in rows)


async def test_google_signup_rolls_back_account_when_consent_storage_fails(database):
    with patch("app.services.auth.verify_google_id_token", return_value=IDENTITY):
        with patch.object(UserConsent, "create", new=AsyncMock(side_effect=RuntimeError("storage failure"))):
            with pytest.raises(RuntimeError):
                await AuthService().login_with_google("signed-token", signup_confirmed=True, consents=agreements())
    assert await User.all().count() == 0
    assert await UserConsent.all().count() == 0


def test_google_signup_rejects_unknown_document_version():
    with pytest.raises(ValidationError):
        GoogleLoginRequest(
            id_token="token",
            signup_confirmed=True,
            consents=[{"purpose": "TERMS_OF_SERVICE", "document_version": "unknown"}],
        )


async def test_returning_google_login_does_not_restore_withdrawn_consent(database):
    user = await User.create(email=IDENTITY.email, google_sub=IDENTITY.subject)
    row = await UserConsent.create(
        user=user, purpose=ConsentPurpose.HEALTH_REFERENCE_ANALYSIS, document_version="v1", status="WITHDRAWN"
    )
    with patch("app.services.auth.verify_google_id_token", return_value=IDENTITY):
        found, created = await AuthService().login_with_google(
            "signed-token", signup_confirmed=True, consents=agreements()
        )
    assert found.id == user.id and not created
    await row.refresh_from_db()
    assert row.status == "WITHDRAWN"


async def test_google_account_can_set_first_password(database):
    user = await User.create(email=IDENTITY.email, google_sub=IDENTITY.subject)
    with patch("app.services.users.verify_google_id_token", return_value=IDENTITY) as verify:
        await UserManageService().change_password(
            user,
            PasswordChangeRequest(
                new_password="New-Password1!",
                google_id_token="fresh-token",
            ),
        )
    verify.assert_called_once_with("fresh-token", max_age_seconds=300)
    await user.refresh_from_db()
    assert not user.requires_google_reauth
    assert user.hashed_password != "New-Password1!"


async def test_google_account_can_delete_after_matching_reauthentication(database):
    user = await User.create(email=IDENTITY.email, google_sub=IDENTITY.subject)
    await UserConsent.create(user=user, purpose=REQUIRED[0], document_version="v1")
    with patch("app.services.users.verify_google_id_token", return_value=IDENTITY):
        await UserManageService().delete_account(user, AccountDeleteRequest(google_id_token="fresh-token"))
    assert await User.all().count() == 0
    assert await UserConsent.all().count() == 0


async def test_different_google_account_cannot_delete_current_user(database):
    user = await User.create(email="other@example.com", google_sub="other-sub")
    with patch("app.services.users.verify_google_id_token", return_value=IDENTITY):
        with pytest.raises(HTTPException) as exc:
            await UserManageService().delete_account(user, AccountDeleteRequest(google_id_token="fresh-token"))
    assert exc.value.status_code == 403
    assert await User.filter(id=user.id).exists()


async def test_missing_credentials_do_not_delete_google_account(database):
    user = await User.create(email=IDENTITY.email, google_sub=IDENTITY.subject)
    with pytest.raises(HTTPException) as exc:
        await UserManageService().delete_account(user, AccountDeleteRequest())
    assert exc.value.status_code == 400
    assert await User.filter(id=user.id).exists()


@pytest.mark.parametrize("age", [301, -30, None])
def test_sensitive_google_actions_reject_old_future_or_missing_issue_time(monkeypatch, age):
    monkeypatch.setattr(config, "GOOGLE_CLIENT_ID", "test-client")
    payload = {
        "iss": "https://accounts.google.com",
        "sub": IDENTITY.subject,
        "email": IDENTITY.email,
        "email_verified": True,
    }
    if age is not None:
        payload["iat"] = time.time() - age
    with patch.object(google.google_id_token, "verify_oauth2_token", return_value=payload):
        with pytest.raises(HTTPException) as exc:
            google.verify_google_id_token("signed-token", max_age_seconds=300)
    assert exc.value.status_code == 401


async def test_recalculation_preserves_manual_model_disable(database):
    repo = PredictionRepository()
    row = await repo.ensure_initial_approval(SUBMODEL_TYPE, MODEL_VERSION, "SYSTEM_AUTO_APPROVAL")
    assert row.is_active
    await repo.upsert_approval(SUBMODEL_TYPE, MODEL_VERSION, False, "reviewer")
    user = await User.create(email="model-qa@example.com")
    snapshot = await HealthInputSnapshot.create(
        user=user, measured_at=datetime.now(UTC), input_values={"height_cm": 170, "weight_kg": 65}, units={}
    )
    service = WaistEstimateService()
    await service._save(user, snapshot.id, status="COMPUTED", value=Decimal("75.0"), failure_reason_code=None)
    await row.refresh_from_db()
    assert not row.is_active
    assert row.approved_by_user_id == "reviewer"
    assert await ApprovedModelVersion.all().count() == 1
