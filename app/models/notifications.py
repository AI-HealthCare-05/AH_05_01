"""알림 · 접근성 · 기록캐시 도메인 (OPTIONAL, ERD 문서 §7)
ERD 문서 OPTIONAL: notification_settings, fcm_device_tokens, profile_accessibility, daily_action_summary

notification_settings, profile_accessibility, daily_action_summary는
기능명세서(2026-08-24)의 "데이터:" 항목을 근거로 필드를 확정했습니다.
"""

import uuid

from tortoise import fields, models


class NotificationSetting(models.Model):
    """기능명세서 §1(마이·설정) 확인: "notification_settings는 user_id·timezone·slots·
    weekdays·quiet_hours·enabled·updated_at을 저장한다." (1차 추정의 setting_type 등에서 교체)
    사용자당 1 row (알림 종류별로 나누지 않고, slots로 여러 시간대를 한 row에 표현)."""

    user = fields.OneToOneField("models.User", related_name="notification_setting", pk=True)
    timezone = fields.CharField(max_length=50, default="Asia/Seoul")
    slots = fields.JSONField(default=list)  # 예: ["08:00", "12:30", "20:00"]
    weekdays = fields.JSONField(default=list)  # 예: ["MON", "TUE", "WED", "THU", "FRI"]
    quiet_hours = fields.JSONField(null=True)  # 예: {"start": "22:00", "end": "07:00"}
    enabled = fields.BooleanField(default=True)
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "notification_settings"


class FcmDeviceToken(models.Model):
    """ERD 문서 §6: 계정 삭제 수락 시 sessions와 함께 동기적으로 revoke됨.
    실제 구현하신 device_tokens.py(DeviceToken)의 필드를 이식 — platform 필드 추가,
    테이블명은 ERD 공식 이름(fcm_device_tokens)으로 통일 (기존엔 device_tokens)."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="fcm_device_tokens")
    token = fields.CharField(max_length=255, unique=True)  # 기존 device_tokens.fcm_token
    platform = fields.CharField(max_length=10, default="ANDROID")  # 기존에서 이식, 추후 iOS 확장 대비
    is_revoked = fields.BooleanField(default=False)  # 기존 device_tokens.is_active를 반전(의미 통일)
    registered_at = fields.DatetimeField(auto_now_add=True)
    revoked_at = fields.DatetimeField(null=True)

    class Meta:
        table = "fcm_device_tokens"


class ProfileAccessibility(models.Model):
    """기능명세서 §1(마이·설정) 확인: "profile_accessibility는 user_id·large_controls·
    reduced_motion·preferred_text_scale_hint·updated_at을 저장한다."
    명세에 명시적으로 "보조기기 사용 여부는 저장하지 않는다"고 되어있어 그 필드는 만들지 않음.

    ⚠️ v3(2026-08-26) 추가: senior_mode. 이건 "UI 글자 크기 키우기" 같은 접근성 설정이 아니라
    "이 사용자에게 시니어 비안전(senior_safe=False) 미션을 카드 후보에서 제외할지" 판단용으로
    새로 필요해진 필드. 기능명세서엔 없던 필드라 팀 확인 후 명세 문서도 같이 업데이트할 것."""

    user = fields.OneToOneField("models.User", related_name="accessibility", pk=True)
    large_controls = fields.BooleanField(default=False)
    reduced_motion = fields.BooleanField(default=False)
    preferred_text_scale_hint = fields.CharField(max_length=20, null=True)  # 예: "LARGE", "XLARGE"
    senior_mode = fields.BooleanField(default=False)  # True면 mission_template_versions.senior_safe=False 제외
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "profile_accessibility"


class DailyActionSummary(models.Model):
    """ERD 문서 §2: "파생 테이블은 캐시입니다. 원본을 덮어쓰지 않고 언제든 재계산".
    기능명세서 §7(기록) 확인: "daily_action_summary는 service_date·status·card_title·
    completed_at·five_element를 이벤트에서 계산한다." (1차 추정의 completed_count 등에서 교체)
    원본은 challenge_events이고, 이 테이블은 재계산 가능한 집계 캐시일 뿐."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="daily_action_summaries")
    service_date = fields.DateField()
    status = fields.CharField(max_length=20)  # 예: COMPLETED / SKIPPED / EXPIRED
    card_title = fields.CharField(max_length=200)  # 그날 확정된 카드의 제목(스냅샷)
    five_element = fields.CharField(max_length=10, null=True)
    completed_at = fields.DatetimeField(null=True)
    generated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "daily_action_summary"
        unique_together = (("user", "service_date"),)
