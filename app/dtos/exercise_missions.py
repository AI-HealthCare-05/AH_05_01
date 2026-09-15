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


class ExerciseMissionsTodayResponse(BaseModel):
    """GET /exercise-missions/today"""

    card_completed: bool  # ⚠️ False면 프론트가 "오늘의 카드부터 완료해 주세요" 안내로 대체
    used: int
    limit: int = 2
    remaining: int
    options: list[ExerciseMissionOption]


class CreateExerciseMissionSessionRequest(BaseModel):
    catalog_entry_id: UUID
    idempotency_key: str


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


class ExerciseMissionActionRequest(BaseModel):
    """PATCH /exercise-mission-sessions/{id}"""

    action: Literal["pause", "resume"]
    accumulated_count: int | None = None  # 카운터형(SENSOR_STEPS 등) 센서값 동기화용


class CompleteExerciseMissionSessionRequest(BaseModel):
    idempotency_key: str
    manual_check: bool = False
    accumulated_count: int | None = None  # 완료 순간의 최신 센서값(카운터형)


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
