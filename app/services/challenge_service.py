from datetime import datetime, time, timedelta

from fastapi import HTTPException, status
from tortoise.exceptions import IntegrityError, OperationalError
from tortoise.transactions import in_transaction

from app.core import config
from app.core.logger import default_logger
from app.dtos.challenges import ChallengeProgressResponse, CompleteChallengeResponse
from app.models.challenges import ChallengeEventType, ChallengeState, duration_seconds_from_target
from app.models.users import User
from app.repositories.challenge_repository import ChallengeRepository
from app.repositories.companion_repository import CompanionRepository

BASE_POINTS_PER_COMPLETION = 10  # 기능명세서 §8.1: "모든 P0 완료는 속성과 관계없이 동일 기본 포인트를 사용"

# CHECK는 사용자가 직접 완료 버튼을 눌러서 끝내는 방식이라 서버가 검증할 측정값이 없음.
# 나머지는 전부 target_duration_seconds 또는 target_count 중 하나로 목표가 정해져 있음.
NO_VALIDATION_EXEC_TYPES = {"CHECK"}

# ⚠️ 2026-09-08 반영(걷기 미션 시간 이중 계산 버그): accumulated_duration_seconds를 채우는
# 주체가 유형마다 다름.
#   - TIMER: 서버만 씀. 일시정지 때 확정 저장하고, ACTIVE 구간은 started_at부터 지금까지를
#     서버가 계산해서 더해야 실제 경과 시간이 나옴.
#   - SENSOR_*_DURATION(걷기·뛰기 시간): 폰의 케이던스 매니저가 "실제로 걸은 누적 초"를
#     배치로 올려주고 sensor_repository.set_duration_if_higher()가 그대로 저장함. 이미 완성된
#     값이라 서버가 벽시계 시간을 더하면 안 됨.
# 그런데 걷기도 시작할 때 startChallenge()를 부르므로(SensorChallengeScreens.kt) started_at이
# 세팅되고, 예전 코드는 유형 구분 없이 (지금 - started_at)을 더했음 - 폰이 보낸 "걸은 시간"에
# 앱을 켜둔 "벽시계 시간"이 얹혀서, 가만히 있어도 목표가 채워졌음(5분 걷기 미션이 5분 뒤
# 자동 달성). 벽시계 구간을 더하는 건 TIMER일 때만으로 한정함.
WALL_CLOCK_ELAPSED_EXEC_TYPES = {"TIMER"}


def _target_duration_seconds(challenge) -> int | None:
    """이 챌린지의 목표 시간(초). 시간 목표가 없는 유형(CHECK·카운터형)이면 None.

    ⚠️ 2026-09-07 반영: 위 버그(단위 변환 누락, duration_seconds_from_target() 주석 참고)
    이전에 만들어진 challenges 행에는 target_duration_seconds 컬럼에 "분" 숫자가 그대로
    들어가 있음(1분짜리가 1). 컬럼만 믿으면 그 행들은 코드를 고쳐도 계속 1초로 잘리므로,
    카드 확정 시점에 통째로 복사해둔 mission_snapshot(target_value·unit이 그대로 들어있음)에서
    다시 계산해서 씀. 새로 만들어지는 행은 컬럼도 이미 초 단위로 맞게 저장되니 둘의 계산
    결과가 같음 - 즉 이 함수 하나가 신·구 데이터를 모두 같은 기준으로 맞춰줌.
    스냅샷이 없거나 값이 이상한 예외적인 행만 컬럼값으로 폴백함.
    """

    if challenge.target_duration_seconds is None:
        return None
    snapshot = challenge.mission_snapshot or {}
    snapshot_target_value = snapshot.get("target_value")
    if isinstance(snapshot_target_value, int) and snapshot_target_value > 0:
        return duration_seconds_from_target(snapshot_target_value, snapshot.get("unit"))
    return challenge.target_duration_seconds


def _duration_at(challenge, as_of: datetime) -> int:
    """ "as_of 시점 기준"의 경과 시간. 아래 _effective_duration_seconds()의 실제 구현.

    ⚠️ 2026-09-08 추가(자정 정산): 지난 날짜 챌린지를 마감할 때는 "지금"이 아니라 "그날이
    끝난 자정"까지만 세야 함 - 어제 시작해서 안 끝낸 미션에 오늘 흐른 시간까지 얹어서 기록을
    남기면 안 되기 때문. 그 경우에만 as_of에 자정을 넣고, 평상시에는 지금 시각이 들어옴.
    """

    base = challenge.accumulated_duration_seconds
    # ⚠️ 2026-09-08 반영: 벽시계 구간을 더하는 건 TIMER만(WALL_CLOCK_ELAPSED_EXEC_TYPES 주석
    # 참고). 걷기·뛰기 시간형은 폰이 보낸 누적 초가 이미 정답이라 여기서 더하면 이중 계산됨.
    if (
        challenge.exec_type in WALL_CLOCK_ELAPSED_EXEC_TYPES
        and challenge.state == ChallengeState.ACTIVE
        and challenge.started_at is not None
    ):
        started_at = challenge.started_at
        if started_at.tzinfo is None:
            # Tortoise 버전에 따라 naive로 돌려주기도 함 - 저장한 기준(KST)으로 라벨링.
            started_at = started_at.replace(tzinfo=config.TIMEZONE)
        # aware끼리 빼면 각자의 타임존과 무관하게 실제 시간 차가 나오므로 여기서 굳이 다른
        # 타임존으로 변환하지 않음(예전엔 UTC로 강제 변환하면서 오히려 9시간 어긋남을 그대로
        # 계산에 반영했음 - 아래 _effective_duration_seconds() 주석 참고).
        base += int((as_of - started_at).total_seconds())
    # ⚠️ 2026-09-07 반영: 상한으로 쓸 목표값을 컬럼에서 바로 읽지 않고 _target_duration_seconds()를
    # 거침 - 예전 데이터는 이 컬럼이 "분" 숫자라서(1분짜리가 1) 여기서 경과 시간이 전부 1초로
    # 잘려나갔던 게 "화면 다시 들어가면 1초로 바뀐다"는 버그의 정체였음.
    target_seconds = _target_duration_seconds(challenge)
    if target_seconds is not None:
        base = min(base, target_seconds)
    # ⚠️ 방어: 어떤 이유로든(기기·서버 시계 어긋남 등) 음수가 나오면 그대로 쓰지 않음.
    # 예전에 이 값이 음수가 돼서 누적 시간이 깎여나간 사고가 있었음(pause() 주석 참고).
    return max(base, 0)


def _effective_duration_seconds(challenge) -> int:
    """⚠️ 2026-09-01 수정: 예전엔 accumulated_duration_seconds 컬럼값만 봤는데, 이 값은
    "일시정지했던 시점까지"만 반영되고 지금 ACTIVE로 흐르고 있는 구간은 안 들어있었음
    (모델 docstring엔 원래 이렇게 계산하기로 되어 있었는데 실제 코드가 안 따라가고 있었음).
    ACTIVE 상태면 "지금까지 쌓인 것 + (지금 - started_at)"까지 더해서 실제 경과 시간을 계산.

    ⚠️ 2026-09-08 반영(타이머가 시작하자마자 목표치로 점프하던 버그의 진짜 원인 - 실제
    로그로 확인함): started_at을 저장할 때만 datetime.now(UTC)를 쓰고 있었는데, Tortoise
    설정(app/core/db/databases.py)은 timezone="Asia/Seoul" + use_tz=False임. 이 조합에서
    저장/조회가 서로 대칭이 아님:
      - 저장: use_tz=False라 Tortoise가 변환을 안 하고, MySQL 드라이버는 tzinfo를 그냥
        버리고 숫자만 씀 -> DB에 "UTC 숫자"가 들어감.
      - 조회: DB의 naive 값에 설정된 타임존(Asia/Seoul)을 붙여서 돌려줌 -> "UTC 숫자 +
        09:00" 이라는 실제로는 9시간 어긋난 시각이 됨.
    실제 로그(2026-09-08 10:44:56 KST에 시작):
      start() done: started_at=2026-09-08 01:44:56+09:00   <- 9시간 과거로 읽힘
      reveal: ... -> elapsed_seconds=180                    <- 6초 뒤인데 3분 목표가 꽉 참
    즉 시작하자마자 "9시간 전에 시작한 것"이 돼서, 아래 목표치 상한에 걸려 항상 목표치가
    그대로 나왔음(예전에 "1초"로 보이던 것도 그때는 상한이 1이었기 때문 - 같은 원인).
    이제 저장할 때도 다른 저장소들(user_repository·health_repository·time_utils 등)과
    똑같이 datetime.now(config.TIMEZONE)을 써서 DB에 "KST 숫자"가 들어가게 하고, 여기서도
    같은 기준(config.TIMEZONE)으로 읽고 비교함 - 저장과 조회가 대칭이 됨.

    ⚠️ 2026-09-06 반영(목표치 상한): 시작해두고 뒤로가기만 한 채 오래 방치하면(서버엔
    일시정지가 전달 안 됨) ACTIVE 상태가 며칠이고 유지되면서 경과 시간이 끝없이 커짐
    (예: 9/4 새벽에 시작한 걸 9/6에 열어보니 45시간짜리로 나온 사례). "시작/이어하기 ~
    일시정지/완료 전까지는 계속 측정 중이지만, 목표치에 도달하면 그 값에서 멈춘다"는
    방향에 맞춰 target_duration_seconds가 있으면 그 값을 상한으로 고정 - 앱을 며칠 안
    열어봐도 항상 목표치를 넘지 않는 값만 보이게 됨.
    """

    return _duration_at(challenge, datetime.now(config.TIMEZONE))


def _describe_shortfall(challenge) -> str:
    """목표 미달성 시 "무엇이 얼마나 부족한지" 사람이 읽을 수 있는 문구로 설명."""

    target_seconds = _target_duration_seconds(challenge)
    if target_seconds is not None:
        current = _effective_duration_seconds(challenge)
        remaining = target_seconds - current
        return (
            f"목표 시간에 아직 도달하지 못했습니다. {current}초 / {target_seconds}초 (남은 시간 {max(remaining, 0)}초)"
        )
    if challenge.target_count is not None:
        remaining = challenge.target_count - challenge.accumulated_count
        return (
            f"목표치에 아직 도달하지 못했습니다. "
            f"{challenge.accumulated_count} / {challenge.target_count} "
            f"(남은 수량 {max(remaining, 0)})"
        )
    return "목표값이 설정되지 않은 챌린지입니다."


def _is_goal_achieved(challenge) -> bool:
    """exec_type별로 target_duration_seconds 또는 target_count 중 해당하는 것과 비교.
    CHECK는 서버가 검증할 측정값이 없어서 항상 통과(사용자 확인 자체가 완료 조건)."""

    if challenge.exec_type in NO_VALIDATION_EXEC_TYPES:
        return True
    # ⚠️ 2026-09-07 반영: 여기도 컬럼을 그대로 믿으면 "1초 >= 1초"라 시작 1초 만에 목표
    # 달성으로 통과됐음(서버 쪽 완료 검증이 사실상 무력화돼 있었음 - 앱이 자체 기준으로
    # 완료 버튼을 막아둬서 드러나지 않았을 뿐).
    target_seconds = _target_duration_seconds(challenge)
    if target_seconds is not None:
        return _effective_duration_seconds(challenge) >= target_seconds
    if challenge.target_count is not None:
        return challenge.accumulated_count >= challenge.target_count
    # 목표값 자체가 없는 예외적인 경우 - 막지 않고 통과 (설계상 있으면 안 되지만 방어적으로 처리)
    return True


def _final_duration_for_completion(challenge, manual_check: bool) -> int:
    """⚠️ 2026-09-08 반영: complete()에서 분리 - manual_check(직접 체크로 완료)면 실측
    시간 대신 목표치를 그대로 확정값으로 씀(그래야 결과에 애매한 미달성 시간이 안 남음)."""

    if manual_check and challenge.target_duration_seconds is not None:
        return challenge.target_duration_seconds
    return _effective_duration_seconds(challenge)  # 완료 순간까지 흐른 진짜 시간


def _raise_if_goal_not_achieved(challenge) -> None:
    """complete()에서 분리 - manual_check가 아닐 때만 호출됨."""

    if not _is_goal_achieved(challenge):
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=_describe_shortfall(challenge))


class ChallengeService:
    def __init__(self):
        self.challenge_repo = ChallengeRepository()
        self.companion_repo = CompanionRepository()

    async def get_progress(self, user: User, challenge_id) -> ChallengeProgressResponse:
        challenge = await self._get_owned_challenge(user, challenge_id)
        return ChallengeProgressResponse.model_validate(challenge)

    async def start(self, user: User, challenge_id) -> ChallengeProgressResponse:
        """C02/C09의 "시작하기"·"측정 시작" 버튼 - 여기서 처음 눌러야만 서버 상태가
        READY -> ACTIVE로 바뀌고 started_at이 기록됨. 일시정지에서 "이어서 하기"도
        같은 엔드포인트 재사용(PAUSED -> ACTIVE, 새 구간 시작)."""

        challenge = await self._get_owned_challenge(user, challenge_id)
        # ⚠️ 2026-09-08: 여기만 UTC를 쓰다가 조회 시 Asia/Seoul로 라벨링돼 9시간 어긋났음
        # (_effective_duration_seconds() 주석에 로그와 함께 전체 설명). 나머지 저장소들과
        # 같은 기준으로 통일.
        now = datetime.now(config.TIMEZONE)

        # ⚠️ 2026-09-07 반영: 백엔드전달_상태전이 문서 G2(P0) - SKIPPED(포기)에서 재시작이
        # 막혀 있어서 "마음 바꾸기 · 다시 도전하기"(전이 T12, 화면 B21/B24)가 전부 409였음.
        # 정책 원칙 P1("자정 전에는 어떤 선택도 영구 확정되지 않는다")과 충돌하던 부분.
        #
        # ⚠️ 2026-09-08 정정: 이 자리에 원래 "skip()이 진행값을 그대로 두므로 다시 ACTIVE로
        # 돌아가도 이어서 누적됨"이라고 적혀 있었는데 사실이 아니었음. skip()은 진행값을
        # "확정 저장하지 않았을" 뿐이라, 일시정지 없이 진행하다 포기하면 accumulated가 0인
        # 채로 굳어서 실제로는 이어지지 않았음(반대로 일시정지 후 포기하면 이어졌음 - 같은
        # 포기인데 결과가 갈렸음). 지금은 정책대로 skip()이 진행값을 명시적으로 0으로
        # 지우므로, SKIPPED에서 재시작하면 항상 "처음부터 다시"가 맞는 동작임.
        transitioned = await self.challenge_repo.try_transition(
            challenge,
            from_states=[ChallengeState.READY, ChallengeState.PAUSED, ChallengeState.SKIPPED],
            to_state=ChallengeState.ACTIVE,
        )
        if not transitioned:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="시작할 수 없는 상태입니다.",
                headers={"X-Current-State": challenge.state},
            )

        await self.challenge_repo.update_started_at(challenge.id, now)
        await self.challenge_repo.create_event(
            challenge_id=challenge.id,
            event_type=ChallengeEventType.START,
            idempotency_key=f"start:{challenge.id}:{challenge.version + 1}",
            version=challenge.version + 1,
        )
        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        # ⚠️ 2026-09-07 QA(N2/타이머 리셋) 임시 진단 로그 - 뒤로가기/일시정지/이어하기 조합에서
        # 경과시간이 1초로 되돌아간다는 재현 보고가 있어서, DB에 실제로 뭐가 저장됐는지 눈으로
        # 확인하려고 추가함. 원인 확정되면 지워도 됨.
        default_logger.warning(
            "[TIMER-DEBUG] start() done: challenge_id=%s accumulated=%s started_at=%s state=%s",
            refreshed.id,
            refreshed.accumulated_duration_seconds,
            refreshed.started_at,
            refreshed.state,
        )
        return ChallengeProgressResponse.model_validate(refreshed)

    async def settle_past_days(self, user: User, today) -> int:
        """⚠️ 2026-09-08 추가(자정 정산): 지난 날짜인데 아직 안 끝난 챌린지를 미완료로 마감.

        지금까지 스케줄러·배치가 아예 없어서(celery·apscheduler·cron 미사용) "어제 시작하고
        안 끝낸 것"이 계속 ACTIVE로 남아 있었음 - 실제 DB에서 9/4에 시작한 챌린지가 9/8까지
        살아서 시간을 계속 쌓고 있는 걸 확인함. 별도 워커를 두는 대신, 홈에 들어올 때
        (card_service.get_or_create_today) 이 함수가 지난 날짜분을 그 자리에서 정리함.

        마감 시각은 "지금"이 아니라 그 날짜의 다음 자정(KST)임 - 어제 미션 기록에 오늘 흐른
        시간까지 얹으면 안 되기 때문(_duration_at 주석 참고).

        캘린더·연속 기록은 원래도 읽는 시점에 "COMPLETED가 아니면 INCOMPLETE"로 판정하므로
        (record_service._build_status_map) 이 마감으로 기록 값이 달라지지는 않음. 목적은
        어제 것이 오늘도 계속 진행되는 걸 끊고, 상태를 명시적으로 남기는 것.

        반환값은 마감한 건수. 실패해도 홈 진입 자체는 막지 않도록 호출부에서 감싸서 씀.
        """

        stale = await self.challenge_repo.find_unsettled_before(user.id, today)
        settled = 0
        for challenge in stale:
            service_date = challenge.selection.card_set.service_date
            # 그 날짜가 끝난 순간 = 다음 날 00:00 (KST)
            end_of_day = datetime.combine(service_date + timedelta(days=1), time.min, tzinfo=config.TIMEZONE)
            final_duration = _duration_at(challenge, end_of_day)

            # ⚠️ 2026-09-08 2차 반영: from_states를 repo의 UNSETTLED_STATES와 같은 집합으로 씀
            # (READY 포함). 조회는 READY까지 가져오는데 여기서 못 넘기면 매번 조회만 하고
            # 아무것도 정산되지 않으므로, 두 곳이 어긋나지 않도록 한 군데서 가져다 씀.
            transitioned = await self.challenge_repo.try_transition(
                challenge,
                from_states=ChallengeRepository.UNSETTLED_STATES,
                to_state=ChallengeState.SKIPPED,
            )
            if not transitioned:
                # 그사이 사용자가 직접 완료·포기했거나 다른 요청이 먼저 마감함 - 건너뜀.
                continue

            # 진행값을 그 자리에서 확정하고 started_at을 비움 - 안 비우면 SKIPPED가 된 뒤에도
            # started_at이 남아서 나중에 재시작(G2) 시 계산이 꼬일 수 있음.
            # ⚠️ 사용자가 직접 누른 포기(skip)는 진행값을 0으로 지우지만, 자정 정산은 지우지
            # 않고 그날 실제로 한 만큼을 그대로 확정함 - "본인이 접은 것"과 "시간이 다 돼서
            # 끝난 것"은 다른 사건이고, 후자는 그날 기록으로 남는 게 맞기 때문. (지난 날짜라
            # 어차피 재도전 대상도 아님)
            await self.challenge_repo.set_accumulated_duration_and_clear_start(challenge.id, final_duration)
            await self.challenge_repo.create_event(
                challenge_id=challenge.id,
                event_type=ChallengeEventType.SKIP,
                idempotency_key=f"settle:{challenge.id}:{challenge.version + 1}",
                version=challenge.version + 1,
                payload={"reason": "MIDNIGHT_SETTLEMENT", "service_date": service_date.isoformat()},
            )
            settled += 1

        if settled:
            default_logger.info(
                "[SETTLE] 지난 날짜 챌린지 %s건 미완료로 마감: user_id=%s today=%s", settled, user.id, today
            )
        return settled

    async def pause(self, user: User, challenge_id) -> ChallengeProgressResponse:
        """C03의 "일시정지" 버튼 - 지금까지 흐른 시간을 accumulated_duration_seconds에
        확정 저장하고 멈춤. TIMER/SENSOR_*_DURATION 타입에서만 의미 있음(CHECK·카운터형은
        애초에 이 버튼이 화면에 없음).

        ⚠️ 2026-09-07 반영(N2 타이머 리셋 버그의 실제 원인): 예전엔 "_effective_duration_seconds
        (캡 적용된 값) - accumulated_duration_seconds(캡 안 된 원본)"으로 "이번 구간 경과"를
        역산해서 F()+extra 방식으로 더했음. 그런데 target_duration_seconds 상한(2026-09-06
        추가)이 있는 상태에서, DB의 accumulated_duration_seconds가 이미 target을 넘어서 있으면
        (예: 예전에 오래 방치돼서 캡 없이 쌓였던 값이 남아있는 경우) _effective_duration_seconds가
        target으로 캡된 더 작은 값을 반환하니, 이 뺄셈이 **음수**가 나옴. 그 음수를 F()로 더하면
        기존 누적값이 오히려 확 깎여서 "20초 쌓였는데 일시정지하니 1초"처럼 보였던 것.
        이제는 역산하지 않고, 캡 적용된 최종값을 그대로 "확정값"으로 절대 저장(set)함 -
        뺄셈 자체가 없으니 음수가 나올 구조가 아예 사라짐.
        """

        challenge = await self._get_owned_challenge(user, challenge_id)
        final_accumulated = _effective_duration_seconds(challenge)

        transitioned = await self.challenge_repo.try_transition(
            challenge, from_states=[ChallengeState.ACTIVE], to_state=ChallengeState.PAUSED
        )
        if not transitioned:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="일시정지할 수 없는 상태입니다.",
                headers={"X-Current-State": challenge.state},
            )

        await self.challenge_repo.set_accumulated_duration_and_clear_start(challenge.id, final_accumulated)
        await self.challenge_repo.create_event(
            challenge_id=challenge.id,
            event_type=ChallengeEventType.PAUSE,
            idempotency_key=f"pause:{challenge.id}:{challenge.version + 1}",
            version=challenge.version + 1,
        )
        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        # ⚠️ 2026-09-07 QA(N2/타이머 리셋) 임시 진단 로그 - start()의 로그와 짝. 원인 확정됐으니
        # 다음 배포에서 지워도 됨(당분간은 실제로 고쳐졌는지 로그로 다시 확인하기 위해 남겨둠).
        default_logger.warning(
            "[TIMER-DEBUG] pause() done: challenge_id=%s final_accumulated=%s accumulated=%s started_at=%s state=%s",
            refreshed.id,
            final_accumulated,
            refreshed.accumulated_duration_seconds,
            refreshed.started_at,
            refreshed.state,
        )
        return ChallengeProgressResponse.model_validate(refreshed)

    async def complete(
        self, user: User, challenge_id, idempotency_key: str, occurred_at=None, manual_check: bool = False
    ) -> CompleteChallengeResponse:
        """문서 §4 "중복 보상이 구조적으로 불가능한 이유"를 그대로 구현:
        1) idempotency_key UNIQUE로 이벤트 중복 생성을 막고
        2) source_event_id UNIQUE로 포인트 중복 적립을 막는다.
        앱이 네트워크 오류로 같은 요청을 여러 번 보내도, 최초 1번의 결과가 그대로 반환된다.

        occurred_at: 오프라인 상태에서 실제로 완료한 시각(클라이언트가 앎).
        HANDOFF.md §3.3 "기록 시각은 업로드 시각이 아니라 실제 완료 시각" 반영.
        안 보내면 None -> ChallengeEvent.server_at(서버 수신 시각)으로 자동 대체됨.

        manual_check: ⚠️ 2026-09-08 반영 - SENSOR형 미션에서 "직접 체크로 할래요"를 눌러
        들어온 완료 요청. 실측값(accumulated_count/accumulated_duration_seconds) 검증을
        건너뛰고, 그 자리에서 목표치를 그대로 채워 확정 저장함(그래야 결과 화면에
        "9/10칸" 같은 어색한 미달성 수치가 안 남고, 이후 조회에서도 정상적으로
        "완료됨"으로 보임).
        """

        challenge = await self._get_owned_challenge(user, challenge_id)

        # 이미 같은 idempotency_key로 처리된 요청이면, 새로 만들지 않고 기존 결과를 그대로 반환
        existing_event = await self.challenge_repo.get_event_by_idempotency_key(idempotency_key, user_id=user.id)
        if existing_event is not None:
            if existing_event.challenge_id != challenge.id:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Idempotency-Key가 다른 챌린지에 이미 사용되었습니다.",
                )
            return await self._build_complete_response(challenge, existing_event)

        if not manual_check:
            _raise_if_goal_not_achieved(challenge)

        five_element = challenge.mission_snapshot.get("five_element", "WOOD")
        final_duration = _final_duration_for_completion(challenge, manual_check)

        # ⚠️ 2026-09-02: 상태 전환(READY→COMPLETED)과 보상 지급(이벤트·포인트·재료)을
        # 예전엔 서로 다른 트랜잭션으로 나눠서 처리했음. 그래서 상태 전환은 이미 커밋됐는데
        # 그 뒤 포인트/재료 지급 쪽에서 데드락 등으로 실패하면, "완료는 됐는데 보상은 없는"
        # 불일치가 생길 수 있었음. 하나의 트랜잭션으로 묶어서 전부 되거나 전부 안 되게 함.
        try:
            async with in_transaction():
                transitioned = await self.challenge_repo.try_transition(
                    challenge,
                    from_states=[ChallengeState.ACTIVE, ChallengeState.READY, ChallengeState.PAUSED],
                    to_state=ChallengeState.COMPLETED,
                )
                if not transitioned:
                    # 이미 다른 요청이 먼저 상태를 바꿔버린 경우 (동시 완료 요청 경합)
                    raise HTTPException(
                        status_code=status.HTTP_409_CONFLICT,
                        detail="이미 완료되었거나 완료할 수 없는 상태입니다.",
                        headers={"X-Current-State": challenge.state},
                    )
                await self._persist_completion_values(challenge, final_duration, manual_check)
                event = await self.challenge_repo.create_event(
                    challenge_id=challenge.id,
                    event_type=ChallengeEventType.COMPLETE,
                    idempotency_key=idempotency_key,
                    version=challenge.version + 1,
                    occurred_at=occurred_at,
                )
                await self.challenge_repo.create_point_ledger(
                    user_id=user.id,
                    source_event_id=event.id,
                    delta=BASE_POINTS_PER_COMPLETION,
                    element=five_element,
                )
                await self.companion_repo.increment_element(user.id, five_element)
        except HTTPException:
            raise
        except IntegrityError as exc:
            # 정상적인 경합: 거의 동시에 들어온 두 요청 중 하나가 먼저 커밋한 경우
            # (idempotency_key UNIQUE 충돌 등).
            response = await self._recover_from_completion_race(challenge, idempotency_key, user.id, five_element)
            if response is not None:
                return response
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="중복 요청입니다.") from exc
        except OperationalError as exc:
            # ⚠️ 2026-09-03 리뷰 반영: IntegrityError(정상적인 동시 요청 경합)와
            # OperationalError(스키마 깨짐·DB 장애 등)는 성격이 완전히 다른데 같이 잡고
            # 있었음. 실제로 2026-09-02에 겪었던 사고(마이그레이션 12번 미적용으로
            # "Unknown column" 발생)가 여기 OperationalError로 걸려서, 위 IntegrityError용
            # 처리를 그대로 타고 "중복 요청입니다" 409로 뭉개져 로그에 아무것도 안 남았음.
            # 이제 별도로 잡아서 실제 예외를 로그에 남기고, 사용자에게는 "중복"이 아니라
            # 진짜 서버 오류라고 정확히 알림.
            default_logger.exception(
                "challenge complete failed (OperationalError): challenge_id=%s user_id=%s",
                challenge.id,
                user.id,
            )
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="처리 중 오류가 발생했습니다."
            ) from exc

        # ⚠️ 2026-09-03: 함수를 분리하면서(_recover_from_completion_race) 실수로 이 성공
        # 경로의 return 자체가 통째로 빠졌던 버그. try 블록이 예외 없이 끝나면(=완료 처리가
        # 실제로 다 성공하면) 여기 도달해야 하는데, 이게 없어서 트랜잭션은 커밋됐는데(챌린지
        # COMPLETED, 포인트·재료 지급 전부 반영) 응답을 못 만들어서 500이 났음. 그 뒤 재시도가
        # "이미 완료되었거나 완료할 수 없는 상태"로 나온 건 실제로 이미 완료돼 있었기 때문임
        # (완료 자체는 최초 시도에서 이미 성공했었음 — 데이터 유실은 없음).
        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        return CompleteChallengeResponse(
            challenge=ChallengeProgressResponse.model_validate(refreshed),
            points_awarded=BASE_POINTS_PER_COMPLETION,
            five_element=five_element,
        )

    async def _recover_from_completion_race(
        self, challenge, idempotency_key: str, user_id, five_element: str
    ) -> CompleteChallengeResponse | None:
        """⚠️ 2026-09-03 리뷰 반영: complete()의 IntegrityError 처리를 분리 — ruff C901
        (complete()가 너무 복잡하다는 경고) 해소 겸, 실제 완료 결과가 이미 있는지 확인하는
        로직을 재사용 가능하게 뺌. "오류코드 500" 대신 실제 처리 결과를 그대로 돌려줌 —
        안 잡으면 사용자는 완료가 됐는지 안 됐는지조차 알 수 없는 상태로 남게 됨. 결과를
        못 찾으면 None을 돌려주고, 호출부가 409로 처리함.
        """

        existing_event = await self.challenge_repo.get_event_by_idempotency_key(idempotency_key, user_id=user_id)
        if existing_event:
            return await self._build_complete_response(challenge, existing_event)
        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        if refreshed is not None and refreshed.state == ChallengeState.COMPLETED:
            return CompleteChallengeResponse(
                challenge=ChallengeProgressResponse.model_validate(refreshed),
                points_awarded=BASE_POINTS_PER_COMPLETION,
                five_element=five_element,
            )
        return None

    async def skip(self, user: User, challenge_id, reason: str | None, occurred_at=None) -> ChallengeProgressResponse:
        """C19 "오늘 미션 포기" · REST -> GIVE_UP 전환에서 호출.

        ⚠️ 2026-09-08 반영(포기 = 진행값 폐기): 예전엔 state만 SKIPPED로 바꾸고 진행값은
        건드리지 않았음. 그런데 진행값을 확정 저장하는 건 pause()·complete()뿐이라, 결과가
        "포기 전에 일시정지를 눌렀는지"에 따라 갈렸음:
          - 진행 8분 -> 바로 포기        : accumulated=0 (한 번도 확정 저장 안 됨) -> 재도전 시 0초부터
          - 진행 8분 -> 일시정지 -> 포기 : accumulated=480 그대로 -> 재도전 시 8분부터
        같은 "포기"인데 결과가 다른 건 어느 쪽이든 틀린 동작이라, 정책("포기했으면 달성한 건
        없앤다")에 맞춰 두 경로 모두 0으로 초기화하도록 명시함.

        없앤 값은 SKIP 이벤트 payload(achieved_duration_seconds/achieved_count)에 기록으로
        남김 - challenge_events는 append-only라 나중에 리포트에서 "포기 시점에 얼마나 했는지"를
        되짚을 수 있음. challenges 행에서만 지우는 것이지 이력을 지우는 게 아님.
        """

        challenge = await self._get_owned_challenge(user, challenge_id)
        # 상태를 바꾸기 전에 읽어야 함 - SKIPPED가 되고 나면 _effective_duration_seconds()가
        # ACTIVE 구간(started_at 이후)을 더 이상 더하지 않아서 실제 달성치보다 작게 나옴.
        achieved_duration = _effective_duration_seconds(challenge)
        achieved_count = challenge.accumulated_count

        transitioned = await self.challenge_repo.try_transition(
            challenge,
            from_states=[ChallengeState.ACTIVE, ChallengeState.READY, ChallengeState.PAUSED],
            to_state=ChallengeState.SKIPPED,
        )
        if not transitioned:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 종료된 챌린지입니다.")

        await self.challenge_repo.reset_progress(challenge.id)
        payload: dict = {
            "achieved_duration_seconds": achieved_duration,
            "achieved_count": achieved_count,
            "progress_discarded": True,
        }
        if reason:
            payload["reason"] = reason
        await self.challenge_repo.create_event(
            challenge_id=challenge.id,
            event_type=ChallengeEventType.SKIP,
            idempotency_key=f"skip:{challenge.id}:{challenge.version + 1}",
            version=challenge.version + 1,
            payload=payload,
            occurred_at=occurred_at,
        )
        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        return ChallengeProgressResponse.model_validate(refreshed)

    async def _persist_completion_values(self, challenge, final_duration: int, manual_check: bool) -> None:
        """complete()에서 분리 - 완료 시점의 최종 시간/개수를 확정 저장."""

        if challenge.target_duration_seconds is not None:
            await self.challenge_repo.set_final_duration(challenge.id, final_duration)
        # ⚠️ 2026-09-08 반영: manual_check로 들어온 COUNT형(target_count가 있는
        # SENSOR_STEPS 등)도 실측치가 목표에 못 미친 채로 남아있으면 이후 조회에서
        # "9/10칸"처럼 미완성 수치가 계속 보임 - 목표치로 채워서 확정.
        if manual_check and challenge.target_count is not None:
            await self.challenge_repo.set_final_count(challenge.id, challenge.target_count)

    async def _get_owned_challenge(self, user: User, challenge_id):
        challenge = await self.challenge_repo.get_by_id(challenge_id)
        if challenge is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="챌린지를 찾을 수 없습니다.")
        owner_id_check = await challenge.selection
        card_set = await owner_id_check.card_set
        if card_set.user_id != user.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="본인의 챌린지만 접근할 수 있습니다.")
        return challenge

    async def _build_complete_response(self, challenge, event) -> CompleteChallengeResponse:
        ledger_entry = await event.point_ledger_entry
        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        return CompleteChallengeResponse(
            challenge=ChallengeProgressResponse.model_validate(refreshed),
            points_awarded=ledger_entry.delta if ledger_entry else 0,
            five_element=ledger_entry.element if ledger_entry else "",
        )
