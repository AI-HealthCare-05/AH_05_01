"""미션 실천 점수 + 틈튼지수 종합(생활습관/전체) 서비스.

⚠️ 2026-09-15 v2 - 강호님(모델) 검토 회신 + composition_formula.py 반영. v1과 달라진
핵심:
  - 종합 조합(생활습관/전체 지수)을 여기서 실제로 계산함(v1은 자리만 남기고 null)
  - 건강 영역(physical/diabetes/hypertension)은 TuntunScorePeerService.fetch_bridge_result()
    를 재사용해서 components[].peerPercentile(백분위 변환 전 raw 아님 - 건강 방향 정렬된
    반올림 전 백분위)을 가져옴
  - 초기 습관 점수는 InitialHabitSnapshot(가입 시점 고정, 최초 1회만 생성)에서 가져옴 -
    세부 배점 정책이 아직 "검토안"이라 지금은 항상 score=None, policy_status='pending'
  - 원장 상태(loaded/confirmed_empty_new/unavailable)를 "시간 필터링 이전의 완료
    레코드 존재 여부"로 판정 - 시간 미수집 이벤트만 있어도 "loaded"로 봄(빈 원장 아님)

⚠️ 아직 반영 안 한 것(명확히 구분):
  - 쉼(rest) 연동 - "오늘은 쉬어가기"의 정확한 의미(오늘의 카드만 건너뛴 것인지, 그
    날 운동을 안 했다는 확인인지)를 먼저 확인해야 함(강호님 요청). 지금은 전부 unknown.
  - CHECK형 시간 미수집 additional의 "이유 표시" - 지금은 그냥 집계에서 제외만 하고
    있음(그 날 mission 점수는 안 깨지게는 이미 처리돼 있음). 제외 사유를 응답에
    구조적으로 남기는 건 다음 단계.
  - 초기 습관 세부 배점 산식 - 검토안 단계라 이번에 구현 안 함.
"""

from datetime import date, timedelta

from app.core.time_utils import service_today
from app.models.assessments import InitialHabitSnapshot
from app.models.challenges import Challenge, ChallengeState
from app.models.exercise_missions import ExerciseMissionSession, ExerciseMissionSessionState
from app.models.users import User
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.repositories.health_repository import HealthInputRepository
from app.services.composition_formula import InitialHabitSnapshot as ComposeInitialSnapshot
from app.services.composition_formula import compose
from app.services.practice_formula import policy as practice_policy
from app.services.practice_formula import trajectory
from app.services.tuntun_score_peer_service import TuntunScorePeerService

MAX_REVIEW_DAYS = 730
HEALTH_KEYS = ("physical", "diabetes", "hypertension")


class PracticeScoreService:
    def __init__(self):
        self.health_repo = HealthInputRepository()
        self.exercise_repo = ExerciseHabitRepository()
        self.peer_service = TuntunScorePeerService()

    async def _daily_events(self, user: User, start: date, end: date) -> dict[date, list[dict]]:
        by_day: dict[date, list[dict]] = {d: [] for d in _date_range(start, end)}

        challenges = await Challenge.filter(
            selection__card_set__user_id=user.id,
            state=ChallengeState.COMPLETED,
            selection__card_set__service_date__gte=start,
            selection__card_set__service_date__lte=end,
        ).prefetch_related("selection__card_set")
        for c in challenges:
            d = c.selection.card_set.service_date
            by_day.setdefault(d, []).append(dict(sessionId=str(c.id), kind="mission", minutes=None))

        sessions = await ExerciseMissionSession.filter(
            user_id=user.id,
            state=ExerciseMissionSessionState.COMPLETED,
            service_date__gte=start,
            service_date__lte=end,
        )
        for s in sessions:
            minutes = (s.accumulated_duration_seconds or 0) / 60
            # ⚠️ 강호님 요청: "시간 없는 additional은 시간을 만들어 넣지 않는다. 기록은
            # 보존하고 가산은 보류". 지금은 daily_units() 입력에서만 제외하고(mission
            # 점수가 깨지지 않게), 완료 기록 자체(ExerciseMissionSession row)는 그대로
            # DB에 남아있음 - "보존"은 이미 되고 있음. 제외 사유를 응답에 구조적으로
            # 남기는 건 다음 단계(위 docstring 참고).
            if minutes <= 0:
                continue
            by_day.setdefault(s.service_date, []).append(
                dict(sessionId=str(s.id), kind="additional", minutes=minutes)
            )

        return by_day

    async def _has_any_completion(self, user: User, start: date, end: date) -> bool:
        """원장 상태(loaded/confirmed_empty_new) 판정용 - 시간 필터링 이전의 완료
        레코드 존재 여부. _daily_events()가 시간 미수집 이벤트를 골라내는 것과는
        별개로, "완료 기록 자체가 있는지"만 봄 - 시간 미수집 기록만 있어도 True."""

        has_challenge = await Challenge.filter(
            selection__card_set__user_id=user.id,
            state=ChallengeState.COMPLETED,
            selection__card_set__service_date__gte=start,
            selection__card_set__service_date__lte=end,
        ).exists()
        if has_challenge:
            return True
        return await ExerciseMissionSession.filter(
            user_id=user.id,
            state=ExerciseMissionSessionState.COMPLETED,
            service_date__gte=start,
            service_date__lte=end,
        ).exists()

    async def _get_or_create_initial_snapshot(self, user: User) -> InitialHabitSnapshot:
        """가입 시점 신체정보+운동습관 스냅샷을 기준으로 최초 1회만 생성 - 이후 절대
        덮어쓰지 않음(user OneToOneField가 DB 레벨에서 재생성을 막음)."""

        existing = await InitialHabitSnapshot.get_or_none(user_id=user.id)
        if existing is not None:
            return existing

        earliest_health = await self.health_repo.get_earliest(user.id)
        earliest_habit = await self.exercise_repo.get_earliest(user.id)
        input_revision = (
            f"{earliest_health.id if earliest_health else 'none'}:"
            f"{earliest_habit.id if earliest_habit else 'none'}"
        )
        return await InitialHabitSnapshot.create(
            user_id=user.id,
            input_revision=input_revision,
            formula_version="pending-selection",
            policy_status="pending",
            score=None,
        )

    async def get_practice_score(self, user: User) -> dict:
        today = service_today(user.id)
        start = user.created_at.date() if hasattr(user, "created_at") and user.created_at else today
        start = max(start, today - timedelta(days=MAX_REVIEW_DAYS))

        try:
            has_any = await self._has_any_completion(user, start, today)
        except Exception:  # noqa: BLE001 - DB 조회 자체 실패는 ledger_state=unavailable로
            has_any = None

        rows = None
        if has_any is None:
            ledger_state = "unavailable"
            practice_score = None
            ledger_revision = None
        elif not has_any:
            ledger_state = "confirmed_empty_new"
            practice_score = None  # compose()가 0.0으로 취급
            ledger_revision = f"empty:{start.isoformat()}:{today.isoformat()}"
        else:
            ledger_state = "loaded"
            by_day = await self._daily_events(user, start, today)
            days = []
            for d in _date_range(start, today):
                events = by_day.get(d, [])
                days.append(dict(status="active" if events else "unknown", events=events))
            rows = trajectory(days)
            latest = rows[-1]
            practice_score = latest["practiceScore"]
            ledger_revision = f"{start.isoformat()}:{today.isoformat()}:{latest['cumulativeUnits']}"

        initial = await self._get_or_create_initial_snapshot(user)
        compose_initial = ComposeInitialSnapshot(
            snapshot_id=str(initial.id),
            input_revision=initial.input_revision,
            formula_version=initial.formula_version,
            policy_status=initial.policy_status,
            score=initial.score,
        )

        health_input_revision = "unavailable"
        health_components = []
        bridge_error = None
        try:
            bridge_result, health_input_revision = await self.peer_service.fetch_bridge_result(user)
            for c in bridge_result.get("components", []):
                if c.get("componentKey") not in HEALTH_KEYS:
                    continue
                health_components.append(c)
        except Exception as e:  # noqa: BLE001 - 건강 영역을 못 가져와도 실천 점수는 보여줘야 함
            bridge_error = type(e).__name__

        p = practice_policy()

        if len(health_components) != 3 or bridge_error:
            # ⚠️ compose()는 건강 영역 3개가 정확히 다 있어야만 실행 가능(안 그러면
            # THREE_HEALTH_COMPONENTS_REQUIRED로 터짐). 못 가져온 경우엔 종합 계산을
            # 아예 시도하지 않고, 실천 점수만이라도 정확히 반환함.
            return dict(
                as_of=today.isoformat(),
                daily_units=(rows[-1]["dailyUnits"] if ledger_state == "loaded" else 0.0),
                cumulative_units=(rows[-1]["cumulativeUnits"] if ledger_state == "loaded" else 0.0),
                practice_score=0.0 if ledger_state == "confirmed_empty_new" else (practice_score or 0.0),
                confirmed_rest_run=(rows[-1]["confirmedRestRun"] if ledger_state == "loaded" else 0),
                unknown_run=(rows[-1]["unknownRun"] if ledger_state == "loaded" else 0),
                freshness=(rows[-1]["freshness"] if ledger_state == "loaded" else "current_or_recent"),
                policy_version=p["version"],
                lifestyle_score=None,
                composite_score=None,
                composite_blocked_reason="HEALTH_COMPONENTS_UNAVAILABLE",
            )

        result = compose(
            health_components=health_components,
            initial=compose_initial,
            practice_score=practice_score,
            practice_version=p["version"],
            ledger_state=ledger_state,
            ledger_revision=ledger_revision,
            health_input_revision=health_input_revision,
            as_of=today.isoformat(),
        )

        return dict(
            as_of=today.isoformat(),
            daily_units=(rows[-1]["dailyUnits"] if ledger_state == "loaded" else 0.0),
            cumulative_units=(rows[-1]["cumulativeUnits"] if ledger_state == "loaded" else 0.0),
            practice_score=result["practice_score"] or 0.0,
            confirmed_rest_run=(rows[-1]["confirmedRestRun"] if ledger_state == "loaded" else 0),
            unknown_run=(rows[-1]["unknownRun"] if ledger_state == "loaded" else 0),
            freshness=(rows[-1]["freshness"] if ledger_state == "loaded" else "current_or_recent"),
            policy_version=p["version"],
            lifestyle_score=result["lifestyle_score"],
            composite_score=result["display_score"],
            composite_blocked_reason=(
                ",".join(result["composite_blocked_reason"]) if result["composite_blocked_reason"] else None
            ),
        )


def _date_range(start: date, end: date) -> list[date]:
    return [start + timedelta(days=i) for i in range((end - start).days + 1)]
