from fastapi import HTTPException, status

from app.dtos.health import HealthInputCreateRequest, HealthInputResponse
from app.models.users import User
from app.repositories.health_repository import HealthInputRepository
from app.services.waist_estimate_service import WaistEstimateService


class HealthInputService:
    def __init__(self):
        self.repo = HealthInputRepository()

    async def create(self, user: User, request: HealthInputCreateRequest) -> HealthInputResponse:
        snapshot = await self.repo.create(
            user_id=user.id,
            input_values=request.input_values,
            units=request.units,
            source=request.source,
            measured_at=request.measured_at,
        )
        # ⚠️ 2026-09-10 추가 - WAIST_DIAGNOSIS_2026-09-10.md가 지적했던 "입력 저장 →
        # 모델 실행"의 빈 연결고리. 실패해도 신체정보 저장 자체(위 create)는 이미
        # 끝났으니 그대로 성공 응답함 - 허리둘레 계산 실패가 신체정보 저장을 막으면 안 됨.
        try:
            await WaistEstimateService().recompute_and_save(user, snapshot.id)
        except Exception:  # noqa: BLE001 - 신체정보 저장 자체엔 영향 주지 않음
            pass
        return HealthInputResponse.model_validate(snapshot)

    async def get_latest(self, user: User) -> HealthInputResponse:
        snapshot = await self.repo.get_latest(user.id)
        if snapshot is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="입력된 신체정보가 없습니다.")
        return HealthInputResponse.model_validate(snapshot)

    async def get_history(self, user: User) -> list[HealthInputResponse]:
        snapshots = await self.repo.get_history(user.id)
        return [HealthInputResponse.model_validate(s) for s in snapshots]
