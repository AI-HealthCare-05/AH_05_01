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

import httpx
from fastapi import HTTPException, status

from app.core import config
from app.core.time_utils import service_today
from app.models.accounts import ConsentPurpose, ConsentStatus
from app.models.assessments import TmtnIndexResult
from app.models.users import User
from app.repositories.consent_repository import ConsentRepository
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
        self.consent_repo = ConsentRepository()

    async def get_score(self, user: User) -> dict:
        result, _ = await self.fetch_bridge_result(user)
        return result

    async def fetch_bridge_result(self, user: User) -> tuple[dict, str]:
        """⚠️ 2026-09-15 리팩토링 - practice_score_service.py(틈튼지수 종합점수 조합)가
        건강 영역(physical/diabetes/hypertension) 원본 components[]를 재사용할 수 있게
        get_score()에서 브릿지 호출 부분만 뽑아냄. 반환값: (브릿지 원본 응답 dict, 이번
        조회에서 쓴 input_revision). 기존 get_score()의 동작(동의 체크, 503/409/422 전달,
        TmtnIndexResult 기록)은 그대로 유지 - 이 메서드도 내부에서 get_score()와 똑같이
        전부 수행함(중복 호출 방지를 위해 결과를 캐싱하지는 않음 - 같은 요청 내에서
        여러 번 부르면 브릿지를 그만큼 여러 번 호출하니, 호출부에서 한 번만 부를 것).
        """
        # ⚠️ 2026-09-12 추가 - "틈튼지수 산출을 위한 분석"(HEALTH_REFERENCE_ANALYSIS,
        # 선택 동의)을 거부한 사용자는 이 분석 자체를 돌리면 안 됨. 위치정보 동의를 이미
        # 같은 방식으로 막고 있던 것과 같은 원칙 - 동의 화면에 항목만 있고 실제로는 안
        # 막던 걸 QA로 발견해서 고침.
        consent = await self.consent_repo.get_latest_by_purpose(user.id, ConsentPurpose.HEALTH_REFERENCE_ANALYSIS)
        if consent is None or consent.status != ConsentStatus.AGREED:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="HEALTH_REFERENCE_ANALYSIS_NOT_AGREED",
            )

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

        # ⚠️ 2026-09-15 버그 수정(Q5 - 문홍주 팀장님 요청) - 예전엔 요청마다 새 무작위
        # UUID를 썼는데, 이러면 "입력이 실제로 바뀌었는지"를 전혀 추적 못 함(캐시 무효화
        # 불가능). health_input_snapshots/exercise_habit_snapshots가 둘 다 append-only라
        # 각 스냅샷의 id 자체가 이미 "그 시점의 입력 상태"를 고유하게 식별함 - 별도 필드
        # 없이 두 스냅샷 id를 조합하는 것만으로 "저장 시점에 발급된 revision"과 동일한
        # 효과. 신체정보나 운동습관 둘 중 하나라도 새로 저장되면(새 스냅샷 생성) 조합값이
        # 자동으로 바뀌고, 둘 다 그대로면 몇 번을 조회해도 같은 값이 나옴.
        input_revision = f"{health.id if health else 'none'}:{habit.id if habit else 'none'}"

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
                if habit and habit.strength_intensity
                else None
            ),
            "aerobicLowMinutes": habit.aerobic_low_minutes if habit else None,
            "aerobicModerateMinutes": habit.aerobic_moderate_minutes if habit else None,
            "aerobicVigorousMinutes": habit.aerobic_high_minutes if habit else None,
            "bedtime": None,  # ⚠️ 앱에 아직 취침/기상 입력 화면이 없음 - 나중에 생기면 채움
            "wakeTime": None,
            "referenceDate": reference_date.isoformat(),
            "inputRevision": input_revision,
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
        result = response.json()

        # ⚠️ 2026-09-15 추가 - 문홍주 팀장님(SHAP/XAI) 요청: "전후 비교는 model/
        # calibration/input(aggregation)/background/explainer 5종 버전이 모두 같을
        # 때만" - 나중에 "지난주 대비" 기능을 만들 때 이 기록에서 버전을 비교할 수
        # 있게, 매 조회마다 결과를 버전 정보와 함께 남겨둠. 지금 이 API(/score/peer/v2)
        # 응답엔 modelVersion·formulaVersion 2개만 있고 나머지 3개(calibration,
        # aggregation/input, background+explainer)는 모델 쪽에서 아직 안 내려줘서
        # null로 남음 - 값이 채워지기 전까지 이 기록으로 전후 비교 기능을 만들면 안 됨.
        await TmtnIndexResult.create(
            user_id=user.id,
            source_result_ids=[],  # vNext는 assessment_results를 안 거침(브릿지 직접 호출)
            display_state="VISIBLE",
            composite_formula_version=result.get("formulaVersion", ""),
            reference_date=reference_date,
            peer_input_revision=input_revision,
            peer_model_version=result.get("modelVersion"),
            peer_calibration_version=None,  # 모델 쪽 확인 불가 상태(model_contract.json)
            peer_aggregation_version=None,  # input_revision 채번 주체 미정(Q5)
            peer_background_id=None,  # /score/peer/v2 응답엔 없음 - SHAP 설명 API에만 있음
            peer_explainer_version=None,
        )

        return result, input_revision
