from app.models.notifications import ProfileAccessibility


class AccessibilityRepository:
    def __init__(self):
        self._model = ProfileAccessibility

    async def get_or_create(self, user_id) -> ProfileAccessibility:
        instance, _ = await self._model.get_or_create(user_id=user_id)
        return instance

    async def update(self, instance: ProfileAccessibility, **fields) -> ProfileAccessibility:
        """None이 아닌 값만 실제로 반영 (부분 업데이트)."""

        update_fields = []
        for key, value in fields.items():
            if value is not None:
                setattr(instance, key, value)
                update_fields.append(key)

        if update_fields:
            update_fields.append("updated_at")
            await instance.save(update_fields=update_fields)
        return instance
