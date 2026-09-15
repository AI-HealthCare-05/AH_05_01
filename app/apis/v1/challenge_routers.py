from datetime import datetime
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel

from app.dependencies.security import get_request_user
from app.dtos.cards import CardRevealResponse
from app.dtos.challenges import ChallengeProgressResponse, CompleteChallengeResponse, SkipChallengeRequest
from app.models.users import User
from app.services.card_service import CardService
from app.services.challenge_service import ChallengeService

challenge_router = APIRouter(prefix="/challenges", tags=["challenges"])


class CompleteChallengeRequest(BaseModel):
    """body 자체는 선택 — 예전처럼 헤더(Idempotency-Key)만 보내는 클라이언트도 그대로 동작함.
    occurred_at을 보내면 그 시각이 "실제 완료 시각"으로 저장됨 (HANDOFF.md §3.3)."""

    occurred_at: datetime | None = None
    # ⚠️ 2026-09-08 반영: SENSOR형 미션에서 "직접 체크로 할래요"(센서 권한 없음·측정 실패
    # 등으로 자동 측정 대신 사용자가 스스로 확인하는 대체 경로)를 눌러도, 서버는 그
    # 챌린지의 진짜 exec_type(SENSOR_*)을 기준으로 계속 실측값(accumulated_count 등)을
    # 요구하고 있었음 - 직접 체크로는 절대 목표 달성이 안 되는 구조였음(화면만 CHECK
    # 완료 화면으로 보내고, 서버는 그 사실을 몰랐음). 이 플래그가 true면 CHECK형과 같이
    # "사용자 확인 자체가 완료 조건"으로 취급 - 실측값 검증을 건너뜀.
    manual_check: bool = False


@challenge_router.get("/{challenge_id}", response_model=ChallengeProgressResponse, status_code=status.HTTP_200_OK)
async def get_challenge(
    challenge_id: UUID,
    user: Annotated[User, Depends(get_request_user)],
    challenge_service: Annotated[ChallengeService, Depends(ChallengeService)],
) -> ChallengeProgressResponse:
    return await challenge_service.get_progress(user, challenge_id)


@challenge_router.post(
    "/{challenge_id}/start", response_model=ChallengeProgressResponse, status_code=status.HTTP_200_OK
)
async def start_challenge(
    challenge_id: UUID,
    user: Annotated[User, Depends(get_request_user)],
    challenge_service: Annotated[ChallengeService, Depends(ChallengeService)],
) -> ChallengeProgressResponse:
    """C02/C09 "시작하기"·"측정 시작" — 여기부터 서버가 READY(또는 PAUSED) -> ACTIVE로
    바꾸고 시간을 재기 시작함. 카드만 확정하고 이 호출이 없으면 서버는 계속 READY."""

    return await challenge_service.start(user, challenge_id)


@challenge_router.post(
    "/{challenge_id}/pause", response_model=ChallengeProgressResponse, status_code=status.HTTP_200_OK
)
async def pause_challenge(
    challenge_id: UUID,
    user: Annotated[User, Depends(get_request_user)],
    challenge_service: Annotated[ChallengeService, Depends(ChallengeService)],
) -> ChallengeProgressResponse:
    """C03 "일시정지"."""

    return await challenge_service.pause(user, challenge_id)


@challenge_router.get("/{challenge_id}/reveal", response_model=CardRevealResponse, status_code=status.HTTP_200_OK)
async def reveal_challenge(
    challenge_id: UUID,
    user: Annotated[User, Depends(get_request_user)],
    card_service: Annotated[CardService, Depends(CardService)],
) -> CardRevealResponse:
    """F: "미션 이어하기" - 앱을 껐다 켜도(또는 B01b에서 다시 눌러도) B06과 같은 카드 내용을
    다시 보여줌. select_option 응답과 완전히 같은 포맷(재계산이라 매번 값이 같음)."""

    return await card_service.get_reveal_for_challenge(user, challenge_id)


@challenge_router.post(
    "/{challenge_id}/complete", response_model=CompleteChallengeResponse, status_code=status.HTTP_200_OK
)
async def complete_challenge(
    challenge_id: UUID,
    user: Annotated[User, Depends(get_request_user)],
    challenge_service: Annotated[ChallengeService, Depends(ChallengeService)],
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
    request: CompleteChallengeRequest | None = None,
) -> CompleteChallengeResponse:
    """Idempotency-Key 헤더 필수. 같은 키로 재요청해도 중복 보상되지 않음 (문서 §4).
    body의 occurred_at은 선택 — 오프라인 상태에서 완료했다가 나중에 동기화하는 경우에 씀."""

    if not idempotency_key:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Idempotency-Key 헤더가 필요합니다.")
    occurred_at = request.occurred_at if request else None
    manual_check = request.manual_check if request else False
    return await challenge_service.complete(
        user, challenge_id, idempotency_key, occurred_at=occurred_at, manual_check=manual_check
    )


@challenge_router.post("/{challenge_id}/skip", response_model=ChallengeProgressResponse, status_code=status.HTTP_200_OK)
async def skip_challenge(
    challenge_id: UUID,
    request: SkipChallengeRequest,
    user: Annotated[User, Depends(get_request_user)],
    challenge_service: Annotated[ChallengeService, Depends(ChallengeService)],
) -> ChallengeProgressResponse:
    return await challenge_service.skip(user, challenge_id, request.reason, occurred_at=request.occurred_at)
