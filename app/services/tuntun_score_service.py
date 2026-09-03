"""틈튼지수(E그룹) 서비스.

⚠️ 2026-09-02: 뼈대(스켈레톤) 단계. ML팀의 실제 서비스 모델(허리둘레/당뇨/고혈압 예측 +
점수 변환식)이 아직 확정 전(§9 미확정 계약 14개 전부 PENDING)이라, _calculate_mock_score()는
실제 예측을 전혀 하지 않고 사용자의 최근 7일 완료율·운동습관값만으로 임시 점수를 만든다.
나중에 진짜 모델이 오면 이 함수만 교체하면 되도록 서비스 바깥(DTO·라우터)은 최종 화면 계약
기준으로 맞춰뒀다. HANDOFF.md 3.7절 정책(공복혈당·허리둘레 미수집, %기여도 미노출, 값 없는
항목은 0 대체 대신 제외) 전부 이 서비스에서 지킨다.
"""

from datetime import date, timedelta

from app.dtos.tuntun_score import (
    ScoreBandRange,
    ScoreBands,
    ScoreEligibilityResponse,
    ScoreInputsResponse,
    TmtnScoreFactor,
    TmtnScoreResponse,
    TuntunScoreOrEligibilityResponse,
)
from app.models.users import User
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.repositories.health_repository import HealthInputRepository
from app.repositories.record_repository import RecordRepository

REQUIRED_RECORDED_DAYS = 5  # HANDOFF.md 3.7: 최근 7일 중 5일 미만이면 산출 불가
LOOKBACK_DAYS = 7

MODEL_VERSION = "국민건강영양조사 기반 참고 모델 v1.2"
CALIBRATION_VERSION = "보정 v1.0"

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
        today = date.today()
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

        period_label = f"{start.strftime('%Y. %-m. %-d.')} ~ {today.strftime('%-m. %-d.')}"

        return TmtnScoreResponse(
            value=value,
            band_label=_band_label(value),
            marker_left_px=None,  # 실제 게이지 폭은 클라이언트가 계산 (서버 좌표 계산은 후속 작업)
            period_label=period_label,
            change_reason="이번 주 기록을 반영해 다시 계산했습니다." if factors else None,
            last_updated_label=today.strftime("%Y. %-m. %-d."),
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
