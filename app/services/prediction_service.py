from fastapi import HTTPException, status

from app.dtos.prediction import (
    ApproveModelVersionRequest,
    PredictionResultBatchRequest,
    PredictionResultBatchResponse,
    PredictionResultResponse,
)
from app.models.health import HealthInputSnapshot
from app.models.users import User
from app.repositories.prediction_repository import PredictionRepository

VALID_SUBMODEL_TYPES = {"WAIST_CM_ESTIMATE", "DIABETES_SCORE", "HYPERTENSION_SCORE"}
VALID_STATUSES = {"COMPUTED", "FAILED", "INPUT_MISSING", "OUT_OF_RANGE"}


class PredictionService:
    def __init__(self):
        self.repo = PredictionRepository()

    async def save_batch(
        self, caller: User, request: PredictionResultBatchRequest
    ) -> PredictionResultBatchResponse:
        """모델 파이프라인이 호출하는 저장 API. 관리자 계정으로만 호출 가능
        (앱 사용자가 직접 자기 예측 결과를 조작할 수 없게).

        ⚠️ 실제로는 이 API를 모델 파이프라인이 "사용자 대신" 호출해야 하는데,
        지금은 앱과 동일한 JWT 인증(get_request_user)을 그대로 씀. 모델 파이프라인이
        어떤 방식으로 인증할지(서비스 계정 토큰, 내부 전용 API 키 등)는 팀과 별도 협의 필요.
        """

        if not caller.is_admin:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="관리자만 결과를 저장할 수 있습니다.")

        snapshot = await HealthInputSnapshot.get_or_none(id=request.input_snapshot_id)
        if snapshot is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="입력 스냅샷을 찾을 수 없습니다.")

        saved = []
        skipped = 0

        for item in request.results:
            if item.submodel_type not in VALID_SUBMODEL_TYPES:
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                    detail=f"알 수 없는 submodel_type: {item.submodel_type}",
                )
            if item.status not in VALID_STATUSES:
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                    detail=f"알 수 없는 status: {item.status}",
                )
            if item.status == "COMPUTED" and item.value is None:
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                    detail=f"{item.submodel_type}: status=COMPUTED인데 value가 없습니다.",
                )
            if item.status != "COMPUTED" and item.value is not None:
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                    detail=f"{item.submodel_type}: status={item.status}인데 value가 채워져 있습니다 (NULL이어야 함).",
                )

            existing = await self.repo.get_existing(item.submodel_type, request.run_id)
            if existing is not None:
                skipped += 1
                saved.append(PredictionResultResponse.model_validate(existing))
                continue

            created = await self.repo.create(
                user_id=snapshot.user_id,
                input_snapshot_id=snapshot.id,
                submodel_type=item.submodel_type,
                value=item.value,
                status=item.status,
                failure_reason_code=item.failure_reason_code,
                model_version=item.model_version,
                feature_version=item.feature_version,
                calibration_version=item.calibration_version,
                target_definition_version=item.target_definition_version,
                run_id=request.run_id,
            )
            saved.append(PredictionResultResponse.model_validate(created))

        return PredictionResultBatchResponse(saved=saved, skipped_duplicate_count=skipped)

    async def approve_model_version(self, caller: User, request: ApproveModelVersionRequest) -> dict:
        if not caller.is_admin:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="관리자만 승인할 수 있습니다.")
        if request.submodel_type not in VALID_SUBMODEL_TYPES:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail="알 수 없는 submodel_type")

        instance = await self.repo.upsert_approval(
            submodel_type=request.submodel_type,
            model_version=request.model_version,
            is_active=request.is_active,
            approved_by_user_id=caller.id,
        )
        return {
            "submodel_type": instance.submodel_type,
            "model_version": instance.model_version,
            "is_active": instance.is_active,
        }

    async def get_latest_visible(self, user: User) -> list[PredictionResultResponse]:
        """사용자에게 보여줄 결과만: COMPUTED + 승인된 model_version인 것만.
        FAILED/미승인 버전은 계산은 됐어도 여기서 걸러져서 절대 노출 안 됨."""

        latest_by_submodel = await self.repo.get_latest_computed_by_submodel(user.id)
        approved_versions = await self.repo.get_approved_version_map()

        visible = []
        for submodel_type, result in latest_by_submodel.items():
            allowed_versions = approved_versions.get(submodel_type, set())
            if result.model_version in allowed_versions:
                visible.append(PredictionResultResponse.model_validate(result))
        return visible
