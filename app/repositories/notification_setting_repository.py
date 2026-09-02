from app.models.notifications import NotificationSetting


class NotificationSettingRepository:
    def __init__(self):
        self._model = NotificationSetting

    async def get_or_create(self, user_id) -> NotificationSetting:
        instance, _ = await self._model.get_or_create(user_id=user_id)
        return instance

    async def update(self, instance: NotificationSetting, **fields) -> NotificationSetting:
        update_fields = []
        for key, value in fields.items():
            if value is not None:
                setattr(instance, key, value)
                update_fields.append(key)
        if update_fields:
            update_fields.append("updated_at")
            await instance.save(update_fields=update_fields)
        return instance
