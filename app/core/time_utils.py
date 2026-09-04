"""서비스 전역에서 "오늘"을 판정할 때 쓰는 공용 헬퍼.

⚠️ 2026-09-03 리뷰 반영: record_service.py·card_routers.py 여러 곳에서 date.today()를
그대로 썼는데, 이건 서버 프로세스의 로컬 시간대 기준임. 컨테이너가 UTC로 뜨면(도커·클라우드
기본값) 00:00~09:00 KST 사이엔 서버가 "어제"라고 판단해서, 그 시간대 사용자는 어제 카드를
다시 받고 쉼 주간·캘린더 "미래 날짜" 판정도 하루씩 밀리는 문제가 있었음.
consent_repository.py·health_repository.py는 이미 datetime.now(config.TIMEZONE)을 쓰고
있어서, 그 패턴을 "오늘 날짜"가 필요한 곳에서도 그대로 재사용하도록 여기 하나로 모음.

⚠️ 2026-09-04 추가: 테스트용 "날짜 시뮬레이션" - 하루에 미션 1개 제한 때문에 실제로
미션 10개를 테스트하려면 10일이 걸림. 이 오프셋을 하루씩 늘려가며 카드·연속기록·캘린더
전체가 "오늘"로 일관되게 인식하는 날을 앞당겨서, 하루씩 실제로 기다리지 않고도 여러 날의
미션 흐름을 이어서 테스트할 수 있게 함. 서버 프로세스 메모리에만 있어서 재시작하면
0으로 초기화되고, DB에는 전혀 안 남음. app/apis/v1/debug_routers.py에서만 건드리고,
그 라우터 자체가 PROD에서는 404로 완전히 막혀 있음(운영에서는 절대 손댈 수 없음).
"""

from datetime import date, datetime, timedelta

from app.core import config

_debug_day_offset = 0


def service_today() -> date:
    """KST(config.TIMEZONE) 기준 오늘 날짜(+ 테스트용 날짜 시뮬레이션 오프셋). date.today()
    대신 이걸 쓸 것."""

    return datetime.now(config.TIMEZONE).date() + timedelta(days=_debug_day_offset)


def _set_debug_day_offset(offset: int) -> int:
    """⚠️ 테스트 전용 - app/apis/v1/debug_routers.py(PROD에서 404로 차단됨)에서만 호출할 것."""

    global _debug_day_offset
    _debug_day_offset = offset
    return _debug_day_offset


def _get_debug_day_offset() -> int:
    return _debug_day_offset
