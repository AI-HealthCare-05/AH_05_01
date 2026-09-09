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
    # ⚠️ 2026-09-07 반영: 상태전이 정책(G3) - 카드를 아직 뽑지 않아 challenge 자체가
    # 없는 날(B18 등)은 "포기"를 기록할 곳이 challenge.state=SKIPPED 말고는 없었음.
    # is_rest_day와 대칭되는 이 필드로, challenge 유무와 무관하게 "그날을 포기함"을
    # 표시할 수 있게 함. REST<->GIVE_UP 상호 전환 시 record_service에서 관리.
    is_given_up = fields.BooleanField(default=False)  # 카드 미선택 상태에서의 "포기" 표시 여부
    memo = fields.TextField(null=True)  # D03: "내 메모"
    created_at = fields.DatetimeField(auto_now_add=True)
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "daily_record_notes"
        unique_together = (("user", "service_date"),)
