from datetime import date, datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel


class ExerciseMissionOption(BaseModel):
    catalog_entry_id: UUID
    title: str
    guide_text: str
    exec_type: str
    target_value: int
    unit: str
    five_element: str
    material_name: str
    already_completed_today: bool  # ⚠️ 오늘 이미 완료한 운동 - 다시 선택 못 하게 프론트에서 비활성 처리용


class ExerciseMissionSessionResponse(BaseModel):
    id: UUID
    state: str
    exec_type: str
    title: str
    five_element: str
    material_name: str
    target_duration_seconds: int | None
    target_count: int | None
    accumulated_duration_seconds: int
    accumulated_count: int
    started_at: datetime | None


class ExerciseMissionsTodayResponse(BaseModel):
    """GET /exercise-missions/today"""

    card_completed: bool  # ⚠️ False면 프론트가 "오늘의 카드부터 완료해 주세요" 안내로 대체
    used: int
    limit: int = 2
    remaining: int
    options: list[ExerciseMissionOption]
    # ⚠️ 2026-09-16 추가(QA) - ACTIVE/PAUSED 세션이 있으면 채워짐. 프론트가 이 값이 있으면
    # 목록/새 시작 대신 바로 진행 화면으로 안내(오늘의 카드 Challenge의 "미션 이어하기"와
    # 같은 패턴). 뒤로가기·홈 버튼으로 화면을 나가도 세션을 취소하지 않고 그대로 두는
    # 대신, 다음에 들어올 때 이 필드로 이어서 하게 하는 게 정확한 해법이었음.
    active_session: ExerciseMissionSessionResponse | None = None


class CreateExerciseMissionSessionRequest(BaseModel):
    catalog_entry_id: UUID
    idempotency_key: str


class ExerciseMissionActionRequest(BaseModel):
    """PATCH /exercise-mission-sessions/{id}"""

    # ⚠️ 2026-09-17 추가(QA #3) - "sync"는 상태를 안 바꾸고(ACTIVE 유지) 측정 중 누적값만
    # 주기적으로 반영하는 용도. 서버의 주기 저장이 "오늘의 카드" challengeId 기준이라
    # 틈새 운동 세션엔 반영이 전혀 안 되고 있었던 문제(리뷰 #3) 대응.
    action: Literal["pause", "resume", "sync"]
    accumulated_count: int | None = None  # 카운터형(SENSOR_STEPS 등) 센서값 동기화용
    # ⚠️ 2026-09-16 추가(QA F05) - 시간형(SENSOR_WALKING_DURATION/SENSOR_RUNNING_DURATION)
    # 센서 확정값. 서버가 "시작~일시정지 경과 시각"으로 대신 계산하던 걸 막기 위해,
    # 클라이언트(WalkingCadenceManager/RunningCadenceManager.getCurrentTotalSeconds())가
    # 실제로 측정한 값을 직접 실어 보내게 함.
    accumulated_duration_seconds: int | None = None


class CompleteExerciseMissionSessionRequest(BaseModel):
    idempotency_key: str
    manual_check: bool = False
    accumulated_count: int | None = None  # 완료 순간의 최신 센서값(카운터형)
    # ⚠️ 2026-09-16 추가(QA F05) - 위와 같은 이유. 완료 순간의 최종 확정 활동 시간.
    accumulated_duration_seconds: int | None = None


class CompleteExerciseMissionSessionResponse(BaseModel):
    session_id: UUID
    reward_slot: int
    material_name: str
    used: int
    remaining: int
    awarded_material: dict  # {"element": "WOOD", "material_name": "나뭇가지", "count": 1}
    authoritative_counts: dict  # {"used": 1, "remaining": 1} - 문서 요구사항 그대로 이름 유지


class ExerciseMissionRecordItem(BaseModel):
    service_date: date
    title: str
    five_element: str
    material_name: str
    reward_slot: int
    completed_at: datetime | None


class ExerciseMissionRecordsResponse(BaseModel):
    records: list[ExerciseMissionRecordItem]
