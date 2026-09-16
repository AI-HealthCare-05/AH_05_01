"""틈새 운동 리포지토리. ChallengeRepository의 낙관적 잠금(try_transition) 패턴을 그대로 재사용."""

from datetime import date

from app.models.exercise_missions import (
    ExerciseMissionCatalog,
    ExerciseMissionSession,
    ExerciseMissionSessionState,
)


class ExerciseMissionRepository:
    def __init__(self):
        self._catalog_model = ExerciseMissionCatalog
        self._session_model = ExerciseMissionSession

    async def get_active_catalog(self) -> list[ExerciseMissionCatalog]:
        """⚠️ 문서 원칙: "단순히 WOOD/FIRE라서 모두 노출하지 않는다" - is_active=False나
        template_version.is_active=False인 건 빠져야 하므로 템플릿까지 같이 확인."""

        return (
            await self._catalog_model.filter(is_active=True, template_version__is_active=True)
            .order_by("display_order")
            .prefetch_related("template_version")
        )

    async def get_sessions_for_date(self, user_id, service_date: date) -> list[ExerciseMissionSession]:
        return await self._session_model.filter(user_id=user_id, service_date=service_date).order_by("created_at")

    async def get_owned_session(self, user_id, session_id) -> ExerciseMissionSession | None:
        return await self._session_model.get_or_none(id=session_id, user_id=user_id).prefetch_related(
            "catalog_entry__template_version"
        )

    async def create_session(
        self, *, user_id, service_date: date, catalog_entry: ExerciseMissionCatalog, template_snapshot: dict,
        target_duration_seconds: int | None, target_count: int | None, started_at, idempotency_key: str,
    ) -> ExerciseMissionSession:
        # ⚠️ 2026-09-16 버그 수정(QA F09) - idempotency_key를 이제 생성 시점에도 저장함.
        # unique 제약이 이미 모델에 있으니(원래는 완료 때만 쓰였음), 같은 키로 재시도가
        # 오면 서비스 레이어가 먼저 조회해서 기존 세션을 그대로 돌려주게 됨(아래
        # ExerciseMissionService.create_session 참고) - "생성 요청 성공 후 응답만
        # 유실된 경우" 재시도해도 새 세션이 중복 생성되지 않음.
        return await self._session_model.create(
            user_id=user_id,
            service_date=service_date,
            catalog_entry=catalog_entry,
            template_snapshot=template_snapshot,
            target_duration_seconds=target_duration_seconds,
            target_count=target_count,
            started_at=started_at,
            idempotency_key=idempotency_key,
        )

    async def try_transition(
        self, session: ExerciseMissionSession, *, from_states: list[str], to_state: str, extra_fields: dict | None = None
    ) -> bool:
        """ChallengeRepository.try_transition과 동일한 낙관적 잠금 방식.
        UPDATE ... WHERE id=? AND version=? AND state IN (...) — affected rows 0이면 실패."""

        current_version = session.version
        fields_to_update = {"state": to_state, "version": current_version + 1}
        if extra_fields:
            fields_to_update.update(extra_fields)
        updated_count = await self._session_model.filter(
            id=session.id, version=current_version, state__in=from_states
        ).update(**fields_to_update)
        return updated_count > 0

    async def get_session_by_idempotency_key(self, idempotency_key: str, user_id) -> ExerciseMissionSession | None:
        return await self._session_model.get_or_none(
            idempotency_key=idempotency_key, user_id=user_id
        ).prefetch_related("catalog_entry__template_version")

    async def count_awarded_today(self, user_id, service_date: date) -> int:
        """오늘 지급 확정된(reward_slot이 null이 아닌) 세션 수 - used 계산용."""

        return await self._session_model.filter(
            user_id=user_id, service_date=service_date, reward_slot__not_isnull=True
        ).count()

    async def has_incomplete_session_today(self, user_id, service_date: date, catalog_entry_id) -> bool:
        """⚠️ 문서: "같은 운동을 같은 날짜에 반복해 재료를 받지 않도록, 완료한 항목은 완료
        상태로 바꾼다. 다른 운동을 선택하게 한다." - 오늘 이 운동을 이미 완료했는지 확인."""

        return await self._session_model.filter(
            user_id=user_id,
            service_date=service_date,
            catalog_entry_id=catalog_entry_id,
            state=ExerciseMissionSessionState.COMPLETED,
        ).exists()

    async def get_records_between(self, user_id, from_date: date, to_date: date) -> list[ExerciseMissionSession]:
        """GET /exercise-mission-records — 하루·주간 집계용. 지급 확정된 것만."""

        return await self._session_model.filter(
            user_id=user_id, service_date__gte=from_date, service_date__lte=to_date, reward_slot__not_isnull=True,
        ).order_by("service_date")
