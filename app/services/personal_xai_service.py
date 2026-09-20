"""저장된 건강 입력으로 사용자별 계산을 요청하고 준비된 결과를 주간 이력으로 보관한다."""

import asyncio
import hashlib
import hmac
import json
import logging
import time
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

import httpx
from tortoise.exceptions import BaseORMException

from app.core import config
from app.core.config import Env
from app.core.time_utils import service_today
from app.dtos.personal_xai import ActivityComparison, PersonalXaiResponse, PersonalXaiSnapshot
from app.repositories.consent_repository import ConsentRepository
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.repositories.health_repository import HealthInputRepository
from app.repositories.weekly_xai_repository import WeeklyXaiRepository
from app.services.journal_editorial_service import age_bounds
from model_service.activity_reference import build_comparison, group_key

logger = logging.getLogger(__name__)


def survey_timestamp(habit):
    recorded_at = getattr(habit, "recorded_at", None)
    if isinstance(recorded_at, datetime) and recorded_at.utcoffset() is not None:
        return recorded_at
    return None


def immediate_activity(user, habit, request, today):
    """Fast survey comparison; ambiguous birthday bands wait for model normalization."""
    ages = age_bounds(user, today)
    sex = {"male": 1, "female": 2}.get(request["sex"])
    try:
        if ages is None or group_key(ages[0], sex) != group_key(ages[1], sex):
            return None
        features = {
            "age_years": ages[0],
            "sex_code": sex,
            "leisure_aerobic_moderate_equivalent_min_week": request["aerobicModerateMinutes"]
            + 2 * request["aerobicVigorousMinutes"],
            "strength_days_week": request["strengthWeeklyCount"],
        }
        result = build_comparison(
            features, input_revision=request["inputRevision"], reference_date=request["referenceDate"]
        )
        return ActivityComparison.model_validate({**result, "survey_recorded_at": survey_timestamp(habit)})
    except (ValueError, TypeError, KeyError, OSError):
        return None


def model_request(user, health, habit, today):
    values = health.input_values or {}
    request = {
        "birthYear": user.birth_year,
        "birthMonth": user.birth_month,
        "sex": str(user.gender).lower(),
        "pregnancyStatus": "not_applicable"
        if user.gender == "MALE"
        else "nonpregnant"
        if user.is_pregnant is False
        else "pregnant"
        if user.is_pregnant is True
        else "unknown",
        "heightCm": values.get("height_cm"),
        "weightKg": values.get("weight_kg"),
        "strengthWeeklyCount": habit.strength_weekly_count,
        "strengthFrequencyUnit": getattr(habit, "strength_frequency_unit", None),
        "strengthIntensity": str(habit.strength_intensity).lower() if habit.strength_intensity else None,
        "aerobicLowMinutes": habit.aerobic_low_minutes,
        "aerobicModerateMinutes": habit.aerobic_moderate_minutes,
        "aerobicVigorousMinutes": habit.aerobic_high_minutes,
        "referenceDate": today.isoformat(),
    }
    # Includes source snapshot IDs and user identity; raw inputs cannot be recovered by guessing a plain hash.
    binding = {"request": request, "user": user.id, "health": str(health.id), "habit": str(habit.id)}
    request["inputRevision"] = hmac.new(
        config.SECRET_KEY.encode(),
        json.dumps(binding, sort_keys=True, separators=(",", ":"), allow_nan=False).encode(),
        hashlib.sha256,
    ).hexdigest()
    return request


@dataclass
class PersonalJob:
    created: float
    task: asyncio.Task


class PersonalJobs:
    """Bounded, short-lived, process-local cache. Restart loses jobs, never user records."""

    def __init__(self):
        self.jobs: dict[tuple[int, str], PersonalJob] = {}
        self.semaphore = asyncio.Semaphore(1)

    def forget(self, user_id):
        for key in [key for key in self.jobs if key[0] == user_id]:
            self.jobs.pop(key).task.cancel()

    async def calculate(self, request):
        try:
            async with self.semaphore:
                requested_at = datetime.now(UTC)
                async with httpx.AsyncClient(timeout=180.0, trust_env=False) as client:
                    result = await client.post(
                        config.TUNTUN_XAI_URL.rstrip("/") + "/personal-score",
                        json=request,
                        headers={"Authorization": "Bearer " + config.TUNTUN_XAI_TOKEN},
                    )
                result.raise_for_status()
                snapshot = PersonalXaiSnapshot.model_validate(result.json())
                if (
                    not requested_at - timedelta(seconds=5)
                    <= snapshot.computed_at
                    <= datetime.now(UTC) + timedelta(seconds=5)
                ):
                    raise ValueError("Snapshot timestamp is outside this calculation")
                if (
                    snapshot.input_revision != request["inputRevision"]
                    or snapshot.reference_date != request["referenceDate"]
                ):
                    raise ValueError("Snapshot does not belong to the current input")
                return PersonalXaiResponse(
                    status=snapshot.status,
                    reason=snapshot.reason,
                    snapshot=snapshot if snapshot.status == "ready" else None,
                )
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            logger.warning(
                "Personal XAI calculation unavailable (%s, status=%s)",
                type(exc).__name__,
                exc.response.status_code if isinstance(exc, httpx.HTTPStatusError) else "none",
            )
            return PersonalXaiResponse(status="unavailable", reason="calculation_failed")

    def poll(self, user_id, request):
        now = time.monotonic()
        for key, job in list(self.jobs.items()):
            if now - job.created > 600:
                self.jobs.pop(key).task.cancel()
        key = (user_id, request["inputRevision"])
        if key not in self.jobs:
            if len(self.jobs) >= 128 or sum(not job.task.done() for job in self.jobs.values()) >= 8:
                return PersonalXaiResponse(status="unavailable", reason="busy")
            self.jobs[key] = PersonalJob(now, asyncio.create_task(self.calculate(request)))
        task = self.jobs[key].task
        if task.done() and not task.cancelled():
            result = task.result()
            if result.status == "unavailable":
                del self.jobs[key]  # A deliberate retry can recover from transient worker failure.
            return result
        return PersonalXaiResponse(status="pending", retry_after_seconds=3)


personal_jobs = PersonalJobs()


class PersonalXaiService:
    def __init__(self):
        self.health_repo = HealthInputRepository()
        self.habit_repo = ExerciseHabitRepository()
        self.consent_repo = ConsentRepository()
        self.history_repo = WeeklyXaiRepository()

    async def get_personal(self, user):
        if config.ENV == Env.PROD:
            return PersonalXaiResponse(status="unavailable", reason="release_review_required")
        if not config.TUNTUN_XAI_URL or not config.TUNTUN_XAI_TOKEN:
            return PersonalXaiResponse(status="unavailable", reason="not_configured")
        consent = await self.consent_repo.get_latest_by_purpose(user.id, "HEALTH_REFERENCE_ANALYSIS")
        if consent is None or consent.status != "AGREED":
            personal_jobs.forget(user.id)
            return PersonalXaiResponse(status="unavailable", reason="consent_required")
        health, habit = await asyncio.gather(self.health_repo.get_latest(user.id), self.habit_repo.get_latest(user.id))
        if (
            health is None
            or habit is None
            or user.birth_year is None
            or user.birth_month is None
            or user.gender is None
        ):
            return PersonalXaiResponse(status="unavailable", reason="input_required")
        if habit.strength_weekly_count > 0 and getattr(habit, "strength_frequency_unit", None) != "days":
            return PersonalXaiResponse(status="unavailable", reason="strength_days_unconfirmed")
        try:
            today = service_today(user.id)
            request = model_request(user, health, habit, today)
        except (ValueError, TypeError):
            return PersonalXaiResponse(status="unavailable", reason="input_invalid")
        response = personal_jobs.poll(user.id, request)
        activity = (
            immediate_activity(user, habit, request, today)
            if response.status in ("pending", "ready") or response.reason in ("busy", "calculation_failed")
            else None
        )
        if response.snapshot is not None:
            # Once ready, the worker's actual normalization is authoritative.
            activity = response.snapshot.activity_comparison
            if activity is not None:
                activity = activity.model_copy(update={"survey_recorded_at": survey_timestamp(habit)})
                response = response.model_copy(
                    update={"snapshot": response.snapshot.model_copy(update={"activity_comparison": activity})}
                )
            await self._save_history(user.id, response.snapshot)
        return PersonalXaiResponse.model_validate({**response.model_dump(), "activity_comparison": activity})

    async def _save_history(self, user_id, snapshot):
        try:
            await self.history_repo.save(user_id, snapshot)
        except BaseORMException:
            # 마이그레이션 전 서버라도 최신 설명을 가리지 않는다. 이력 API는 실패를 명시한다.
            logger.warning("주간 XAI 기록 저장 실패. DB 마이그레이션과 연결 상태를 확인하세요.")
