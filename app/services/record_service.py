from calendar import monthrange
from collections import Counter
from datetime import date, timedelta

from fastapi import HTTPException, status
from tortoise.transactions import in_transaction

from app.core.time_utils import service_today
from app.dtos.companion import MATERIAL_INFO
from app.dtos.records import (
    CalendarDayItem,
    DayDetailResponse,
    MemoUpdateRequest,
    MonthlyCalendarResponse,
    RestDayRequest,
    StreakResponse,
    WeeklyMaterialItem,
    WeeklyReportResponse,
)
from app.models.challenges import ChallengeEvent, ChallengeState
from app.models.users import User
from app.repositories.record_repository import RecordRepository
from app.services.challenge_service import ChallengeService

MAX_REST_DAYS_PER_WEEK = 2


def _week_boundaries(d: date) -> tuple[date, date]:
    """월요일 시작 기준 그 주의 (월요일, 일요일)."""

    monday = d - timedelta(days=d.weekday())
    sunday = monday + timedelta(days=6)
    return monday, sunday


def _time_slot_from_hour(hour: int) -> str:
    if 5 <= hour < 11:
        return "아침"
    if 11 <= hour < 17:
        return "점심 뒤"
    if 17 <= hour < 22:
        return "저녁"
    return "밤"


class RecordService:
    def __init__(self):
        self.repo = RecordRepository()
        # ⚠️ 2026-09-07 반영: REST<->GIVE_UP 전환(switch_to_give_up)에서 챌린지를 SKIPPED로
        # 넘기는 로직을 challenge_service.skip()과 별도로 다시 구현하면 idempotency_key
        # 생성 규칙 등이 어긋날 위험이 있어서, 기존 서비스를 그대로 재사용함.
        self.challenge_service = ChallengeService()

    async def _build_status_map(
        self, user_id, start: date, end: date, signup_date: date | None = None
    ) -> dict[date, tuple[str, object]]:
        """날짜 -> (status, card_set) 매핑. card_set은 상세 조회 시 재사용하려고 같이 반환.

        ⚠️ 2026-09-04 QA 반영: 가입 이전 날짜를 "미완료"로 보여주던 버그를 고치면서
        (signup_date 이전은 조회 자체를 안 하게) 예전엔 조회 범위(start)를 가입일로
        당겨버렸는데, 그러면 "최근 7일"/"주간" 같이 슬롯 개수가 고정된 위젯이 실제
        날짜 수만큼만 오는 문제가 새로 생겼음(홈 최근 7일이 2개만 나오는 등). 이제
        범위(start~end)는 그대로 두고, signup_date 이전 날짜만 "BEFORE_SIGNUP"이라는
        별도 상태로 표시함 - 슬롯 개수는 항상 요청한 범위만큼 나오면서도, 그 사람이
        아직 계정도 없었던 날을 "미완료"라고 하는 거짓은 안 하게 됨.
        """

        card_sets = await self.repo.get_card_sets_in_range(user_id, start, end)
        notes = await self.repo.get_notes_in_range(user_id, start, end)
        card_set_by_date = {cs.service_date: cs for cs in card_sets}

        status_map: dict[date, tuple[str, object]] = {}
        current = start
        while current <= end:
            note = notes.get(current)
            card_set = card_set_by_date.get(current)

            if signup_date is not None and current < signup_date:
                status_map[current] = ("BEFORE_SIGNUP", None)
            elif note and note.is_rest_day:
                status_map[current] = ("REST", card_set)
            elif card_set and card_set.selection and card_set.selection.challenge:
                challenge_state = card_set.selection.challenge.state
                status_map[current] = ("COMPLETED" if challenge_state == "COMPLETED" else "INCOMPLETE", card_set)
            else:
                status_map[current] = ("INCOMPLETE", card_set)

            current += timedelta(days=1)
        return status_map

    async def get_monthly_calendar(self, user: User, year: int, month: int) -> MonthlyCalendarResponse:
        _, last_day = monthrange(year, month)
        start = date(year, month, 1)
        end = date(year, month, last_day)
        today = service_today(user.id)  # ⚠️ 2026-09-08: 계정별 오프셋 적용
        if end > today:
            end = today  # 미래 날짜는 아예 표시 안 함 (완료 여부를 알 수 없으니까)

        if start > today:
            return MonthlyCalendarResponse(year=year, month=month, days=[], completed_count=0, rest_count=0)

        # ⚠️ 2026-09-04 QA 반영: "가입 이전" 날짜를 시작일을 당겨서 아예 안 만들었더니,
        # 이번 달의 실제 칸 수 자체가 줄어버리는 부작용이 있었음(달력이야 어차피 칸이
        # 넘치니 큰 문제는 아니지만, 아래 주간 리포트에서는 슬롯 수가 고정이라 문제가 됨 -
        # 일관성 있게 여기도 범위는 그대로 두고 signup_date만 넘겨서 상태로 구분함).
        signup_date = user.created_at.date()

        status_map = await self._build_status_map(user.id, start, end, signup_date=signup_date)
        days = [CalendarDayItem(date=d, status=s) for d, (s, _) in sorted(status_map.items())]
        completed_count = sum(1 for _, (s, _) in status_map.items() if s == "COMPLETED")
        rest_count = sum(1 for _, (s, _) in status_map.items() if s == "REST")

        return MonthlyCalendarResponse(
            year=year, month=month, days=days, completed_count=completed_count, rest_count=rest_count
        )

    async def get_weekly_report(self, user: User) -> WeeklyReportResponse:
        today = service_today(user.id)  # ⚠️ 2026-09-08: 계정별 오프셋 적용
        start = today - timedelta(days=6)
        signup_date = user.created_at.date()

        status_map = await self._build_status_map(user.id, start, today, signup_date=signup_date)
        days = [CalendarDayItem(date=d, status=s) for d, (s, _) in sorted(status_map.items())]
        completed_count = sum(1 for _, (s, _) in status_map.items() if s == "COMPLETED")

        # 이번 주 완료된 챌린지들의 오행 집계 + 완료 시간대 집계
        element_counter: Counter = Counter()
        hour_counter: Counter = Counter()

        for _, (day_status, card_set) in status_map.items():
            if day_status != "COMPLETED" or card_set is None:
                continue
            selection = card_set.selection
            if selection is None or selection.challenge is None:
                continue
            challenge = selection.challenge
            five_element = (challenge.mission_snapshot or {}).get("five_element")
            if five_element:
                element_counter[five_element] += 1

            complete_event = (
                await ChallengeEvent.filter(challenge_id=challenge.id, event_type="COMPLETE")
                .order_by("-server_at")
                .first()
            )
            if complete_event:
                hour_counter[_time_slot_from_hour((complete_event.occurred_at or complete_event.server_at).hour)] += 1

        materials = [
            WeeklyMaterialItem(element=element, material_name=MATERIAL_INFO[element]["material_name"], count=count)
            for element, count in element_counter.items()
        ]
        best_time_slot = hour_counter.most_common(1)[0][0] if hour_counter else None

        return WeeklyReportResponse(
            start_date=start,
            end_date=today,
            days=days,
            completed_count=completed_count,
            total_days=7,
            best_time_slot=best_time_slot,
            materials_this_week=materials,
        )

    async def get_day_detail(self, user: User, target_date: date) -> DayDetailResponse:
        status_map = await self._build_status_map(user.id, target_date, target_date)
        day_status, card_set = status_map[target_date]

        note = await self.repo.get_or_create_note(user.id, target_date)

        response = DayDetailResponse(date=target_date, status=day_status, memo=note.memo)

        if card_set and card_set.selection and card_set.selection.challenge:
            challenge = card_set.selection.challenge
            template = card_set.selection.card_option.mission_template_version
            response.mission_title = template.title
            response.exec_type = challenge.exec_type
            response.duration_seconds = challenge.accumulated_duration_seconds or None
            response.count_achieved = challenge.accumulated_count or None
            response.element = (challenge.mission_snapshot or {}).get("five_element")
            if response.element:
                response.material_name = MATERIAL_INFO.get(response.element, {}).get("material_name")

            if day_status == "COMPLETED":
                complete_event = (
                    await ChallengeEvent.filter(challenge_id=challenge.id, event_type="COMPLETE")
                    .order_by("-server_at")
                    .first()
                )
                if complete_event:
                    response.completed_at = (complete_event.occurred_at or complete_event.server_at).isoformat()

        return response

    async def update_memo(self, user: User, target_date: date, request: MemoUpdateRequest) -> DayDetailResponse:
        note = await self.repo.get_or_create_note(user.id, target_date)
        note.memo = request.memo
        await note.save(update_fields=["memo", "updated_at"])
        return await self.get_day_detail(user, target_date)

    async def mark_rest_day(self, user: User, request: RestDayRequest) -> StreakResponse:
        target_date = request.service_date
        today = service_today(user.id)  # ⚠️ 2026-09-08: 계정별 오프셋 적용
        if target_date > today:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail="미래 날짜는 쉼으로 표시할 수 없습니다."
            )

        monday, sunday = _week_boundaries(target_date)

        # ⚠️ 2026-09-03 리뷰 반영: 챌린지 상태 확인 없이 note.is_rest_day만 True로 바꿔서,
        # 이미 완료(COMPLETED)한 날에 쉼을 찍으면 _build_status_map이 note.is_rest_day를
        # 먼저 보기 때문에(위 61번째 줄) 캘린더에서 완료가 사라지고 REST로 보였음. 재료·
        # 포인트는 이미 지급된 상태인데 화면만 쉼으로 바뀌는 모순이라, 이미 완료된 날은
        # 막음. (안드로이드 CardHomeState.kt의 openRestDaySheet()에 같은 취지의 방어가
        # 있었는데, 기기를 바꿔도 맞으려면 서버에도 있어야 함.)
        target_card_set = await self.repo.get_card_set_by_date(user.id, target_date)
        if (
            target_card_set
            and target_card_set.selection
            and target_card_set.selection.challenge
            and target_card_set.selection.challenge.state == "COMPLETED"
        ):
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT, detail="이미 완료한 날은 쉼으로 표시할 수 없습니다."
            )

        existing_note = await self.repo.get_or_create_note(user.id, target_date)

        if not existing_note.is_rest_day:
            rest_count_this_week = await self.repo.count_rest_days_in_range(user.id, monday, sunday)
            if rest_count_this_week >= MAX_REST_DAYS_PER_WEEK:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"이번 주에 이미 쉼을 {MAX_REST_DAYS_PER_WEEK}번 사용했습니다.",
                )

        existing_note.is_rest_day = True
        # ⚠️ 2026-09-07 반영: 상태전이 정책(GIVE_UP -> REST, 전이 T-포기취소) - "포기"
        # 표시와 "쉼" 표시는 daily_record_notes에서 서로 배타적이어야 함(달력·홈에서
        # 동시에 두 상태가 보이면 안 됨). is_given_up이 켜져 있던 날에 쉼을 선택하면
        # 여기서 같이 꺼줌 - 별도 "포기 취소" 호출 없이 이 API 하나로 전환이 끝남
        # (실제로 challenge.state==SKIPPED이면 challenge 쪽은 그대로 두는데, 이미
        # start()가 SKIPPED에서도 재시작을 허용하므로(G2) 문제 없음).
        existing_note.is_given_up = False
        await existing_note.save(update_fields=["is_rest_day", "is_given_up", "updated_at"])

        return await self.get_streak(user)

    async def switch_to_give_up(self, user: User, target_date: date) -> StreakResponse:
        """⚠️ 2026-09-07 반영: 백엔드전달_상태전이 문서의 REST -> GIVE_UP 전환(T13 계열).
        "쉬어가기 취소"와 "포기 기록"을 하나의 트랜잭션으로 묶음 - 두 API를 순서대로 따로
        부르게 하면, 두 번째 호출이 실패했을 때 "쉼 티켓은 이미 환불됐는데 상태는 여전히
        쉼으로 보이는" 불일치가 생길 수 있음.

        카드를 뽑고 챌린지가 있는 날은 challenge_service.skip()으로 챌린지 자체를
        SKIPPED로 넘기고(그게 곧 "포기" 표시), 카드를 아직 안 뽑은 날(B18 등)은 애초에
        challenge가 없으므로 G3에서 추가한 note.is_given_up 플래그로 대신 기록함.
        """

        today = service_today(user.id)  # ⚠️ 2026-09-08: 계정별 오프셋 적용
        if target_date > today:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail="미래 날짜는 포기로 표시할 수 없습니다."
            )

        card_set = await self.repo.get_card_set_by_date(user.id, target_date)
        challenge = card_set.selection.challenge if (card_set and card_set.selection) else None

        if challenge is not None and challenge.state == ChallengeState.COMPLETED:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT, detail="이미 완료한 날은 포기로 표시할 수 없습니다."
            )

        async with in_transaction():
            note = await self.repo.get_or_create_note(user.id, target_date)
            note.is_rest_day = False

            if challenge is not None:
                # 이미 SKIPPED면(중복 호출 등) challenge_service.skip()이 409를 던지므로,
                # 여기서 미리 걸러서 조용히 통과시킴 - 사용자 입장에선 결과가 같아야 함.
                if challenge.state != ChallengeState.SKIPPED:
                    await self.challenge_service.skip(user, challenge.id, reason="REST_TO_GIVE_UP")
                note.is_given_up = False  # challenge.state==SKIPPED 쪽이 곧 "포기" 표시라 중복 표시 안 함
            else:
                note.is_given_up = True

            await note.save(update_fields=["is_rest_day", "is_given_up", "updated_at"])

        return await self.get_streak(user)

    async def cancel_rest_day(self, user: User, target_date: date) -> StreakResponse:
        """⚠️ 2026-09-07 반영: 백엔드전달_상태전이 문서 G1(P0) - "쉬어가기 취소" API가
        없어서 T08/T09/T10/T13(번복) 전이가 전부 막혀 있었음. mark_rest_day와 같은 날짜
        검증 규칙(미래 날짜 거부)을 그대로 적용. 이미 REST가 아니면(is_rest_day=False)
        조용히 그대로 반환 - 중복 취소를 에러로 취급할 이유가 없음."""

        today = service_today(user.id)  # ⚠️ 2026-09-08: 계정별 오프셋 적용
        if target_date > today:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail="미래 날짜는 쉼을 취소할 수 없습니다."
            )

        note = await self.repo.get_or_create_note(user.id, target_date)
        if note.is_rest_day:
            note.is_rest_day = False
            await note.save(update_fields=["is_rest_day", "updated_at"])

        return await self.get_streak(user)

    async def get_streak(self, user: User) -> StreakResponse:
        today = service_today(user.id)  # ⚠️ 2026-09-08: 계정별 오프셋 적용
        earliest = await self.repo.get_earliest_card_set_date(user.id)

        if earliest is None:
            monday, sunday = _week_boundaries(today)
            return StreakResponse(
                current_streak=0,
                longest_streak=0,
                rest_days_used_this_week=0,
                rest_days_remaining_this_week=MAX_REST_DAYS_PER_WEEK,
            )

        status_map = await self._build_status_map(user.id, earliest, today)

        # ⚠️ 2026-09-03 리뷰 반영: 오늘부터 거슬러 올라가는데, 아침에 아직 카드를 안 뽑은
        # 시점의 "오늘"은 INCOMPLETE라 첫 바퀴에서 바로 끊겨서 30일 연속이던 사람도 매일
        # 아침 "0일"로 보이는 문제가 있었음. CLAUDE.md 기준 "아무것도 안 하고 지나간 날은
        # 미완료"인데, 오늘은 아직 지나가지 않았으므로 오늘이 미완료면 어제부터 세기 시작함.
        current_streak = 0
        d = today
        if status_map[today][0] not in ("COMPLETED", "REST"):
            d = today - timedelta(days=1)
        while d >= earliest:
            day_status, _ = status_map[d]
            if day_status in ("COMPLETED", "REST"):
                current_streak += 1
                d -= timedelta(days=1)
            else:
                break

        # 과거 전체를 순방향으로 훑으며 최장 기록 계산
        longest_streak = 0
        running = 0
        d = earliest
        while d <= today:
            day_status, _ = status_map[d]
            if day_status in ("COMPLETED", "REST"):
                running += 1
                longest_streak = max(longest_streak, running)
            else:
                running = 0
            d += timedelta(days=1)

        monday, sunday = _week_boundaries(today)
        rest_used = await self.repo.count_rest_days_in_range(user.id, monday, sunday)

        return StreakResponse(
            current_streak=current_streak,
            longest_streak=longest_streak,
            rest_days_used_this_week=rest_used,
            rest_days_remaining_this_week=max(MAX_REST_DAYS_PER_WEEK - rest_used, 0),
        )
