from datetime import UTC, datetime

from fastapi import HTTPException, status
from tortoise.exceptions import IntegrityError, OperationalError
from tortoise.transactions import in_transaction

from app.core.logger import default_logger
from app.dtos.challenges import ChallengeProgressResponse, CompleteChallengeResponse
from app.models.challenges import ChallengeEventType, ChallengeState
from app.models.users import User
from app.repositories.challenge_repository import ChallengeRepository
from app.repositories.companion_repository import CompanionRepository

BASE_POINTS_PER_COMPLETION = 10  # 기능명세서 §8.1: "모든 P0 완료는 속성과 관계없이 동일 기본 포인트를 사용"

# CHECK는 사용자가 직접 완료 버튼을 눌러서 끝내는 방식이라 서버가 검증할 측정값이 없음.
# 나머지는 전부 target_duration_seconds 또는 target_count 중 하나로 목표가 정해져 있음.
NO_VALIDATION_EXEC_TYPES = {"CHECK"}


def _effective_duration_seconds(challenge) -> int:
    """⚠️ 2026-09-01 수정: 예전엔 accumulated_duration_seconds 컬럼값만 봤는데, 이 값은
    "일시정지했던 시점까지"만 반영되고 지금 ACTIVE로 흐르고 있는 구간은 안 들어있었음
    (모델 docstring엔 원래 이렇게 계산하기로 되어 있었는데 실제 코드가 안 따라가고 있었음).
    ACTIVE 상태면 "지금까지 쌓인 것 + (지금 - started_at)"까지 더해서 실제 경과 시간을 계산.

    ⚠️ 2026-09-06 반영(naive/aware 방어): started_at을 저장할 때는 datetime.now(UTC)
    (타임존 있음)를 쓰는데, challenges.started_at 필드 자체엔 타임존 관련 옵션이 없어서
    DB가 naive를 돌려주거나 다른 타임존으로 잘못 라벨링해서 돌려줄 수 있음. 여기서 명시적으로
    UTC로 맞춘 뒤에만 계산해서, DB가 뭘 돌려주든 항상 저장했던 실제 시각(UTC) 기준으로
    일관되게 계산되게 함.

    ⚠️ 2026-09-06 반영(목표치 상한): 시작해두고 뒤로가기만 한 채 오래 방치하면(서버엔
    일시정지가 전달 안 됨) ACTIVE 상태가 며칠이고 유지되면서 경과 시간이 끝없이 커짐
    (예: 9/4 새벽에 시작한 걸 9/6에 열어보니 45시간짜리로 나온 사례). "시작/이어하기 ~
    일시정지/완료 전까지는 계속 측정 중이지만, 목표치에 도달하면 그 값에서 멈춘다"는
    방향에 맞춰 target_duration_seconds가 있으면 그 값을 상한으로 고정 - 앱을 며칠 안
    열어봐도 항상 목표치를 넘지 않는 값만 보이게 됨.
    """

    base = challenge.accumulated_duration_seconds
    if challenge.state == ChallengeState.ACTIVE and challenge.started_at is not None:
        started_at = challenge.started_at
        if started_at.tzinfo is None:
            # DB가 naive로 돌려준 경우 - 저장할 때 UTC로 만들어서 넣었으므로 그대로 UTC로 라벨링.
            started_at = started_at.replace(tzinfo=UTC)
        else:
            # 이미 aware인데 다른 타임존(Asia/Seoul 등)으로 잘못 라벨링돼 있을 수 있으니 UTC로 정규화.
            started_at = started_at.astimezone(UTC)
        now = datetime.now(UTC)
        base += int((now - started_at).total_seconds())
    if challenge.target_duration_seconds is not None:
        base = min(base, challenge.target_duration_seconds)
    return base


def _describe_shortfall(challenge) -> str:
    """목표 미달성 시 "무엇이 얼마나 부족한지" 사람이 읽을 수 있는 문구로 설명."""

    if challenge.target_duration_seconds is not None:
        current = _effective_duration_seconds(challenge)
        remaining = challenge.target_duration_seconds - current
        return (
            f"목표 시간에 아직 도달하지 못했습니다. "
            f"{current}초 / {challenge.target_duration_seconds}초 "
            f"(남은 시간 {max(remaining, 0)}초)"
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
    if challenge.target_duration_seconds is not None:
        return _effective_duration_seconds(challenge) >= challenge.target_duration_seconds
    if challenge.target_count is not None:
        return challenge.accumulated_count >= challenge.target_count
    # 목표값 자체가 없는 예외적인 경우 - 막지 않고 통과 (설계상 있으면 안 되지만 방어적으로 처리)
    return True


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
        now = datetime.now(UTC)

        transitioned = await self.challenge_repo.try_transition(
            challenge, from_states=[ChallengeState.READY, ChallengeState.PAUSED], to_state=ChallengeState.ACTIVE
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
        return ChallengeProgressResponse.model_validate(refreshed)

    async def pause(self, user: User, challenge_id) -> ChallengeProgressResponse:
        """C03의 "일시정지" 버튼 - 지금까지 흐른 시간을 accumulated_duration_seconds에
        더해 넣고 멈춤. TIMER/SENSOR_*_DURATION 타입에서만 의미 있음(CHECK·카운터형은
        애초에 이 버튼이 화면에 없음)."""

        challenge = await self._get_owned_challenge(user, challenge_id)
        elapsed = _effective_duration_seconds(challenge) - challenge.accumulated_duration_seconds

        transitioned = await self.challenge_repo.try_transition(
            challenge, from_states=[ChallengeState.ACTIVE], to_state=ChallengeState.PAUSED
        )
        if not transitioned:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="일시정지할 수 없는 상태입니다.",
                headers={"X-Current-State": challenge.state},
            )

        await self.challenge_repo.add_accumulated_duration_and_clear_start(challenge.id, elapsed)
        await self.challenge_repo.create_event(
            challenge_id=challenge.id,
            event_type=ChallengeEventType.PAUSE,
            idempotency_key=f"pause:{challenge.id}:{challenge.version + 1}",
            version=challenge.version + 1,
        )
        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        return ChallengeProgressResponse.model_validate(refreshed)

    async def complete(
        self, user: User, challenge_id, idempotency_key: str, occurred_at=None
    ) -> CompleteChallengeResponse:
        """문서 §4 "중복 보상이 구조적으로 불가능한 이유"를 그대로 구현:
        1) idempotency_key UNIQUE로 이벤트 중복 생성을 막고
        2) source_event_id UNIQUE로 포인트 중복 적립을 막는다.
        앱이 네트워크 오류로 같은 요청을 여러 번 보내도, 최초 1번의 결과가 그대로 반환된다.

        occurred_at: 오프라인 상태에서 실제로 완료한 시각(클라이언트가 앎).
        HANDOFF.md §3.3 "기록 시각은 업로드 시각이 아니라 실제 완료 시각" 반영.
        안 보내면 None -> ChallengeEvent.server_at(서버 수신 시각)으로 자동 대체됨.
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

        if not _is_goal_achieved(challenge):
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=_describe_shortfall(challenge),
            )

        five_element = challenge.mission_snapshot.get("five_element", "WOOD")
        final_duration = _effective_duration_seconds(
            challenge
        )  # 완료 순간까지 흐른 진짜 시간(ACTIVE 상태였으면 계산해서 확정)

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
                if challenge.target_duration_seconds is not None:
                    await self.challenge_repo.set_final_duration(challenge.id, final_duration)
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
        challenge = await self._get_owned_challenge(user, challenge_id)

        transitioned = await self.challenge_repo.try_transition(
            challenge,
            from_states=[ChallengeState.ACTIVE, ChallengeState.READY, ChallengeState.PAUSED],
            to_state=ChallengeState.SKIPPED,
        )
        if not transitioned:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 종료된 챌린지입니다.")

        await self.challenge_repo.create_event(
            challenge_id=challenge.id,
            event_type=ChallengeEventType.SKIP,
            idempotency_key=f"skip:{challenge.id}:{challenge.version + 1}",
            version=challenge.version + 1,
            payload={"reason": reason} if reason else None,
            occurred_at=occurred_at,
        )
        refreshed = await self.challenge_repo.get_by_id(challenge.id)
        return ChallengeProgressResponse.model_validate(refreshed)

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
