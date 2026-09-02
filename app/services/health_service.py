from fastapi import HTTPException, status

from app.dtos.health import HealthInputCreateRequest, HealthInputResponse
from app.models.users import User
from app.repositories.health_repository import HealthInputRepository


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
        return HealthInputResponse.model_validate(snapshot)

    async def get_latest(self, user: User) -> HealthInputResponse:
        snapshot = await self.repo.get_latest(user.id)
        if snapshot is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="입력된 신체정보가 없습니다.")
        return HealthInputResponse.model_validate(snapshot)

    async def get_history(self, user: User) -> list[HealthInputResponse]:
        snapshots = await self.repo.get_history(user.id)
        return [HealthInputResponse.model_validate(s) for s in snapshots]
