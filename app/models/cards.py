"""오늘의 카드 도메인
ERD 문서 CORE: daily_card_sets, card_options, daily_card_selections
"""

import uuid

from tortoise import fields, models


class DailyCardSet(models.Model):
    """문서: "service_date당 1세트". unique_together로 강제."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="daily_card_sets")
    service_date = fields.DateField()  # "하루" 기준. 자정이 아닐 수 있어 서버 로직에서 결정
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "daily_card_sets"
        unique_together = (("user", "service_date"),)  # uq_set_per_day


class CardOption(models.Model):
    """문서: "세트당 정확히 3개". mission_template_versions를 참조하는 원본 스냅샷.
    ⚠️ 컬럼명 "position"은 MySQL에서 함수명과 겹쳐서 문서(§8)대로 option_index로 개명."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    card_set = fields.ForeignKeyField("models.DailyCardSet", related_name="options")
    mission_template_version = fields.ForeignKeyField("models.MissionTemplateVersion", related_name="card_options")
    option_index = fields.IntField()  # 1~3, 문서 §8: position → option_index로 개명
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "card_options"
        unique_together = (("card_set", "option_index"),)  # uq_option_index


class DailyCardSelection(models.Model):
    """문서: "winner 1개". 세트당 정확히 하나만 확정될 수 있음.
    동시 확정 경합은 uq_winner_per_set 하나로 처리(문서 §5 — 분산 락 불필요).
    OneToOneField를 쓰면 유니크 제약이 자동으로 걸려서 unique_together보다 명확함."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    card_set = fields.OneToOneField("models.DailyCardSet", related_name="selection")  # uq_winner_per_set
    card_option = fields.ForeignKeyField("models.CardOption", related_name="selection")
    selected_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "daily_card_selections"
