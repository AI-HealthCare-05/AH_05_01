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
from app.repositories.record_repository import RecordRepository
from app.services.composition_formula import InitialHabitSnapshot as ComposeInitialSnapshot
from app.services.composition_formula import compose
from app.services.initial_habit_formula import DEFINITION as INITIAL_HABIT_DEFINITION
from app.services.initial_habit_formula import calculate as calculate_initial_habit
from app.services.practice_formula import policy as practice_policy
from app.services.practice_formula import trajectory
from app.services.tuntun_score_peer_service import TuntunScorePeerService

MAX_REVIEW_DAYS = 730
HEALTH_KEYS = ("physical", "diabetes", "hypertension")
_STRENGTH_INTENSITY_MAP = {"LIGHT": "light", "MODERATE": "moderate", "HARD": "hard"}
# ⚠️ 2026-09-16 모듈 상수로 분리 - app/scripts/backfill_initial_habit_scores.py(기존
# pending 사용자 일괄 전환)가 여기와 똑같은 판정 기준을 써야 해서, 매직넘버 중복을
# 피하려고 여기서만 정의하고 스크립트가 import해서 씀.
SIGNUP_CONFIRMATION_WINDOW_SECONDS = 3600


def _calculate_initial_habit_from_snapshot(habit, input_revision: str) -> dict:
    """⚠️ 2026-09-16 추가 - 강호님 "초기습관_산식과_모델표시_확정_v1" 반영. 아직 실제로
    호출되는 곳 없음(_get_or_create_initial_snapshot()이 policy_status='pending'만
    저장하는 중) - 온보딩이 "가입 당시 완성 설문"을 식별할 수 있는 방법이 확인되면
    바로 연결할 수 있게 계산 로직만 미리 준비해둠.

    ⚠️ strength_days=habit.strength_weekly_count 그대로 씀 - 앱의 "주 N회"가 실제로는
    운동한 일수라는 건 이미 확인 완료(tuntun_score_peer_service.py의 같은 매핑 참고).
    ⚠️ input_definition('leisure_bouts10_strength_days_v1', "10분 이상 지속 활동" 기준)
    이 실제 온보딩 설문 문항과 일치하는지는 별도 확인 필요 - 확인 전까지 이 함수를
    실제 계산에 쓰면 안 됨.
    """
    if habit is None:
        return dict(score=None, contributions=None, formula_version="pending-selection",
                    input_definition=None, policy_status="pending")

    strength_days = habit.strength_weekly_count
    intensity = _STRENGTH_INTENSITY_MAP.get(str(habit.strength_intensity)) if strength_days else None

    result = calculate_initial_habit(
        low_minutes=habit.aerobic_low_minutes,
        moderate_minutes=habit.aerobic_moderate_minutes,
        vigorous_minutes=habit.aerobic_high_minutes,
        strength_days=strength_days,
        strength_intensity=intensity,
        input_revision=input_revision,
        input_definition=INITIAL_HABIT_DEFINITION,
    )
    return dict(
        score=result["score"], contributions=result["contributions"],
        formula_version=result["formula_version"], input_definition=result["input_definition"],
        policy_status="pending" if result["score"] is None else "approved",
    )


class PracticeScoreService:
    def __init__(self):
        self.health_repo = HealthInputRepository()
        self.exercise_repo = ExerciseHabitRepository()
        self.peer_service = TuntunScorePeerService()
        self.record_repo = RecordRepository()

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
        덮어쓰지 않음(user OneToOneField가 DB 레벨에서 재생성을 막음).

        ⚠️ 2026-09-16 갱신 - 온보딩 코드 확인 완료: OnboardingState.submitProfile()
        (A07, 신체정보 포함)이 성공하면 자동으로 OnboardingStep.A08_EXERCISE로 넘어가고
        그 화면에서 submitExerciseHabits()(A08, 운동습관)를 호출하는 강제 순차 흐름이다.
        즉 정상적으로 온보딩을 거친 사용자는 "가장 오래된 신체정보"와 "가장 오래된
        운동습관"의 서버 저장 시각이 항상 몇 분 이내로 근접한다. "서로 무관한 가장
        오래된 두 행을 가입 당시 완성본으로 단정하지 말라"는 원칙에 따라, 이 근접성이
        확인될 때만 실제 계산(policy_status='approved')을 수행하고, 확인 안 되면(둘
        중 하나가 없거나, 시간 차이가 비정상적으로 크면 - 예: 온보딩 없이 나중에 개별
        입력한 경우) pending으로 유지한다.
        """

        existing = await InitialHabitSnapshot.get_or_none(user_id=user.id)
        if existing is not None:
            return existing

        earliest_health = await self.health_repo.get_earliest(user.id)
        earliest_habit = await self.exercise_repo.get_earliest(user.id)

        # ⚠️ 온보딩 A07→A08 화면 전환에 걸리는 실제 시간을 실측한 근거는 아직 없음 -
        # 여유 있게 1시간(SIGNUP_CONFIRMATION_WINDOW_SECONDS)으로 잡은 값. 너무 좁으면
        # 정상 가입자도 놓치고, 너무 넓으면 "무관한 두 행"이 섞일 위험이 있어 실측 후
        # 조정이 필요할 수 있음.
        signup_confirmed = (
            earliest_health is not None and earliest_habit is not None
            and abs((earliest_health.created_at - earliest_habit.recorded_at).total_seconds())
            <= SIGNUP_CONFIRMATION_WINDOW_SECONDS
        )

        if not signup_confirmed:
            input_revision = (
                f"{earliest_health.id if earliest_health else 'none'}:"
                f"{earliest_habit.id if earliest_habit else 'none'}"
            )
            return await InitialHabitSnapshot.create(
                user_id=user.id,
                input_revision=input_revision,
                input_definition=None,
                formula_version="pending-selection",
                policy_status="pending",
                score=None,
                contributions=None,
            )

        input_revision = f"{earliest_health.id}:{earliest_habit.id}"
        calc = _calculate_initial_habit_from_snapshot(earliest_habit, input_revision)
        return await InitialHabitSnapshot.create(
            user_id=user.id,
            input_revision=input_revision,
            input_definition=calc["input_definition"],
            formula_version=calc["formula_version"],
            policy_status=calc["policy_status"],
            score=calc["score"],
            contributions=calc["contributions"],
        )

    async def get_practice_score(self, user: User) -> dict:  # noqa: C901 - 분기가 많아짐(v1→v2→쉼 연결), 다음에 정리 예정
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
            # ⚠️ 2026-09-15 추가 - 강호님 확정: "사용자가 명시적으로 쉬어가기를 선택한
            # 날만 쉼으로 표시. 기록이 없는 날을 자동으로 쉼 처리하지 않음." 기존
            # DailyRecordNote.is_rest_day(D05 "쉼" 표시)를 그대로 재사용 - 새 테이블 없음.
            notes = await self.record_repo.get_notes_in_range(user.id, start, today)
            days = []
            for d in _date_range(start, today):
                events = by_day.get(d, [])
                note = notes.get(d)
                if events:
                    day_status = "active"  # 유효 운동이 있으면 쉼보다 우선
                elif note is not None and note.is_rest_day:
                    day_status = "confirmed_rest"
                else:
                    day_status = "unknown"  # 무기록은 쉼이 아니라 unknown으로 보존
                days.append(dict(status=day_status, events=events))
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
