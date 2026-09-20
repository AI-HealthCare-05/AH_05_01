"""실제 저장된 주별 점수만 조회한다. 기록 조회로 과거 추론을 다시 실행하지 않는다."""

import hashlib
import json
from datetime import date, timedelta

from app.core import config
from app.core.config import Env
from app.core.time_utils import service_today
from app.dtos.personal_xai import PersonalXaiSnapshot
from app.dtos.weekly_xai import WeeklyPracticePoint, WeeklyReferencePoint, WeeklyXaiHistoryResponse
from app.repositories.consent_repository import ConsentRepository
from app.repositories.record_repository import RecordRepository
from app.repositories.weekly_xai_repository import WeeklyXaiRepository


def comparison_key(snapshot: PersonalXaiSnapshot) -> str:
    # 고정 릴리스 해시는 모델·보정 파일 전체를 묶는다. 입력 값과 날짜는 비교 기준이 아니다.
    keys = (
        "release_sha256",
        "model_version",
        "reference_version",
        "formula_version",
        "input_mapping_version",
        "display_policy_version",
    )
    policy = {key: getattr(snapshot, key) for key in keys}
    policy["domains"] = [
        {
            key: getattr(domain, key)
            for key in (
                "domain",
                "reference_group",
                "background_sha256",
                "explainer",
                "masker",
                "link",
                "shap_version",
                "unit",
            )
        }
        for domain in snapshot.domains
    ]
    return hashlib.sha256(json.dumps(policy, sort_keys=True).encode()).hexdigest()


def history_points(rows, today: date, count: int = 8) -> list[WeeklyReferencePoint]:
    monday = today - timedelta(days=today.weekday())
    by_week = {row.week_start: row for row in rows}
    points = []
    for index in range(count - 1, -1, -1):
        start = monday - timedelta(weeks=index)
        point = WeeklyReferencePoint(week_start=start, week_end=start + timedelta(days=6))
        row = by_week.get(start)
        if row is not None:
            try:
                snapshot = PersonalXaiSnapshot.model_validate(row.snapshot)
                observed = date.fromisoformat(snapshot.reference_date)
                if snapshot.status != "ready" or not start <= observed <= min(point.week_end, today):
                    raise ValueError("주간 기록 날짜 불일치")
                point = point.model_copy(
                    update={
                        "status": "recorded",
                        "observed_on": observed,
                        "diabetes": snapshot.domains[0].output_value,
                        "hypertension": snapshot.domains[1].output_value,
                        "comparison_key": comparison_key(snapshot),
                    }
                )
            except (ValueError, TypeError, KeyError):
                point = point.model_copy(update={"status": "incompatible"})
        points.append(point)
    return points


class WeeklyXaiService:
    def __init__(self):
        self.consent_repo = ConsentRepository()
        self.history_repo = WeeklyXaiRepository()
        self.record_repo = RecordRepository()

    async def get_history(self, user) -> WeeklyXaiHistoryResponse:
        if config.ENV == Env.PROD:
            return WeeklyXaiHistoryResponse(status="unavailable", reason="release_review_required")
        consent = await self.consent_repo.get_latest_by_purpose(user.id, "HEALTH_REFERENCE_ANALYSIS")
        if consent is None or consent.status != "AGREED":
            return WeeklyXaiHistoryResponse(status="unavailable", reason="consent_required")
        today = service_today(user.id)
        monday = today - timedelta(days=today.weekday())
        start = monday - timedelta(weeks=7)
        rows = await self.history_repo.list_since(user.id, start, monday)
        return WeeklyXaiHistoryResponse(
            status="ready", points=history_points(rows, today), practice=await self.practice(user.id, monday, today)
        )

    async def practice(self, user_id: int, monday: date, today: date):
        start = monday - timedelta(weeks=1)
        cards = await self.record_repo.get_card_sets_in_range(user_id, start, today)
        notes = await self.record_repo.get_notes_in_range(user_id, start, today)
        completed = {
            card.service_date
            for card in cards
            if card.selection and card.selection.challenge and card.selection.challenge.state == "COMPLETED"
        }
        rest = {day for day, note in notes.items() if note.is_rest_day}
        recorded = {card.service_date for card in cards} | set(notes)
        return [
            WeeklyPracticePoint(
                week_start=week,
                recorded=any(week <= day <= week + timedelta(days=6) for day in recorded),
                completed_days=sum(week <= day <= week + timedelta(days=6) for day in completed - rest),
                rest_days=sum(week <= day <= week + timedelta(days=6) for day in rest),
                elapsed_days=min(7, (today - week).days + 1),
            )
            for week in (start, monday)
        ]
