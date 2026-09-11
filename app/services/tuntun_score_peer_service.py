"""틈튼지수 vNext(또래 백분위) 서비스.

⚠️ 2026-09-09 추가 — LOCAL_REVIEW_CANDIDATE. 별도 Python 3.14.7 프로세스(모델
브릿지, tuntun_peer_bridge/)에 HTTP로 요청해서 실제 모델 추론 결과를 받아온다.
기존 tuntun_score_service.py(TuntunScoreService)는 전혀 건드리지 않고, 이 서비스는
완전히 별개다 - 기존 /tuntun-score, /tuntun-score/v2는 계속 Mock으로 남는다.

⚠️ 브릿지가 안 떠 있으면(로컬 개발 중 docker compose에 이 서비스를 안 띄웠거나,
아직 검토 안 끝나 운영에 배포 안 한 경우) 503을 그대로 반환한다 - 조용히 Mock으로
폴백하지 않는다. "모델이 실제로 응답했는지"와 "서버가 그냥 기본값을 보여주는지"를
클라이언트가 구분할 수 있어야 한다는 게 원본 패키지 README의 핵심 요구사항
("모델 오류를 고정 80점으로 바꾸는 fallback은 없습니다").
"""

import uuid

import httpx
from fastapi import HTTPException, status

from app.core import config
from app.core.time_utils import service_today
from app.models.users import User
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.repositories.health_repository import HealthInputRepository

SCHEMA_VERSION = "tuntun-app-vnext-v0.2"


def _pregnancy_status(user: User) -> str:
    if user.gender == "MALE":
        return "not_applicable"
    if user.gender == "FEMALE" and user.is_pregnant is True:
        return "pregnant"
    if user.gender == "FEMALE" and user.is_pregnant is False:
        return "nonpregnant"
    return "unknown"  # 여성인데 아직 안 물어봤거나(None), 성별 자체가 없는 경우


class TuntunScorePeerService:
    def __init__(self):
        self.health_repo = HealthInputRepository()
        self.exercise_repo = ExerciseHabitRepository()

    async def get_score(self, user: User) -> dict:
        if not config.TUNTUN_PEER_BRIDGE_URL:
            # ⚠️ 운영에는 이 값을 절대 채우지 않음(브릿지 자체가 PRODUCTION_RELEASE_GATE:
            # BLOCKED 상태의 검토용 패키지). 로컬 개발 중 docker compose로 브릿지를 띄웠을
            # 때만 config.TUNTUN_PEER_BRIDGE_URL이 채워짐(.env).
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="TUNTUN_PEER_BRIDGE_NOT_CONFIGURED",
            )

        health = await self.health_repo.get_latest(user.id)
        habit = await self.exercise_repo.get_latest(user.id)
        input_values = (health.input_values or {}) if health else {}
        reference_date = service_today(user.id)

        # ⚠️ strengthFrequencyUnit=days는 팀 확인 완료(2026-09-09) - 앱의 "주 N회"는
        # 실제로 운동한 일수를 뜻함. tuntun_score_service.py의 기존 Mock 계산에서도
        # 이미 이 값을 strength_days_week로 취급하고 있었음(같은 전제 재확인).
        body = {
            "birthYear": user.birth_year,
            "birthMonth": user.birth_month,
            "sex": "male" if user.gender == "MALE" else "female" if user.gender == "FEMALE" else None,
            "pregnancyStatus": _pregnancy_status(user),
            "heightCm": input_values.get("height_cm"),
            "weightKg": input_values.get("weight_kg"),
            "strengthWeeklyCount": habit.strength_weekly_count if habit else None,
            "strengthFrequencyUnit": "days" if habit and habit.strength_weekly_count is not None else None,
            "strengthIntensity": (
                {"LIGHT": "light", "MODERATE": "moderate", "HARD": "hard"}.get(str(habit.strength_intensity))
                if habit and habit.strength_intensity else None
            ),
            "aerobicLowMinutes": habit.aerobic_low_minutes if habit else None,
            "aerobicModerateMinutes": habit.aerobic_moderate_minutes if habit else None,
            "aerobicVigorousMinutes": habit.aerobic_high_minutes if habit else None,
            "bedtime": None,  # ⚠️ 앱에 아직 취침/기상 입력 화면이 없음 - 나중에 생기면 채움
            "wakeTime": None,
            "referenceDate": reference_date.isoformat(),
            # ⚠️ 매 요청마다 새 ID - "저장 시 새 opaque ID 발행"이 원칙이지만, 이 라우트는
            # 아직 저장 없이 즉시 조회만 하므로 요청 시점 UUID로 대체. 실제 화면에 연결할
            # 때는 입력을 저장하는 시점에 발급한 ID를 그대로 써야 함(캐시 무효화 목적).
            "inputRevision": uuid.uuid4().hex,
        }

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(
                    f"{config.TUNTUN_PEER_BRIDGE_URL}/score/peer/v2",
                    json=body,
                    headers={"X-Tuntun-Schema": SCHEMA_VERSION, "Content-Type": "application/json"},
                )
        except httpx.HTTPError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="TUNTUN_PEER_BRIDGE_UNREACHABLE"
            ) from exc

        # ⚠️ 브릿지가 준 상태코드를 그대로 전달 - 409(스키마 불일치)/422(입력 오류)/
        # 503(모델 실행 오류) 각각 다른 의미라 200으로 뭉개지 않음(README §3).
        if response.status_code != 200:
            raise HTTPException(status_code=response.status_code, detail=response.json())
        return response.json()
