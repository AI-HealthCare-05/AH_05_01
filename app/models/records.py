"""기록 캘린더(D01~D05) 도메인 신규 모델.

핵심 아이디어: 하루의 "완료/미완료" 상태는 daily_card_sets -> selection -> challenges에서
이미 다 알 수 있어서(하루에 챌린지 1개), 별도 캐시 테이블 없이 그때그때 조회하면 됨.
근데 "쉼으로 표시" · "메모"는 사용자가 직접 입력하는 값이라 어디서도 유도가 안 되니,
이 둘만 담는 작은 테이블을 새로 만듦.
"""

import uuid

from tortoise import fields, models


class DailyRecordNote(models.Model):
    """하루 단위 부가 정보. append-only가 아니라 "그날의 현재 상태" 하나만 있으면 되는
    성격이라(수정 가능해야 함 — 메모 고쳐쓰기, 쉼 취소 등) 다른 테이블들과 다르게
    UPDATE를 허용하는 구조로 설계함 (profile_accessibility, notification_settings와 같은 패턴)."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="daily_record_notes")
    service_date = fields.DateField()
    is_rest_day = fields.BooleanField(default=False)  # D05: "쉼" 표시 여부
    memo = fields.TextField(null=True)  # D03: "내 메모"
    created_at = fields.DatetimeField(auto_now_add=True)
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "daily_record_notes"
        unique_together = (("user", "service_date"),)
