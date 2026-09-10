"""틈튼지수(E그룹) 응답 DTO.

⚠️ 2026-09-02: 예측 모델 확정 전 뼈대(스켈레톤) 단계 — 필드명은 Figma 핸드오프
(tmtn-handoff/HANDOFF.md §3.7, FIELDS.csv의 E01~E06)를 그대로 따름. 실제 계산 로직은
tuntun_score_service.py에 전부 MOCK으로 표시돼 있고, ML팀의 실제 서비스 모델(예측 결과 +
점수 변환식)이 확정되면 서비스 레이어만 교체하면 되도록 DTO는 최종 화면 계약 기준으로 미리
맞춰둠. tmtnScore.value/factors 등은 절대 "확률"이 아니며, 화면에도 확률 표현을 쓰지 않음
(HANDOFF.md 정책 — 항목별 기여도 %는 노출하지 않고 막대 길이로만 표시).
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class ScoreBandRange(BaseModel):
    """관심/보통/양호 구간 정의 (E02)."""

    range_label: str  # 예: "0 ~ 39"


class ScoreBands(BaseModel):
    low: ScoreBandRange  # 관심 0~39
    mid: ScoreBandRange  # 보통 40~69
    high: ScoreBandRange  # 양호 70~100


class TmtnScoreFactor(BaseModel):
    """E03 "이번 계산에 반영된 항목" 막대 하나.
    ⚠️ weight_ratio는 화면에 %로 노출하지 않고 막대 길이로만 씀(HANDOFF.md 정책)."""

    name: str  # 예: "움직임·유산소", "근력"
    weight_ratio: float  # 0.0~1.0


class TmtnScoreResponse(BaseModel):
    """E01(요약)·E02(자세히) 공통 응답."""

    value: int  # 0~100 정수
    band_label: str  # "관심" / "보통" / "양호"
    marker_left_px: int | None = None  # E01 게이지 마커 위치 - 서버가 값 대신 좌표까지 내려줌
    period_label: str  # 예: "2026. 8. 20. ~ 8. 27."
    change_reason: str | None = None  # 예: "이번 주 유산소 시간을 새로 넣어 다시 계산했습니다."
    last_updated_label: str | None = None  # E02 전용
    bands: ScoreBands
    factors: list[TmtnScoreFactor]
    excluded_factors: list[str] = []  # 값이 없어 계산에서 제외된 항목 이름 목록 (HANDOFF.md 3.7 필수 정책)
    model_version: str  # 예: "국민건강영양조사 기반 참고 모델 v1.2"
    calibration_version: str | None = None  # 예: "보정 v1.0" — E06은 이 필드 없이 model_version만 표기
    data_source: str  # model_version·calibration_version을 조립한 화면 표시용 문자열 (E01/E02/E06 각자 다른 조립 규칙)
    is_mock: bool = True  # ⚠️ 실제 서비스 모델 연결 전까지 항상 true. 내부 QA 빌드에서만 노출 권장(ML팀 문서 §7)


class ScoreEligibilityResponse(BaseModel):
    """E05: 지수를 아직 낼 수 없는 상태."""

    eligible: bool
    recorded_days: int  # 최근 7일 중 카드 완료가 있었던 날 수
    required_days: int  # 5로 고정
    recorded_days_label: str  # 예: "3일 / 7일"
    required_days_label: str  # 예: "5일"


class TuntunScoreOrEligibilityResponse(BaseModel):
    """E01 진입점의 실제 응답 — eligible이 false면 score는 항상 null (E05로 분기)."""

    eligible: bool
    score: TmtnScoreResponse | None = None
    eligibility: ScoreEligibilityResponse | None = None  # eligible=false일 때만 채움


class ScoreInputsResponse(BaseModel):
    """E04 "계산에 쓰인 값" — 기존 온보딩/프로필/운동습관 데이터를 그대로 읽어와 조립.
    ⚠️ 이 스냅샷들을 위한 새 테이블을 만들지 않음 — users(gender/birth_year/birth_month),
    health_input_snapshots(최신 height_cm/weight_kg), exercise_habit_snapshots(최신 운동량)을
    그대로 재사용함. 값이 아예 없으면 해당 필드는 null로 내려가고 화면에서 "입력 필요"로 처리."""

    birth_month_label: str | None = None  # 예: "1990년 3월"
    sex_label: str | None = None  # 예: "여성"
    height_cm: float | None = None
    weight_kg: float | None = None
    strength_label: str | None = None  # 예: "주 2회 · 적당히" (빈도+강도 조립)
    cardio_low_min: int | None = None
    cardio_moderate_min: int | None = None
    cardio_vigorous_min: int | None = None  # 0도 정상값 — None(빈 값)과 구분됨


class TuntunComponentScoreV2(BaseModel):
    """앱에 표시하는 4개 영역 점수. 내부 확률·fold·참여자 ID는 포함하지 않는다."""

    model_config = ConfigDict(populate_by_name=True)

    key: Literal["physical", "diabetes", "hypertension", "lifestyle"]
    label: str
    score: float | None
    available: bool
    band_label: str | None = Field(alias="bandLabel")
    guidance: str
    source: Literal["mock_health_input", "questionnaire", "unavailable"]


class TuntunScoreV2Response(BaseModel):
    """종합점수와 4개 하위점수를 함께 제공하는 앱 통합용 Mock 계약."""

    model_config = ConfigDict(populate_by_name=True)

    tuntun_index: float | None = Field(alias="tuntunIndex")
    physical_score: float | None = Field(alias="physicalScore")
    diabetes_score: float | None = Field(alias="diabetesScore")
    hypertension_score: float | None = Field(alias="hypertensionScore")
    lifestyle_score: float | None = Field(alias="lifestyleScore")
    aerobic_score: float | None = Field(alias="aerobicScore")
    strength_score: float | None = Field(alias="strengthScore")
    lifestyle_available_subcomponent_count: int = Field(alias="lifestyleAvailableSubcomponentCount")
    lifestyle_score_source: Literal["questionnaire", "unavailable"] = Field(alias="lifestyleScoreSource")
    component_scores: list[TuntunComponentScoreV2] = Field(alias="componentScores")
    available_component_count: int = Field(alias="availableComponentCount")
    available_components: list[str] = Field(alias="availableComponents")
    unavailable_components: list[str] = Field(alias="unavailableComponents")
    is_partial_score: bool = Field(alias="isPartialScore")
    score_available: bool = Field(alias="scoreAvailable")
    activity_window_start: str = Field(alias="activityWindowStart")
    activity_window_end: str = Field(alias="activityWindowEnd")
    recorded_days: int = Field(alias="recordedDays")
    mission_integration_status: Literal["pending_evidence"] = Field(alias="missionIntegrationStatus")
    score_contract_version: str = Field(alias="scoreContractVersion")
    model_version: str = Field(alias="modelVersion")
    calibration_version: str | None = Field(alias="calibrationVersion")
    notice: str
    older_adult_notice: str | None = Field(alias="olderAdultNotice")
    is_mock: bool = Field(alias="isMock")
