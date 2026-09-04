"""틈튼지수(E그룹) 서비스.

⚠️ 2026-09-02: 뼈대(스켈레톤) 단계. ML팀의 실제 서비스 모델(허리둘레/당뇨/고혈압 예측 +
점수 변환식)이 아직 확정 전(§9 미확정 계약 14개 전부 PENDING)이라, _calculate_mock_score()는
실제 예측을 전혀 하지 않고 사용자의 최근 7일 완료율·운동습관값만으로 임시 점수를 만든다.
나중에 진짜 모델이 오면 이 함수만 교체하면 되도록 서비스 바깥(DTO·라우터)은 최종 화면 계약
기준으로 맞춰뒀다. HANDOFF.md 3.7절 정책(공복혈당·허리둘레 미수집, %기여도 미노출, 값 없는
항목은 0 대체 대신 제외) 전부 이 서비스에서 지킨다.
"""

from datetime import date, timedelta
from math import isfinite

import httpx

from app.core import config
from app.core.time_utils import service_today
from app.dtos.tuntun_score import (
    ScoreBandRange,
    ScoreBands,
    ScoreEligibilityResponse,
    ScoreInputsResponse,
    TmtnScoreFactor,
    TmtnScoreResponse,
    TuntunComponentScoreV2,
    TuntunScoreOrEligibilityResponse,
    TuntunScoreV2Response,
)
from app.models.users import User
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.repositories.health_repository import HealthInputRepository
from app.repositories.record_repository import RecordRepository

REQUIRED_RECORDED_DAYS = 5  # HANDOFF.md 3.7: 최근 7일 중 5일 미만이면 산출 불가
LOOKBACK_DAYS = 7

MODEL_VERSION = "국민건강영양조사 기반 참고 모델 v1.2"
CALIBRATION_VERSION = "보정 v1.0"
V2_MOCK_MODEL_VERSION = "mock-ui-integration-v0.1"
V2_SCORE_CONTRACT_VERSION = "v0.2-empirical-cdf-owner-approved"
V2_NOTICE = (
    "틈튼지수는 입력 정보와 통계 모델을 바탕으로 산출한 생활습관 개선 참고용 보정 점수이며, "
    "의료진의 진단이나 치료를 대신하지 않습니다. 현재 화면의 건강영역 점수는 연동 확인용 Mock 값입니다."
)
V2_OLDER_ADULT_NOTICE = "65세 이상에서는 모델의 예측 불확실성이 상대적으로 클 수 있어 참고용으로 활용해 주세요."

_V2_MOCK_HEALTH_SCORES = {
    "physical": 74.0,
    "diabetes": 78.0,
    "hypertension": 76.0,
}

_V2_COMPONENT_META = {
    "physical": ("신체", "허리둘레 위험을 낮추는 방향으로 체중과 활동 습관을 꾸준히 관리해 보세요."),
    "diabetes": ("당뇨", "규칙적인 활동과 균형 잡힌 식사를 이어가며 생활습관을 관리해 보세요."),
    "hypertension": ("고혈압", "걷기 등 꾸준한 활동과 나트륨 섭취 관리를 실천해 보세요."),
    "lifestyle": ("생활습관", "유산소 운동 시간과 주간 근력운동 일수를 조금씩 늘려 보세요."),
}

_STRENGTH_INTENSITY_LABEL = {"LIGHT": "가볍게", "MODERATE": "적당히", "HARD": "힘들게"}
_SEX_LABEL = {"MALE": "남성", "FEMALE": "여성"}

_BANDS = ScoreBands(
    low=ScoreBandRange(range_label="0 ~ 39"),
    mid=ScoreBandRange(range_label="40 ~ 69"),
    high=ScoreBandRange(range_label="70 ~ 100"),
)


def _band_label(value: int) -> str:
    if value < 40:
        return "관심"
    if value < 70:
        return "보통"
    return "양호"


def _band_label_float(value: float | None) -> str | None:
    return _band_label(round(value)) if value is not None else None


def _positive_finite_number(value: object) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if isfinite(number) and number > 0 else None


def _nonnegative_finite_number(value: object) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if isfinite(number) and number >= 0 else None


def _mean(values: list[float]) -> float | None:
    return sum(values) / len(values) if values else None


def _component_v2(key: str, score: float | None, source: str) -> TuntunComponentScoreV2:
    label, guidance = _V2_COMPONENT_META[key]
    if score is None:
        guidance = "계산에 필요한 입력이 부족해 이번 종합점수에서는 제외했습니다."
    return TuntunComponentScoreV2(
        key=key,
        label=label,
        score=score,
        available=score is not None,
        bandLabel=_band_label_float(score),
        guidance=guidance,
        source=source,
    )


class TuntunScoreService:
    def __init__(self):
        self.record_repo = RecordRepository()
        self.health_repo = HealthInputRepository()
        self.exercise_repo = ExerciseHabitRepository()

    async def _count_recorded_days(self, user_id, today: date) -> int:
        """HANDOFF.md 3.7: "기록된 날" = 카드 완료(challenge COMPLETED)가 있었던 날."""

        start = today - timedelta(days=LOOKBACK_DAYS - 1)
        card_sets = await self.record_repo.get_card_sets_in_range(user_id, start, today)
        recorded = 0
        for card_set in card_sets:
            selection = getattr(card_set, "selection", None)
            challenge = getattr(selection, "challenge", None) if selection else None
            if challenge is not None and challenge.state == "COMPLETED":
                recorded += 1
        return recorded

    async def get_score(self, user: User) -> TuntunScoreOrEligibilityResponse:
        today = service_today()  # ⚠️ 리뷰 반영: 서버 로컬 타임존 대신 KST 고정
        recorded_days = await self._count_recorded_days(user.id, today)

        if recorded_days < REQUIRED_RECORDED_DAYS:
            # E05: 몸 정보·운동량 입력 부족은 산출 불가 사유가 아님 — 오직 기록 일수만 봄.
            return TuntunScoreOrEligibilityResponse(
                eligible=False,
                eligibility=ScoreEligibilityResponse(
                    eligible=False,
                    recorded_days=recorded_days,
                    required_days=REQUIRED_RECORDED_DAYS,
                    recorded_days_label=f"{recorded_days}일 / {LOOKBACK_DAYS}일",
                    required_days_label=f"{REQUIRED_RECORDED_DAYS}일",
                ),
            )

        score = await self._calculate_mock_score(user, today)
        return TuntunScoreOrEligibilityResponse(eligible=True, score=score)

    async def _get_pregnancy_status(self, user: User) -> str | None:
        """⚠️ 통합모델 입력 계약(input_schema.json)은 "pregnancy_status: explicit
        nonpregnant required by score adapter" — 미수집을 nonpregnant로 조용히
        가정하면 안 된다고 명시돼 있음.

        2026-09-04: 온보딩(A07)에 여성만 물어보는 임신 여부 질문을 추가하면서 채움.
        - 남성(MALE): 생물학적으로 해당 사항이 없어 자동으로 "nonpregnant" 확정.
        - 여성이고 명시적으로 "아니오"(is_pregnant=False)라고 답한 경우만 "nonpregnant".
        - 여성인데 아직 안 물어봤거나(is_pregnant=None) 임신 중(True)이면 None을 돌려줘서
          실모델 호출 자체를 건너뛰게 함 - "모른다"를 "임신 아님"으로 넘겨짚지 않음.
        """
        if user.gender == "MALE":
            return "nonpregnant"
        if user.gender == "FEMALE" and user.is_pregnant is False:
            return "nonpregnant"
        return None

    async def _call_local_model_inference(
        self,
        age_years: int,
        sex_code: int,
        height_cm: float,
        weight_kg: float,
        aerobic_equivalent_min_week: float,
        strength_days_week: int,
        pregnancy_status: str,
        activity_window_end: date,
        recorded_days: int,
    ) -> dict | None:
        """⚠️ 2026-09-04: 로컬 개발/검토용 실모델 서비스(별도 Python 3.14.7 프로세스,
        tuntun_local_service.py)가 떠 있을 때만 동작. config.TUNTUN_LOCAL_MODEL_URL을
        .env에 안 채우면(기본값 None) 이 함수는 맨 앞에서 바로 None을 돌려주고 끝나서
        운영에는 절대 영향 없음. 모델 패키지 자체는 이 저장소에 없고 별도 배포 채널로
        받아서 로컬에서 직접 띄우는 구조 - PRODUCTION_RELEASE_GATE: BLOCKED 상태라
        운영 서버에 이 값을 채우면 안 됨.

        실패(연결 안 됨/타임아웃/4xx/5xx)하면 조용히 None을 돌려주고, 호출부가 기존
        Mock 계산으로 폴백함 - 로컬 모델 서버를 안 띄워놓고 테스트해도 앱이 죽지 않음.
        """
        if not config.TUNTUN_LOCAL_MODEL_URL:
            return None

        body = {
            "features": {
                "age_years": min(max(age_years, 19), 80),  # 모델 top-code: 80세 이상은 80으로
                "sex_code": sex_code,
                "height_cm": height_cm,
                "weight_kg": weight_kg,
                "leisure_aerobic_moderate_equivalent_min_week": aerobic_equivalent_min_week,
                "strength_days_week": min(strength_days_week, 7),
            },
            "pregnancy_status": pregnancy_status,
            "activity_window_end": activity_window_end.isoformat(),
            "recorded_days": recorded_days,
        }
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.post(f"{config.TUNTUN_LOCAL_MODEL_URL}/score", json=body)
            if response.status_code != 200:
                return None
            return response.json()
        except httpx.HTTPError:
            return None

    async def get_score_v2(self, user: User) -> TuntunScoreV2Response:
        """4영역 UI 연결을 검증하기 위한 deterministic Mock 응답을 만든다.

        건강영역 3개는 production 모델이 없으므로 값을 추론하지 않고 고정 Mock 점수를
        사용한다. 단, 실제 모델의 최소 입력인 연령·성별·키·몸무게가 모두 있을 때만
        available로 표시한다. 생활습관은 승인된 설문 산식만 적용하며 미션 수행값은
        환산·coverage 정책이 승인되기 전까지 점수에 더하지 않는다.
        """

        today = service_today()  # ⚠️ 리뷰 반영: 서버 로컬 타임존 대신 KST 고정
        start = today - timedelta(days=LOOKBACK_DAYS - 1)
        recorded_days = await self._count_recorded_days(user.id, today)
        health = await self.health_repo.get_latest(user.id)
        habit = await self.exercise_repo.get_latest(user.id)

        input_values = (health.input_values or {}) if health else {}
        has_health_inputs = all(
            (
                user.birth_year is not None,
                user.birth_month is not None,
                user.gender is not None,
                _positive_finite_number(input_values.get("height_cm")) is not None,
                _positive_finite_number(input_values.get("weight_kg")) is not None,
            )
        )

        physical_score = _V2_MOCK_HEALTH_SCORES["physical"] if has_health_inputs else None
        diabetes_score = _V2_MOCK_HEALTH_SCORES["diabetes"] if has_health_inputs else None
        hypertension_score = _V2_MOCK_HEALTH_SCORES["hypertension"] if has_health_inputs else None

        # ⚠️ 2026-09-04: 로컬 검토용 실모델이 떠 있으면(TUNTUN_LOCAL_MODEL_URL 설정 시) 그
        # 결과로 통째로 교체. pregnancy_status를 아직 못 구하면(_get_pregnancy_status가
        # None) 절대 호출 안 하고 위 Mock 값 그대로 씀 - "모른다"를 "임신 아님"으로
        # 넘겨짚지 않음.
        if has_health_inputs and habit is not None:
            pregnancy_status = await self._get_pregnancy_status(user)
            if pregnancy_status is not None:
                current_year_for_age = today.year - (1 if user.birth_month and today.month < user.birth_month else 0)
                age_for_model = current_year_for_age - user.birth_year
                moderate_for_model = _nonnegative_finite_number(habit.aerobic_moderate_minutes) or 0.0
                vigorous_for_model = _nonnegative_finite_number(habit.aerobic_high_minutes) or 0.0
                real_response = await self._call_local_model_inference(
                    age_years=age_for_model,
                    sex_code=1 if user.gender == "MALE" else 2,
                    height_cm=_positive_finite_number(input_values.get("height_cm")),
                    weight_kg=_positive_finite_number(input_values.get("weight_kg")),
                    aerobic_equivalent_min_week=moderate_for_model + (2.0 * vigorous_for_model),
                    strength_days_week=int(_nonnegative_finite_number(habit.strength_weekly_count) or 0),
                    pregnancy_status=pregnancy_status,
                    activity_window_end=today,
                    recorded_days=recorded_days,
                )
                if real_response is not None:
                    return TuntunScoreV2Response.model_validate(real_response)

        aerobic_score = None
        strength_score = None
        if habit is not None:
            moderate = _nonnegative_finite_number(habit.aerobic_moderate_minutes)
            vigorous = _nonnegative_finite_number(habit.aerobic_high_minutes)
            if moderate is not None and vigorous is not None:
                moderate_equivalent_minutes = moderate + (2.0 * vigorous)
                aerobic_score = min(100.0, moderate_equivalent_minutes / 150.0 * 100.0)

            strength_days = _nonnegative_finite_number(habit.strength_weekly_count)
            if strength_days is not None and strength_days <= 7.0:
                strength_score = min(100.0, strength_days / 2.0 * 100.0)

        lifestyle_subscores = [score for score in (aerobic_score, strength_score) if score is not None]
        lifestyle_score = _mean(lifestyle_subscores)

        raw_scores = {
            "physical": physical_score,
            "diabetes": diabetes_score,
            "hypertension": hypertension_score,
            "lifestyle": lifestyle_score,
        }
        available_components = [key for key, score in raw_scores.items() if score is not None]
        unavailable_components = [key for key, score in raw_scores.items() if score is None]
        tuntun_index = _mean([score for score in raw_scores.values() if score is not None])
        components = [
            _component_v2(
                key,
                score,
                "questionnaire"
                if key == "lifestyle" and score is not None
                else ("mock_health_input" if score is not None else "unavailable"),
            )
            for key, score in raw_scores.items()
        ]

        current_year = today.year - (1 if user.birth_month and today.month < user.birth_month else 0)
        age = current_year - user.birth_year if user.birth_year is not None else None

        return TuntunScoreV2Response(
            tuntunIndex=tuntun_index,
            physicalScore=physical_score,
            diabetesScore=diabetes_score,
            hypertensionScore=hypertension_score,
            lifestyleScore=lifestyle_score,
            aerobicScore=aerobic_score,
            strengthScore=strength_score,
            lifestyleAvailableSubcomponentCount=len(lifestyle_subscores),
            lifestyleScoreSource="questionnaire" if lifestyle_score is not None else "unavailable",
            componentScores=components,
            availableComponentCount=len(available_components),
            availableComponents=available_components,
            unavailableComponents=unavailable_components,
            isPartialScore=0 < len(available_components) < 4,
            scoreAvailable=tuntun_index is not None,
            activityWindowStart=start.isoformat(),
            activityWindowEnd=today.isoformat(),
            recordedDays=recorded_days,
            missionIntegrationStatus="pending_evidence",
            scoreContractVersion=V2_SCORE_CONTRACT_VERSION,
            modelVersion=V2_MOCK_MODEL_VERSION,
            calibrationVersion=None,
            notice=V2_NOTICE,
            olderAdultNotice=V2_OLDER_ADULT_NOTICE if age is not None and age >= 65 else None,
            isMock=True,
        )

    async def _calculate_mock_score(self, user: User, today: date) -> TmtnScoreResponse:
        """⚠️ MOCK — 실제 예측 모델과 무관한 임시 계산. 최근 7일 완료율 + 운동습관값만
        섞어서 그럴듯한 점수를 만든다. 진짜 모델 연결 시 이 함수만 교체하면 됨."""

        start = today - timedelta(days=LOOKBACK_DAYS - 1)
        recorded_days = await self._count_recorded_days(user.id, today)
        completion_ratio = recorded_days / LOOKBACK_DAYS

        habit = await self.exercise_repo.get_latest(user.id)
        factors: list[TmtnScoreFactor] = []
        excluded: list[str] = []

        if habit is not None:
            aerobic_minutes = (
                (habit.aerobic_low_minutes or 0)
                + (habit.aerobic_moderate_minutes or 0)
                + (habit.aerobic_high_minutes or 0)
            )
            # 세계보건기구 주간 150분 기준을 100%로 잡은 MOCK 비율 (실제 산식 아님, §9 결정 4번 대기 중)
            aerobic_score = min(aerobic_minutes / 150, 1.0)
            factors.append(TmtnScoreFactor(name="움직임·유산소", weight_ratio=round(aerobic_score, 2)))

            if habit.strength_weekly_count is not None:
                # 주 2일 기준 MOCK 비율 (실제 산식 아님, §9 결정 5번 대기 중)
                strength_score = min(habit.strength_weekly_count / 2, 1.0)
                factors.append(TmtnScoreFactor(name="근력", weight_ratio=round(strength_score, 2)))
            else:
                excluded.append("근력")
        else:
            excluded.extend(["움직임·유산소", "근력"])

        # 완료율 + 운동습관 비율 평균을 0~100으로 환산 (MOCK)
        factor_avg = sum(f.weight_ratio for f in factors) / len(factors) if factors else 0.0
        value = round(((completion_ratio * 0.5) + (factor_avg * 0.5)) * 100)
        value = max(0, min(100, value))

        # ⚠️ PR #12 리뷰(P1) 반영: %-m/%-d는 glibc(Linux/Mac)에만 있는 확장 포맷이라
        # Windows에서 ValueError로 500이 남. f-string으로 직접 조립해서 플랫폼 무관하게 함.
        period_label = f"{start.year}. {start.month}. {start.day}. ~ {today.month}. {today.day}."

        return TmtnScoreResponse(
            value=value,
            band_label=_band_label(value),
            marker_left_px=None,  # 실제 게이지 폭은 클라이언트가 계산 (서버 좌표 계산은 후속 작업)
            period_label=period_label,
            change_reason="이번 주 기록을 반영해 다시 계산했습니다." if factors else None,
            last_updated_label=f"{today.year}. {today.month}. {today.day}.",
            bands=_BANDS,
            factors=factors,
            excluded_factors=excluded,
            model_version=MODEL_VERSION,
            calibration_version=CALIBRATION_VERSION,
            data_source=f"{MODEL_VERSION} · {CALIBRATION_VERSION}",
            is_mock=True,
        )

    async def get_score_about(self) -> TmtnScoreResponse:
        """E06 "이 지수에 대하여" — 정적 안내라 값 없이 출처 문자열만 필요하지만,
        DTO를 재사용하기 위해 최소 필드만 채워 돌려줌(값 자체는 화면에서 안 씀)."""

        return TmtnScoreResponse(
            value=0,
            band_label="관심",
            period_label="",
            bands=_BANDS,
            factors=[],
            model_version=MODEL_VERSION,
            calibration_version=None,  # HANDOFF.md: E06은 보정 버전 표기 없음
            data_source=f"출처 · {MODEL_VERSION}",
            is_mock=True,
        )

    async def get_score_inputs(self, user: User) -> ScoreInputsResponse:
        """E04 "계산에 쓰인 값" — 새 테이블 없이 기존 온보딩/프로필/운동습관 데이터를 조립."""

        birth_month_label = None
        if user.birth_year and user.birth_month:
            birth_month_label = f"{user.birth_year}년 {user.birth_month}월"

        health = await self.health_repo.get_latest(user.id)
        height_cm = (health.input_values or {}).get("height_cm") if health else None
        weight_kg = (health.input_values or {}).get("weight_kg") if health else None

        habit = await self.exercise_repo.get_latest(user.id)
        strength_label = None
        cardio_low = cardio_mod = cardio_high = None
        if habit is not None:
            if habit.strength_weekly_count is not None:
                intensity_label = _STRENGTH_INTENSITY_LABEL.get(str(habit.strength_intensity), "")
                strength_label = f"주 {habit.strength_weekly_count}회" + (
                    f" · {intensity_label}" if intensity_label else ""
                )
            cardio_low = habit.aerobic_low_minutes
            cardio_mod = habit.aerobic_moderate_minutes
            cardio_high = habit.aerobic_high_minutes

        return ScoreInputsResponse(
            birth_month_label=birth_month_label,
            sex_label=_SEX_LABEL.get(str(user.gender)) if user.gender else None,
            height_cm=float(height_cm) if height_cm is not None else None,
            weight_kg=float(weight_kg) if weight_kg is not None else None,
            strength_label=strength_label,
            cardio_low_min=cardio_low,
            cardio_moderate_min=cardio_mod,
            cardio_vigorous_min=cardio_high,
        )
