from app.dtos.accessibility import AccessibilityResponse, AccessibilityUpdateRequest
from app.models.users import User
from app.repositories.accessibility_repository import AccessibilityRepository


class AccessibilityService:
    def __init__(self):
        self.repo = AccessibilityRepository()

    async def get_current(self, user: User) -> AccessibilityResponse:
        instance = await self.repo.get_or_create(user.id)
        return AccessibilityResponse.model_validate(instance)

    async def update(self, user: User, request: AccessibilityUpdateRequest) -> AccessibilityResponse:
        instance = await self.repo.get_or_create(user.id)
        updated = await self.repo.update(
            instance,
            large_controls=request.large_controls,
            reduced_motion=request.reduced_motion,
            preferred_text_scale_hint=request.preferred_text_scale_hint,
            senior_mode=request.senior_mode,
        )
        return AccessibilityResponse.model_validate(updated)
