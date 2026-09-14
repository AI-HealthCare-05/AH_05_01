"""틈새 운동 서비스. TMtn_UI_V17 FEATURE_RULES.md §5의 규칙을 그대로 구현:
- 오늘의 카드 완료 후에만 이용
- 하루 최대 2회, 완료당 재료 1개
- 시작/일시정지는 보상 횟수를 안 씀 - 서버가 완료·지급을 확정할 때 한 번만 씀
- 같은 날 같은 운동 반복 완료 방지
- 카드 실천일·카드첩 장수에 안 더함(Challenge와 완전히 분리된 테이블이라 자동으로 지켜짐)
- 동시 시작/중복 완료는 서버가 제한(멱등키 + UNIQUE(user, service_date, reward_slot))
"""

from datetime import datetime

from fastapi import HTTPException, status
from tortoise.exceptions import IntegrityError
from tortoise.transactions import in_transaction

from app.core import config
from app.core.time_utils import service_today
from app.dtos.companion import MATERIAL_INFO
from app.dtos.exercise_missions import (
    CompleteExerciseMissionSessionResponse,
    ExerciseMissionOption,
    ExerciseMissionRecordItem,
    ExerciseMissionRecordsResponse,
    ExerciseMissionSessionResponse,
    ExerciseMissionsTodayResponse,
)
from app.models.challenges import Challenge, ChallengeState, duration_seconds_from_target
from app.models.exercise_missions import ExerciseMissionSessionState
from app.models.users import User
from app.repositories.card_repository import CardRepository
from app.repositories.companion_repository import CompanionRepository
from app.repositories.exercise_mission_repository import ExerciseMissionRepository

DAILY_LIMIT = 2  # ⚠️ 문서: "하루 추가 보상은 최대 2회다."
NO_VALIDATION_EXEC_TYPES = {"CHECK"}


def _target_duration_seconds(session) -> int | None:
    """challenge_service._target_duration_seconds와 동일한 로직 - ExerciseMissionSession도
    같은 필드명(target_duration_seconds/exec_type)을 쓰도록 설계해서 그대로 재사용 가능."""

    return session.target_duration_seconds


def _effective_duration_seconds(session) -> int:
    """challenge_service._effective_duration_seconds와 동일 - ACTIVE면 지금까지 쌓인 것 +
    (지금 - started_at)까지 더해서 실제 경과 시간을 계산."""

    base = session.accumulated_duration_seconds
    if session.state == ExerciseMissionSessionState.ACTIVE and session.started_at is not None:
        now = datetime.now(config.TIMEZONE)
        elapsed = int((now - session.started_at).total_seconds())
        base += max(elapsed, 0)
    target = _target_duration_seconds(session)
    if target is not None:
        base = min(base, target)  # 목표치 초과로 튀지 않게 상한
    return base


def _is_goal_achieved(session) -> bool:
    template_snapshot = session.template_snapshot
    exec_type = template_snapshot.get("exec_type")
    if exec_type in NO_VALIDATION_EXEC_TYPES:
        return True
    target_seconds = _target_duration_seconds(session)
    if target_seconds is not None:
        return _effective_duration_seconds(session) >= target_seconds
    if session.target_count is not None:
        return session.accumulated_count >= session.target_count
    return True


class ExerciseMissionService:
    def __init__(self):
        self.repo = ExerciseMissionRepository()
        self.card_repo = CardRepository()
        self.companion_repo = CompanionRepository()

    async def _is_card_completed_today(self, user: User, service_date) -> bool:
        card_set = await self.card_repo.get_set_by_date(user.id, service_date)
        if card_set is None or card_set.selection is None:
            return False
        challenge = await Challenge.get_or_none(selection_id=card_set.selection.id)
        return challenge is not None and challenge.state == ChallengeState.COMPLETED

    async def get_today(self, user: User) -> ExerciseMissionsTodayResponse:
        today = service_today(user.id)
        card_completed = await self._is_card_completed_today(user, today)
        sessions_today = await self.repo.get_sessions_for_date(user.id, today)
        used = sum(1 for s in sessions_today if s.reward_slot is not None)
        remaining = max(DAILY_LIMIT - used, 0)

        catalog = await self.repo.get_active_catalog()
        options = []
        for entry in catalog:
            template = entry.template_version
            already = await self.repo.has_incomplete_session_today(user.id, today, entry.id)
            options.append(
                ExerciseMissionOption(
                    catalog_entry_id=entry.id,
                    title=template.title,
                    guide_text=template.guide_text,
                    exec_type=template.exec_type,
                    target_value=template.target_value,
                    unit=template.unit,
                    five_element=template.five_element,
                    material_name=MATERIAL_INFO[template.five_element]["material_name"],
                    already_completed_today=already,
                )
            )
        return ExerciseMissionsTodayResponse(
            card_completed=card_completed, used=used, limit=DAILY_LIMIT, remaining=remaining, options=options
        )

    async def create_session(self, user: User, catalog_entry_id, idempotency_key: str) -> ExerciseMissionSessionResponse:
        today = service_today(user.id)

        if not await self._is_card_completed_today(user, today):
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT, detail="오늘의 카드를 먼저 완료해야 틈새 운동을 시작할 수 있어요."
            )

        sessions_today = await self.repo.get_sessions_for_date(user.id, today)
        used = sum(1 for s in sessions_today if s.reward_slot is not None)
        if used >= DAILY_LIMIT:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="오늘 받을 수 있는 틈새 운동 보상을 다 받았어요.")
        if any(s.state == ExerciseMissionSessionState.ACTIVE for s in sessions_today):
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 진행 중인 틈새 운동이 있어요.")

        catalog = await self.repo.get_active_catalog()
        entry = next((c for c in catalog if c.id == catalog_entry_id), None)
        if entry is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="선택할 수 없는 운동이에요.")

        if await self.repo.has_incomplete_session_today(user.id, today, entry.id):
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="오늘 이미 완료한 운동이에요. 다른 운동을 골라주세요.")

        template = entry.template_version
        snapshot = {
            "title": template.title,
            "guide_text": template.guide_text,
            "exec_type": template.exec_type,
            "target_value": template.target_value,
            "unit": template.unit,
            "five_element": template.five_element,
        }
        target_duration = None
        target_count = None
        if template.exec_type in ("TIMER", "SENSOR_WALKING_DURATION", "SENSOR_RUNNING_DURATION"):
            target_duration = duration_seconds_from_target(template.target_value, template.unit)
        elif template.exec_type in (
            "SENSOR_STEPS", "SENSOR_FLOORS_CLIMBED", "SENSOR_STEPS_IN_PLACE", "SENSOR_RUNNING_DISTANCE",
        ):
            target_count = template.target_value

        now = datetime.now(config.TIMEZONE)
        try:
            session = await self.repo.create_session(
                user_id=user.id, service_date=today, catalog_entry=entry, template_snapshot=snapshot,
                target_duration_seconds=target_duration, target_count=target_count, started_at=now,
            )
        except IntegrityError as exc:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 진행 중인 틈새 운동이 있어요.") from exc

        return self._to_session_response(session, snapshot)

    async def _get_owned_active_session(self, user: User, session_id):
        session = await self.repo.get_owned_session(user.id, session_id)
        if session is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="틈새 운동을 찾을 수 없어요.")
        return session

    async def patch_session(self, user: User, session_id, action: str, accumulated_count: int | None) -> ExerciseMissionSessionResponse:
        session = await self._get_owned_active_session(user, session_id)
        now = datetime.now(config.TIMEZONE)

        if action == "pause":
            if session.state != ExerciseMissionSessionState.ACTIVE:
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="진행 중이 아니에요.")
            elapsed = _effective_duration_seconds(session)
            extra = {"accumulated_duration_seconds": elapsed, "last_paused_at": now, "started_at": None}
            if accumulated_count is not None:
                extra["accumulated_count"] = accumulated_count
            ok = await self.repo.try_transition(
                session, from_states=[ExerciseMissionSessionState.ACTIVE],
                to_state=ExerciseMissionSessionState.PAUSED, extra_fields=extra,
            )
        elif action == "resume":
            if session.state != ExerciseMissionSessionState.PAUSED:
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="일시정지 상태가 아니에요.")
            extra = {"started_at": now}
            ok = await self.repo.try_transition(
                session, from_states=[ExerciseMissionSessionState.PAUSED],
                to_state=ExerciseMissionSessionState.ACTIVE, extra_fields=extra,
            )
        else:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail="알 수 없는 action이에요.")

        if not ok:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="다른 요청이 먼저 처리됐어요. 다시 시도해 주세요.")

        session = await self.repo.get_owned_session(user.id, session_id)
        return self._to_session_response(session, session.template_snapshot)

    async def complete_session(
        self, user: User, session_id, idempotency_key: str, manual_check: bool, accumulated_count: int | None,
    ) -> CompleteExerciseMissionSessionResponse:
        existing = await self.repo.get_session_by_idempotency_key(idempotency_key, user.id)
        if existing is not None:
            if existing.id != session_id:
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Idempotency-Key가 다른 세션에 이미 사용됐어요.")
            return await self._build_complete_response(user, existing)

        session = await self._get_owned_active_session(user, session_id)
        if accumulated_count is not None:
            session.accumulated_count = accumulated_count

        if not manual_check and not _is_goal_achieved(session):
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="아직 목표에 도달하지 못했어요.")

        today = session.service_date
        five_element = session.template_snapshot["five_element"]
        now = datetime.now(config.TIMEZONE)

        try:
            async with in_transaction():
                # ⚠️ reward_slot 배정: 오늘 이미 지급된 개수를 세서 다음 슬롯(1 또는 2) 결정.
                # UNIQUE(user, service_date, reward_slot)가 동시 요청 경합을 최종 방어.
                sessions_today = await self.repo.get_sessions_for_date(user.id, today)
                used = sum(1 for s in sessions_today if s.reward_slot is not None)
                if used >= DAILY_LIMIT:
                    raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="오늘 받을 수 있는 틈새 운동 보상을 다 받았어요.")
                next_slot = used + 1

                extra = {
                    "accumulated_count": session.accumulated_count,
                    "completed_at": now, "awarded_at": now,
                    "reward_slot": next_slot, "idempotency_key": idempotency_key,
                }
                transitioned = await self.repo.try_transition(
                    session,
                    from_states=[ExerciseMissionSessionState.ACTIVE, ExerciseMissionSessionState.PAUSED],
                    to_state=ExerciseMissionSessionState.COMPLETED, extra_fields=extra,
                )
                if not transitioned:
                    raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 완료되었거나 완료할 수 없는 상태예요.")
                await self.companion_repo.increment_element(user.id, five_element)
        except HTTPException:
            raise
        except IntegrityError as exc:
            existing = await self.repo.get_session_by_idempotency_key(idempotency_key, user.id)
            if existing is not None:
                return await self._build_complete_response(user, existing)
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="중복 요청이거나 이미 오늘 보상을 다 받았어요.") from exc

        session = await self.repo.get_owned_session(user.id, session_id)
        return await self._build_complete_response(user, session)

    async def _build_complete_response(self, user: User, session) -> CompleteExerciseMissionSessionResponse:
        five_element = session.template_snapshot["five_element"]
        material = MATERIAL_INFO[five_element]
        sessions_today = await self.repo.get_sessions_for_date(user.id, session.service_date)
        used = sum(1 for s in sessions_today if s.reward_slot is not None)
        remaining = max(DAILY_LIMIT - used, 0)
        return CompleteExerciseMissionSessionResponse(
            session_id=session.id,
            reward_slot=session.reward_slot,
            material_name=material["material_name"],
            used=used,
            remaining=remaining,
            awarded_material={"element": five_element, "material_name": material["material_name"], "count": 1},
            authoritative_counts={"used": used, "remaining": remaining},
        )

    async def cancel_session(self, user: User, session_id) -> None:
        """⚠️ 문서: "추가 운동을 안 하거나 중단해도 오늘의 카드 기록·쉼·연속 기록에 불이익은
        없다." - 지급·카드 기록은 그대로 두고 세션만 CANCELLED로 바꿈."""

        session = await self._get_owned_active_session(user, session_id)
        ok = await self.repo.try_transition(
            session,
            from_states=[ExerciseMissionSessionState.ACTIVE, ExerciseMissionSessionState.PAUSED],
            to_state=ExerciseMissionSessionState.CANCELLED,
        )
        if not ok:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 완료되었거나 취소할 수 없는 상태예요.")

    async def get_records(self, user: User, from_date, to_date) -> ExerciseMissionRecordsResponse:
        sessions = await self.repo.get_records_between(user.id, from_date, to_date)
        items = [
            ExerciseMissionRecordItem(
                service_date=s.service_date,
                title=s.template_snapshot["title"],
                five_element=s.template_snapshot["five_element"],
                material_name=MATERIAL_INFO[s.template_snapshot["five_element"]]["material_name"],
                reward_slot=s.reward_slot,
                completed_at=s.completed_at,
            )
            for s in sessions
        ]
        return ExerciseMissionRecordsResponse(records=items)

    def _to_session_response(self, session, snapshot: dict) -> ExerciseMissionSessionResponse:
        return ExerciseMissionSessionResponse(
            id=session.id,
            state=session.state,
            exec_type=snapshot["exec_type"],
            title=snapshot["title"],
            five_element=snapshot["five_element"],
            material_name=MATERIAL_INFO[snapshot["five_element"]]["material_name"],
            target_duration_seconds=session.target_duration_seconds,
            target_count=session.target_count,
            accumulated_duration_seconds=_effective_duration_seconds(session),
            accumulated_count=session.accumulated_count,
            started_at=session.started_at,
        )
