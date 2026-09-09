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
미션 흐름을 이어서 테스트할 수 있게 함. DB에는 전혀 안 남음. app/apis/v1/debug_routers.py에서만
건드리고, 그 라우터 자체가 PROD에서는 404로 완전히 막혀 있음(운영에서는 절대 손댈 수 없음).

⚠️ 2026-09-07 QA(N3) 반영: 원래 이 오프셋이 프로세스 메모리(전역 변수)에만 있었음 - 문서화된
대로 "재시작하면 0으로 초기화"인데, dev 서버가 `uvicorn --reload`로 뜨는 게 문제였음. 파일을
하나만 고쳐도 오토리로드가 프로세스를 재기동시켜서 오프셋이 조용히 0으로 리셋됐고, 그 사이
카드/챌린지는 이미 시뮬레이션 날짜(예: 2026-10-29)로 만들어진 채로 DB에 남아있어서 —
카드·챌린지 API가 보는 "오늘"과 mark_rest_day가 보는 "오늘"이 어긋나 "미래 날짜는 쉼으로
표시할 수 없습니다"로 거부되는 게 재현됐음. 임시 파일에 오프셋을 같이 적어둬서 프로세스가
재시작돼도(오토리로드 포함) 같은 값을 이어서 읽게 함 - DB 스키마 변경 없음.

⚠️ 2026-09-08 QA(N3/N4) 반영: 위 파일 저장까지 했어도 오프셋이 "서버 전체 공유 값 1개"였던
게 진짜 문제였음 - 여러 사람이 같은 dev 서버로 QA하면 한 명이 "다음 날"을 누르는 순간
다른 모든 계정의 "오늘"이 같이 밀려버렸음(리포트: "다음 날을 처음 눌렀는데 +10일이었다" -
이전 세션이 이미 9번 눌러둔 값 위에 한 번 더 누른 것). 계정(user_id)별로 오프셋을 따로
관리하도록 바꿈 - 값 자체는 여전히 DB가 아니라 임시 파일(JSON 딕셔너리)에만 있어서
"DB에는 전혀 안 남는다"는 원래 원칙은 그대로 유지됨.
"""

import json
import tempfile
from datetime import date, datetime, timedelta
from pathlib import Path

from app.core import config

# ⚠️ 여러 프로세스(예: 오토리로드 전/후, 또는 API·워커가 따로 뜬 경우)가 같은 값을 보게
# 하려는 용도일 뿐, 이 값 자체가 서비스 데이터인 적은 없음(디버그 전용, PROD 미도달).
# user_id(문자열 키, JSON 제약) -> offset(일 단위 정수)의 딕셔너리.
_DEBUG_DAY_OFFSET_FILE = Path(tempfile.gettempdir()) / "tmtn_debug_day_offset.json"


def _load_debug_day_offsets_from_disk() -> dict[str, int]:
    try:
        raw = json.loads(_DEBUG_DAY_OFFSET_FILE.read_text())
        # ⚠️ 계정별로 바뀌기 전(2026-09-07까지) 남아있던 예전 파일 형식({"offset": N})은
        # 무시하고 빈 값으로 시작함 - 그 값이 "누구의" 오프셋이었는지 알 방법이 없어서, 섞어
        # 쓰면 또 "남이 남긴 값"을 보는 예전 버그가 재현됨.
        if isinstance(raw, dict) and "offset" not in raw:
            return {str(k): int(v) for k, v in raw.items()}
        return {}
    except (OSError, ValueError, KeyError, TypeError):
        return {}


def _persist_debug_day_offsets_to_disk(offsets: dict[str, int]) -> None:
    try:
        _DEBUG_DAY_OFFSET_FILE.write_text(json.dumps(offsets))
    except OSError:
        pass  # 디스크에 못 써도(권한 등) 이번 프로세스가 살아있는 동안은 메모리 값으로 계속 동작


# 모듈이 (오토리로드 등으로) 새로 로드될 때 이전 프로세스가 남겨둔 값을 이어받음.
_debug_day_offsets: dict[str, int] = _load_debug_day_offsets_from_disk()


def service_today(user_id: int | None = None) -> date:
    """KST(config.TIMEZONE) 기준 오늘 날짜(+ 테스트용 날짜 시뮬레이션 오프셋).
    date.today() 대신 이걸 쓸 것.

    user_id를 안 넘기면(또는 그 계정이 오프셋을 설정한 적이 없으면) 오프셋 0 - 실제 오늘.
    """

    offset = _debug_day_offsets.get(str(user_id), 0) if user_id is not None else 0
    return datetime.now(config.TIMEZONE).date() + timedelta(days=offset)


def _set_debug_day_offset(user_id: int, offset: int) -> int:
    """⚠️ 테스트 전용 - app/apis/v1/debug_routers.py(PROD에서 404로 차단됨)에서만 호출할 것."""

    _debug_day_offsets[str(user_id)] = offset
    _persist_debug_day_offsets_to_disk(_debug_day_offsets)
    return offset


def _get_debug_day_offset(user_id: int) -> int:
    return _debug_day_offsets.get(str(user_id), 0)
