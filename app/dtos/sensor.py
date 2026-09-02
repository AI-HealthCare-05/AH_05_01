from datetime import datetime

from pydantic import BaseModel

from app.dtos.challenges import ChallengeProgressResponse


class SensorMeasurementItem(BaseModel):
    """안드로이드 MissionRecord.kt / MissionModels.kt 한 건과 대응.

    measurement_type: "STEP" / "STAIR" / "RUNNING"
    - STEP/STAIR: value(이번 배치에서 새로 감지된 걸음수·계단수 증가분)만 사용
    - RUNNING: speed_kmh, event_timestamp_ns 사용, value는 무시
    """

    measurement_type: str
    value: int | None = None
    speed_kmh: float | None = None
    event_timestamp_ns: int | None = None
    recorded_at: datetime


class SensorMeasurementBatchRequest(BaseModel):
    records: list[SensorMeasurementItem]


class SensorMeasurementBatchResponse(BaseModel):
    challenge: ChallengeProgressResponse
    accepted_count: int
    rejected_count: int
    rejected_reasons: list[str] = []
