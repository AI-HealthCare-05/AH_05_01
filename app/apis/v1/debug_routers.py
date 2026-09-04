"""테스트 전용 - "오늘"을 앞당겨서 미션 여러 개를 하루씩 기다리지 않고 이어서 테스트하기 위함.

⚠️ 이 라우터는 PROD에서 완전히 차단된다(모든 엔드포인트가 404). 서버 프로세스 메모리에만
있는 값이라 재시작하면 0으로 초기화되고, DB에는 전혀 안 남는다. 실제 날짜(datetime.now)는
안 건드리고, app.core.time_utils.service_today()가 돌려주는 값에만 오프셋을 더한다 -
로그·JWT 만료 시각 등 다른 모든 시스템 시각은 정상적인 실제 시각 그대로 흐른다.
"""

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.core import config
from app.core.config import Env
from app.core.time_utils import _get_debug_day_offset, _set_debug_day_offset, service_today
from app.dependencies.security import get_request_user
from app.models.users import User

debug_router = APIRouter(prefix="/debug", tags=["debug (테스트 전용, PROD 차단)"])


def _reject_in_prod() -> None:
    if config.ENV == Env.PROD:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not Found")


@debug_router.post("/advance-day", status_code=status.HTTP_200_OK)
async def advance_day(_user: Annotated[User, Depends(get_request_user)]) -> dict:
    """ "오늘"을 하루 앞당김. 카드·연속기록·캘린더·틈튼지수 기간이 전부 이 새 날짜
    기준으로 일관되게 동작함(새 daily_card_set이 그 날짜로 새로 생성됨)."""

    _reject_in_prod()
    offset = _set_debug_day_offset(_get_debug_day_offset() + 1)
    return {"debug_day_offset": offset, "simulated_today": service_today().isoformat()}


@debug_router.post("/reset-day", status_code=status.HTTP_200_OK)
async def reset_day(_user: Annotated[User, Depends(get_request_user)]) -> dict:
    """시뮬레이션한 날짜를 실제 오늘로 되돌림."""

    _reject_in_prod()
    offset = _set_debug_day_offset(0)
    return {"debug_day_offset": offset, "simulated_today": service_today().isoformat()}


@debug_router.get("/current-day", status_code=status.HTTP_200_OK)
async def get_current_day(_user: Annotated[User, Depends(get_request_user)]) -> dict:
    _reject_in_prod()
    return {"debug_day_offset": _get_debug_day_offset(), "simulated_today": service_today().isoformat()}
