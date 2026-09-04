"""예측모델 결과 저장 도메인 (신규, 2026-08-27)

팀원(모델 담당)이 정리해준 스펙을 그대로 반영:
- 서브모델 3종: 허리둘레 추정(estimated_waist_cm), 당뇨 확률(score), 고혈압 확률(score)
- 틈틈지수는 종합 공식 미확정이라 서브모델 목록에 아예 안 넣음 (임시 평균 계산 금지)
- 계산 불가 시 0이 아니라 NULL + 사유코드로 저장
- "계산됨"과 "사용자에게 공개해도 됨"은 별개 — 승인된 model_version만 노출
- append-only, 같은 실행(run_id) 재시도 시 중복 저장 방지(idempotency)
- 허리둘레 실측값(health_input_snapshots)과 추정값(여기)은 서로 다른 테이블 — 실측 들어와도 추정 기록 안 지움
"""

import uuid
from enum import StrEnum

from tortoise import fields, models


class SubmodelType(StrEnum):
    """⚠️ 틈틈지수는 의도적으로 여기 없음. 종합 공식이 확정되기 전까지 값을 만들면 안 됨
    (팀원 지시: "임시 평균으로 계산하지 않고 미제공 상태로 두면 됩니다")."""

    WAIST_CM_ESTIMATE = "WAIST_CM_ESTIMATE"
    DIABETES_SCORE = "DIABETES_SCORE"
    HYPERTENSION_SCORE = "HYPERTENSION_SCORE"


class PredictionResultStatus(StrEnum):
    """ "결과 상태와 미제공 사유"를 구분하기 위한 상태값."""

    COMPUTED = "COMPUTED"  # 정상 계산됨 (value가 채워짐)
    FAILED = "FAILED"  # 실행 자체가 실패함
    INPUT_MISSING = "INPUT_MISSING"  # 필요한 입력값이 없어서 계산 불가
    OUT_OF_RANGE = "OUT_OF_RANGE"  # 입력값이 모델 지원 범위 밖


class ApprovedModelVersion(models.Model):
    """ "계산됐다는 것"과 "공개해도 된다는 것"은 별개라서 분리된 테이블.
    (submodel_type, model_version) 조합이 여기 is_active=True로 있어야만
    해당 버전으로 계산된 PredictionResult가 사용자에게 노출됨."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    submodel_type = fields.CharEnumField(enum_type=SubmodelType)
    model_version = fields.CharField(max_length=30)
    is_active = fields.BooleanField(default=False)
    approved_at = fields.DatetimeField(null=True)
    approved_by_user_id = fields.CharField(max_length=36, null=True)  # 승인한 관리자 (감사 목적)

    class Meta:
        table = "approved_model_versions"
        unique_together = (("submodel_type", "model_version"),)


class PredictionResult(models.Model):
    """서브모델 실행 결과 1건. append-only — 덮어쓰지 않고 항상 새 row.
    허리둘레 "실측값"은 health_input_snapshots에 따로 있고, 여긴 "모델 추정값"만 저장."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="prediction_results")
    input_snapshot = fields.ForeignKeyField(
        "models.HealthInputSnapshot", related_name="prediction_results"
    )  # 어떤 입력으로 계산했는지

    submodel_type = fields.CharEnumField(enum_type=SubmodelType)
    value = fields.DecimalField(max_digits=8, decimal_places=4, null=True)  # 계산 불가 시 NULL (0 아님)
    status = fields.CharEnumField(enum_type=PredictionResultStatus)
    failure_reason_code = fields.CharField(max_length=50, null=True)  # status가 COMPUTED가 아닐 때 사유

    # --- 재현성/추적용 버전 정보 (팀원 스펙 그대로) ---
    model_version = fields.CharField(max_length=30)
    feature_version = fields.CharField(max_length=30)
    calibration_version = fields.CharField(max_length=30)  # "identity"도 명시적으로 넣어야 함, 기본값 없음
    target_definition_version = fields.CharField(max_length=30, null=True)
    # ⚠️ 2026-08-27 모델 담당 팀원 확인: "P0 라벨 기준은 확정, 변경 계획 없음.
    # model_version으로 항상 당시 라벨 정의를 조회 가능하니 결과마다 중복 필수 저장할 필요는 없음."
    # → 그래서 nullable(선택)로 유지. 필요하면 나중에 model_version -> target_definition_version
    # 매핑을 관리하는 별도 "모델 레지스트리" 테이블로 정규화할 수도 있음(지금은 과설계라 생략).

    run_id = fields.CharField(max_length=100)  # 실행 추적 ID, idempotency에도 사용
    computed_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "prediction_results"
        # 같은 실행(run_id)이 같은 서브모델 결과를 재전송해도 중복 저장 안 되게
        unique_together = (("submodel_type", "run_id"),)
