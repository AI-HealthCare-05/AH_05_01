from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel

from app.dtos.base import BaseSerializerModel


class SubmodelResultItem(BaseModel):
    """서브모델 하나의 실행 결과. status가 COMPUTED가 아니면 value는 반드시 None이어야 함
    (0으로 채우지 말 것 — 팀원 스펙 §3)."""

    submodel_type: str  # WAIST_CM_ESTIMATE / DIABETES_SCORE / HYPERTENSION_SCORE
    value: Decimal | None = None
    status: str  # COMPUTED / FAILED / INPUT_MISSING / OUT_OF_RANGE
    failure_reason_code: str | None = None
    model_version: str
    feature_version: str
    calibration_version: str  # "identity"도 명시적으로 보낼 것, 기본값 없음
    target_definition_version: str | None = None


class PredictionResultBatchRequest(BaseModel):
    """한 번의 모델 실행(run)에서 서브모델 여러 개 결과를 한 번에 저장.
    run_id가 같은 요청을 재전송해도 서브모델별로 중복 저장 안 됨(idempotency)."""

    input_snapshot_id: UUID
    run_id: str
    results: list[SubmodelResultItem]


class PredictionResultResponse(BaseSerializerModel):
    id: UUID
    submodel_type: str
    value: Decimal | None = None
    status: str
    failure_reason_code: str | None = None
    model_version: str
    feature_version: str
    calibration_version: str
    target_definition_version: str | None = None
    run_id: str
    computed_at: datetime


class PredictionResultBatchResponse(BaseModel):
    saved: list[PredictionResultResponse]
    skipped_duplicate_count: int


class ApproveModelVersionRequest(BaseModel):
    submodel_type: str
    model_version: str
    is_active: bool = True
