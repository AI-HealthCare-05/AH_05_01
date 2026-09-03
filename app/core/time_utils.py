"""서비스 전역에서 "오늘"을 판정할 때 쓰는 공용 헬퍼.

⚠️ 2026-09-03 리뷰 반영: record_service.py·card_routers.py 여러 곳에서 date.today()를
그대로 썼는데, 이건 서버 프로세스의 로컬 시간대 기준임. 컨테이너가 UTC로 뜨면(도커·클라우드
기본값) 00:00~09:00 KST 사이엔 서버가 "어제"라고 판단해서, 그 시간대 사용자는 어제 카드를
다시 받고 쉼 주간·캘린더 "미래 날짜" 판정도 하루씩 밀리는 문제가 있었음.
consent_repository.py·health_repository.py는 이미 datetime.now(config.TIMEZONE)을 쓰고
있어서, 그 패턴을 "오늘 날짜"가 필요한 곳에서도 그대로 재사용하도록 여기 하나로 모음.
"""

from datetime import date, datetime

from app.core import config


def service_today() -> date:
    """KST(config.TIMEZONE) 기준 오늘 날짜. date.today() 대신 이걸 쓸 것."""

    return datetime.now(config.TIMEZONE).date()
