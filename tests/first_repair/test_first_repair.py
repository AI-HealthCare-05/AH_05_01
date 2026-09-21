"""첫 선물의 영속성·중복 방지·기존 계정 보존을 격리된 DB에서 확인한다."""

import asyncio
from unittest.mock import patch

import pytest
import pytest_asyncio
from fastapi import FastAPI, HTTPException
from httpx import ASGITransport, AsyncClient
from tortoise import Tortoise
from tortoise.transactions import in_transaction

from app.apis.v1.companion_routers import companion_router
from app.core.db.databases import TORTOISE_APP_MODELS
from app.dependencies.security import get_request_user
from app.models.challenges import Challenge, PointLedger
from app.models.companion import CompanionFirstRepair, CompanionStageLog, CompanionState
from app.models.notifications import DailyActionSummary
from app.models.users import User
from app.repositories.companion_repository import CompanionRepository
from app.repositories.user_repository import UserRepository
from app.services.companion_service import CompanionService

pytestmark = pytest.mark.asyncio(loop_scope="function")


@pytest_asyncio.fixture(autouse=True, loop_scope="function")
async def isolated_db():
    await Tortoise.init(db_url="sqlite://:memory:", modules={"models": TORTOISE_APP_MODELS}, timezone="Asia/Seoul")
    await Tortoise.generate_schemas()
    yield
    await Tortoise.close_connections()


async def new_user():
    return await UserRepository().create_user_minimal("new@example.test", "test-hash")


async def test_email_and_google_new_accounts_are_eligible():
    email = await new_user()
    google = await UserRepository().create_user_from_google("google@example.test", "subject-test", "지우")
    for user in (email, google):
        response = await CompanionService().get_first_repair(user)
        assert response.status == "ELIGIBLE"
        assert response.gift_count == response.companion.total_materials == response.companion.current_stage == 0


async def test_existing_account_and_google_link_keep_materials_and_stages():
    user = await User.create(email="existing@example.test", hashed_password="test-hash")
    await CompanionState.create(user=user, five_element_completion_counts={"WOOD": 41})
    await UserRepository().link_google_sub(user.id, "existing-subject")
    service = CompanionService()
    response = await service.get_first_repair(user)
    assert response.status == "UNAVAILABLE"
    assert (response.companion.current_stage, response.companion.total_materials) == (3, 41)
    with pytest.raises(HTTPException) as error:
        await service.advance_first_repair(user, complete=False)
    assert error.value.status_code == 409
    assert await CompanionFirstRepair.all().count() == 0


async def test_received_gift_resumes_without_counting_as_repaired_or_exercise():
    user = await new_user()
    service = CompanionService()
    for _ in range(2):
        response = await service.advance_first_repair(user, complete=False)
        assert response.status == "GIFT_RECEIVED"
        assert response.gift_count == 1
        assert response.companion.current_stage == response.companion.total_materials == 0
    restored = await CompanionService().get_first_repair(user)
    assert restored.status == "GIFT_RECEIVED"
    assert await CompanionStageLog.all().count() == 0


async def test_completion_requires_receiving_the_gift_first():
    user = await new_user()
    with pytest.raises(HTTPException) as error:
        await CompanionService().advance_first_repair(user, complete=True)
    assert error.value.status_code == 409
    assert (await CompanionService().get_first_repair(user)).status == "ELIGIBLE"


async def test_retries_resume_one_completed_repair_without_exercise_or_duplicate_celebration():
    user = await new_user()
    service = CompanionService()
    await service.advance_first_repair(user, complete=False)
    for _ in range(3):
        await service.advance_first_repair(user, complete=True)
    response = await CompanionService().get_first_repair(user)
    assert response.status == "COMPLETED"
    assert response.companion.current_stage == response.companion.total_materials == 1
    assert response.companion.next_stage_threshold == 15
    assert response.companion.materials_needed_for_next == 14
    assert response.companion.stages[0].threshold == 1
    assert (await CompanionState.get(user=user)).five_element_completion_counts == {}
    assert await CompanionStageLog.all().count() == 1
    assert await service.get_stage_up_pending(user) is None
    assert (
        await Challenge.all().count() == await PointLedger.all().count() == await DailyActionSummary.all().count() == 0
    )
    history = await service.get_material_history(user, "WOOD")
    assert history.count == history.welcome_gift_count == 1
    assert history.recent_history == []
    assert (await service.get_card_collection(user)).total_count == 0


async def test_exercise_after_first_repair_never_returns_to_stage_zero():
    user = await new_user()
    service = CompanionService()
    await service.advance_first_repair(user, complete=False)
    await service.advance_first_repair(user, complete=True)
    for i in range(1, 15):
        async with in_transaction():
            await CompanionRepository().increment_element(user.id, "WOOD")
        state = await service.get_dam_status(user)
        assert state.total_materials == i + 1
        assert state.current_stage == (1 if i < 14 else 2)
    assert await CompanionStageLog.filter(user=user).count() == 2


async def test_legacy_first_stage_threshold_remains_five():
    service = CompanionService()
    assert service._calculate_stage(1) == (0, 5)
    assert service._calculate_stage(5) == (1, 15)
    assert service._calculate_stage(120) == (5, None)


async def test_late_first_repair_preserves_prior_progress_and_logs_crossed_stage():
    user = await new_user()
    await CompanionState.create(user=user, five_element_completion_counts={"WOOD": 14})
    await CompanionStageLog.create(user=user, stage_number=1, total_materials_at_stage=5)
    service = CompanionService()
    await service.advance_first_repair(user, complete=False)
    result = await service.advance_first_repair(user, complete=True)
    assert result.companion.total_materials == 15
    assert result.companion.current_stage == 2
    assert (await CompanionStageLog.get(user=user, stage_number=1)).total_materials_at_stage == 5
    assert (await CompanionStageLog.get(user=user, stage_number=2)).total_materials_at_stage == 15


async def test_concurrent_retries_do_not_duplicate_gifts():
    user = await new_user()
    service = CompanionService()
    await asyncio.gather(*(service.advance_first_repair(user, complete=False) for _ in range(4)))
    await asyncio.gather(*(service.advance_first_repair(user, complete=True) for _ in range(4)))
    assert (await service.get_dam_status(user)).total_materials == 1
    assert await CompanionStageLog.all().count() == 1


async def test_signup_and_first_repair_eligibility_rollback_together():
    with patch.object(CompanionFirstRepair, "create", side_effect=RuntimeError("저장 실패")):
        with pytest.raises(RuntimeError):
            await new_user()
    assert await User.all().count() == 0


async def test_authenticated_api_contract_and_idempotency():
    user = await new_user()
    app = FastAPI()
    app.include_router(companion_router, prefix="/api/v1")
    app.dependency_overrides[get_request_user] = lambda: user
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        initial = await client.get("/api/v1/companion/first-repair")
        assert initial.json()["status"] == "ELIGIBLE"
        assert (await client.post("/api/v1/companion/first-repair/gift")).status_code == 200
        for _ in range(2):
            result = await client.post("/api/v1/companion/first-repair/complete")
            assert result.status_code == 200
            assert result.json()["companion"]["total_materials"] == 1
