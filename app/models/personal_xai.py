"""로그인 계정별 주간 XAI 결과. 과거에 계산하지 않은 점수를 소급 생성하지 않는다."""

import uuid

from tortoise import fields, models


class WeeklyXaiSnapshot(models.Model):
    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="weekly_xai_snapshots")
    week_start = fields.DateField()
    observed_on = fields.DateField()
    computed_at = fields.DatetimeField()
    snapshot = fields.JSONField()

    class Meta:
        table = "weekly_xai_snapshots"
        unique_together = (("user", "week_start"),)
