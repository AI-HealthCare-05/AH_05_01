from app.models.prediction import ApprovedModelVersion, PredictionResult


class PredictionRepository:
    def __init__(self):
        self._result_model = PredictionResult
        self._approval_model = ApprovedModelVersion

    async def get_existing(self, submodel_type: str, run_id: str) -> PredictionResult | None:
        """idempotency 체크용. (submodel_type, run_id) 조합이 이미 있으면 재저장 안 함."""

        return await self._result_model.get_or_none(submodel_type=submodel_type, run_id=run_id)

    async def create(self, user_id, input_snapshot_id, **fields) -> PredictionResult:
        return await self._result_model.create(user_id=user_id, input_snapshot_id=input_snapshot_id, **fields)

    async def get_approved_version_map(self) -> dict[str, set[str]]:
        """submodel_type별로 "노출 가능한 model_version 집합"을 미리 다 가져옴.
        결과 여러 개를 필터링할 때마다 매번 쿼리하지 않으려고 한 번에 로드."""

        rows = await self._approval_model.filter(is_active=True).values("submodel_type", "model_version")
        result_map: dict[str, set[str]] = {}
        for row in rows:
            result_map.setdefault(row["submodel_type"], set()).add(row["model_version"])
        return result_map

    async def get_latest_computed_by_submodel(self, user_id) -> dict[str, PredictionResult]:
        """서브모델별 "가장 최근 COMPUTED 결과"만. FAILED/INPUT_MISSING 등은 사용자 노출용 조회에서 제외."""

        rows = await self._result_model.filter(user_id=user_id, status="COMPUTED").order_by("-computed_at")
        latest: dict[str, PredictionResult] = {}
        for row in rows:
            if row.submodel_type not in latest:
                latest[row.submodel_type] = row
        return latest

    async def upsert_approval(
        self, submodel_type: str, model_version: str, is_active: bool, approved_by_user_id
    ) -> ApprovedModelVersion:
        from datetime import UTC, datetime

        instance, _ = await self._approval_model.get_or_create(submodel_type=submodel_type, model_version=model_version)
        instance.is_active = is_active
        instance.approved_at = datetime.now(UTC) if is_active else instance.approved_at
        instance.approved_by_user_id = str(approved_by_user_id)
        await instance.save(update_fields=["is_active", "approved_at", "approved_by_user_id"])
        return instance
