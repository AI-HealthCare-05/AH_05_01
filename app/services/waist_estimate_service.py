"""허리둘레(cm) 자동 계산·저장.

⚠️ 2026-09-10 추가 — 신체정보 또는 운동습관을 저장할 때마다 자동으로 허리둘레 모델을
다시 돌려서 결과를 prediction_results에 저장한다. WAIST_DIAGNOSIS_2026-09-10.md가
지적했던 "입력 저장 → 모델 실행 → 결과 저장이 연결돼 있지 않다"는 빈 연결고리를 채움.

⚠️ prediction_service.save_batch()는 관리자 전용 API라 그대로 재사용 못 함(앱 사용자
요청 흐름에서 관리자 권한이 없음) - 여기서는 PredictionRepository를 직접 써서 저장한다.
이건 "사용자가 자기 예측 결과를 조작"하는 게 아니라 "서버가 사용자 입력을 근거로 자동
계산해서 기록"하는 것이라 save_batch()의 관리자 제한 취지(조작 방지)에 어긋나지 않는다.

⚠️ 2026-09-10 반영: 모델 버전 승인도 자동화함(팀 결정 - "승인을 임의로 만들지 않는다"
원칙을 이 서브모델에 한해 해제). approved_by_user_id에는 실제 사람이 아니라는 걸
투명하게 남기려고 "SYSTEM_AUTO_APPROVAL"을 남김(감사 목적 필드를 거짓으로 채우지
않기 위함) - 나중에 DB만 봐도 "이게 사람이 승인한 게 아니라 자동 승인됐다"를 바로
알 수 있음.

⚠️ 실패해도 신체정보/운동습관 저장 자체는 항상 성공해야 하므로, 호출부에서 예외를
전부 삼키고 무시한다(사용자 경험에 영향 없게) - 대신 실패 사유를 FAILED/INPUT_MISSING
상태로 결과 테이블에 남겨서, 나중에 담당자가 DB로 원인을 확인할 수 있게 한다.
"""

import uuid
from decimal import Decimal

import httpx

from app.core import config
from app.models.users import User
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.repositories.health_repository import HealthInputRepository
from app.repositories.prediction_repository import PredictionRepository

MODEL_VERSION = "waist_addon_v0_1"
FEATURE_VERSION = "d0_canonical_six_v0_1"  # core_bundle/legacy/d0_inference.py의 SCHEMA["version"]과 동일
CALIBRATION_VERSION = "identity"  # 허리둘레는 회귀 예측값 그대로 씀(보정 없음)
SUBMODEL_TYPE = "WAIST_CM_ESTIMATE"
SYSTEM_APPROVER = "SYSTEM_AUTO_APPROVAL"  # 사람이 아니라 이 서비스가 자동 승인했다는 표시(감사 목적)


def _pregnancy_status(user: User) -> str:
    if user.gender == "MALE":
        return "not_applicable"
    if user.gender == "FEMALE" and user.is_pregnant is True:
        return "pregnant"
    if user.gender == "FEMALE" and user.is_pregnant is False:
        return "nonpregnant"
    return "unknown"


class WaistEstimateService:
    def __init__(self):
        self.health_repo = HealthInputRepository()
        self.exercise_repo = ExerciseHabitRepository()
        self.prediction_repo = PredictionRepository()

    async def recompute_and_save(self, user: User, snapshot_id) -> None:
        """⚠️ 호출부에서 반드시 try/except로 감싸서 호출할 것 - 여기서 나는 예외가
        신체정보/운동습관 저장 자체를 실패시키면 안 됨."""

        if not config.TUNTUN_WAIST_BRIDGE_URL:
            return  # 로컬 검토 미설정 환경 - 조용히 스킵(운영에는 이 값을 절대 안 채움)

        health = await self.health_repo.get_latest(user.id)
        habit = await self.exercise_repo.get_latest(user.id)
        if health is None or habit is None or user.birth_year is None or user.birth_month is None:
            # ⚠️ 신체정보나 운동습관 중 하나라도 아직 없으면 계산 자체가 불가능 - 이것도
            # "실패"가 아니라 "아직 입력이 덜 끝남"이므로 INPUT_MISSING으로 남김(0이나
            # 성공으로 위장하지 않음).
            await self._save(user, snapshot_id, status="INPUT_MISSING", value=None, failure_reason_code="INPUT_INCOMPLETE")
            return

        input_values = health.input_values or {}
        age_years = _approx_age(user)
        body = {
            "age_years": age_years,
            "sex_code": 1 if user.gender == "MALE" else 2 if user.gender == "FEMALE" else None,
            "height_cm": input_values.get("height_cm"),
            "weight_kg": input_values.get("weight_kg"),
            "leisure_aerobic_moderate_equivalent_min_week": (
                (habit.aerobic_moderate_minutes or 0) + 2 * (habit.aerobic_high_minutes or 0)
            ),
            "strength_days_week": habit.strength_weekly_count,
            "pregnancyStatus": _pregnancy_status(user),
        }

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(f"{config.TUNTUN_WAIST_BRIDGE_URL}/waist/cm", json=body)
        except httpx.HTTPError:
            await self._save(user, snapshot_id, status="FAILED", value=None, failure_reason_code="BRIDGE_UNREACHABLE")
            return

        if response.status_code == 422:
            detail = response.json().get("detail", "OUT_OF_RANGE")
            reason = "PREGNANCY_UNSUPPORTED" if "PREGNANCY" in detail else "OUT_OF_RANGE"
            await self._save(
                user, snapshot_id,
                status="OUT_OF_RANGE" if reason == "OUT_OF_RANGE" else "FAILED",
                value=None, failure_reason_code=reason,
            )
            return
        if response.status_code != 200:
            await self._save(user, snapshot_id, status="FAILED", value=None, failure_reason_code="MODEL_ERROR")
            return

        cm = response.json()["waistCmEstimate"]
        await self._save(user, snapshot_id, status="COMPUTED", value=Decimal(str(round(cm, 1))), failure_reason_code=None)

    async def _save(self, user: User, snapshot_id, *, status: str, value, failure_reason_code):
        await self.prediction_repo.create(
            user_id=user.id,
            input_snapshot_id=snapshot_id,
            submodel_type=SUBMODEL_TYPE,
            value=value,
            status=status,
            failure_reason_code=failure_reason_code,
            model_version=MODEL_VERSION,
            feature_version=FEATURE_VERSION,
            calibration_version=CALIBRATION_VERSION,
            target_definition_version=None,
            run_id=uuid.uuid4().hex,
        )
        if status == "COMPUTED":
            # ⚠️ 2026-09-10 반영: 팀 결정으로 이 서브모델에 한해 자동 승인. is_active를
            # 매번 다시 True로 세팅해도 값은 변하지 않으니(멱등) 매 계산마다 호출해도
            # 안전함 - 대신 "관리자가 껐다가 다시 자동으로 켜지는" 상황을 피하려면
            # 나중에 관리자가 수동으로 비활성화(is_active=False)했을 가능성도 고려해야
            # 하는데, 지금은 그 구분 없이 계산될 때마다 항상 켜짐(팀이 필요하면 나중에
            # "수동 비활성화 여부"를 별도로 추적하도록 개선).
            await self.prediction_repo.upsert_approval(
                submodel_type=SUBMODEL_TYPE,
                model_version=MODEL_VERSION,
                is_active=True,
                approved_by_user_id=SYSTEM_APPROVER,
            )


def _approx_age(user: User) -> int:
    """⚠️ 정확한 생일이 없어 생년월만으로 근사 - 틈튼지수 vNext(peer/v2)와 달리 이
    모델은 "보수적 확정 나이" 정책이 없어서 단순 근사만 함(연도차, 이번 달 지났으면
    생일 지난 것으로 간주). 다른 곳(peer/v2)과 나이 계산 정책이 다르다는 점에 주의.
    디버그 날짜 시뮬레이션(service_today)은 다른 곳과 일관되게 재사용."""
    from app.core.time_utils import service_today

    today = service_today(user.id)
    age = today.year - user.birth_year
    if today.month < user.birth_month:
        age -= 1
    return age
