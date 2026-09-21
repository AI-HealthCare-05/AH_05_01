"""계정 · 세션 · 동의 도메인
ERD 문서 CORE: sessions, user_consents (+ deletion_jobs)

User 자체는 기존 app/models/users.py의 User를 그대로 사용합니다 (BigIntField 자동증가 PK).
Tortoise ORM은 ForeignKeyField를 걸 때 참조 모델의 PK 타입을 자동으로 따라가므로,
아래 user_id 관련 컬럼들은 CHAR(36)이 아니라 자동으로 BIGINT로 생성됩니다.

⚠️ 확인 필요: 기존 User 모델의 `birthday`(전체 생년월일)와 `phone_number`, `gender`, `is_admin`은
TMTN 원칙(README: "주민등록번호, 전체 생년월일, 원시 정밀 위치처럼 불필요한 데이터는 수집하지 않습니다")과
정면으로 배치됩니다. PK 타입은 그대로 둬도 되지만, 이 필드들은 팀 회의에서 한 번 짚고 넘어가는 걸 추천드려요.
"""

import uuid
from enum import StrEnum

from tortoise import fields, models


class SessionRevokeReason(StrEnum):
    LOGOUT = "LOGOUT"
    EXPIRED = "EXPIRED"
    ACCOUNT_DELETION = "ACCOUNT_DELETION"
    SECURITY = "SECURITY"


class Session(models.Model):
    """문서: sessions는 user_id·expires_at·revoked_at·last_seen_at을 저장한다 (기능명세서 §1.5 근거).
    device_id/refresh_token_hash/fcm_token은 명세에 명시되진 않았지만 표준 인증 흐름상 필요해 유지."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="sessions")
    device_id = fields.CharField(max_length=100, null=True)
    refresh_token_hash = fields.CharField(max_length=255)
    fcm_token = fields.CharField(max_length=255, null=True)  # 푸시 발송용, nullable
    issued_at = fields.DatetimeField(auto_now_add=True)
    expires_at = fields.DatetimeField()  # 기능명세서 §1.5 확인됨
    last_seen_at = fields.DatetimeField(auto_now=True)  # 기능명세서 §1.5 확인됨
    revoked_at = fields.DatetimeField(null=True)
    revoke_reason = fields.CharEnumField(enum_type=SessionRevokeReason, null=True)

    class Meta:
        table = "sessions"


class EmailVerificationRequest(models.Model):
    """기능명세서 §1.4 확인됨. 34개 ERD 요약에는 없었지만 실제 스펙엔 명시된 테이블.
    이메일 인증번호 검증 흐름(가입 시 이메일 소유 확인)을 위한 테이블."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    request_id = fields.UUIDField(unique=True, default=uuid.uuid4)  # 클라이언트가 요청 추적에 사용하는 별도 식별자
    email_normalized = fields.CharField(max_length=255, index=True)  # 대소문자 정규화된 이메일
    code_hash = fields.CharField(max_length=255)  # 인증코드는 평문 저장 금지, 해시로 저장
    expires_at = fields.DatetimeField()
    verified_at = fields.DatetimeField(null=True)
    attempt_count = fields.IntField(default=0)  # 코드 입력 시도 횟수(무차별 대입 방지용)
    resend_count = fields.IntField(default=0)  # 재발송 횟수(남발 방지용)
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "email_verification_requests"


class PasswordResetToken(models.Model):
    """기능명세서 §1.5 확인됨. 34개 ERD 요약에는 없었지만 실제 스펙엔 명시된 테이블."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    token_hash = fields.CharField(max_length=255, unique=True)  # 재설정 토큰도 해시로 저장
    user = fields.ForeignKeyField("models.User", related_name="password_reset_tokens")
    expires_at = fields.DatetimeField()
    used_at = fields.DatetimeField(null=True)
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "password_reset_tokens"


class ConsentStatus(StrEnum):
    AGREED = "AGREED"
    WITHDRAWN = "WITHDRAWN"


class ConsentPurpose(StrEnum):
    """문서: "법적 게이트 · 이력형". 실제 약관 목록은 팀 기획안(V.1) 기준으로 맞춰야 함.

    ⚠️ 2026-08-27 Figma 핸드오프(A06 화면) 대조 중 발견: "만 14세 이상입니다"가 필수 동의
    3종 중 하나로 명시되어 있는데 enum에 없었음(AGE_OVER_14 추가)."""

    TERMS_OF_SERVICE = "TERMS_OF_SERVICE"
    PRIVACY_POLICY = "PRIVACY_POLICY"
    AGE_OVER_14 = "AGE_OVER_14"
    NON_DIAGNOSTIC_NOTICE = "NON_DIAGNOSTIC_NOTICE"
    HEALTH_DATA_USAGE = "HEALTH_DATA_USAGE"  # ⚠️ 2026-09-02 추가: 건강정보(키·몸무게·운동습관)
    # "수집·이용" 자체 - 개인정보보호법 §23 민감정보라 다른 필수동의와 절대 합치면 안 됨(필수)
    LOCATION_DATA_USAGE = "LOCATION_DATA_USAGE"  # ⚠️ 2026-09-02 추가: 위치정보법 §15, 선택
    HEALTH_REFERENCE_ANALYSIS = "HEALTH_REFERENCE_ANALYSIS"  # "틈튼지수 산출을 위한 분석" - 선택
    NOTIFICATION = "NOTIFICATION"


class UserConsent(models.Model):
    """문서: append-only, 법적 게이트. row 삭제 없이 withdrawn_at만 기록."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="consents")
    purpose = fields.CharEnumField(enum_type=ConsentPurpose)
    document_version = fields.CharField(max_length=20)
    status = fields.CharEnumField(enum_type=ConsentStatus, default=ConsentStatus.AGREED)
    agreed_at = fields.DatetimeField(auto_now_add=True)
    withdrawn_at = fields.DatetimeField(null=True)

    class Meta:
        table = "user_consents"
        unique_together = (("user", "purpose", "document_version"),)


class DeletionJobStatus(StrEnum):
    REQUESTED = "REQUESTED"
    PROCESSING = "PROCESSING"
    COMPLETED = "COMPLETED"


class DeletionJob(models.Model):
    """문서 §9: user_id에 FK를 안 걸어둔 게 의도적(삭제 후에도 감사기록 남기려고).
    기능명세서 §1.3 확인: user_id·status·requested_at·completed_at·policy_version·failure_code."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user_id = fields.UUIDField()  # 의도적으로 FK 아님 (문서 §9)
    status = fields.CharEnumField(enum_type=DeletionJobStatus, default=DeletionJobStatus.REQUESTED)
    requested_at = fields.DatetimeField(auto_now_add=True)
    completed_at = fields.DatetimeField(null=True)
    policy_version = fields.CharField(max_length=20)  # 기능명세서에서 확인됨
    failure_code = fields.CharField(max_length=50, null=True)  # 기능명세서에서 확인됨

    class Meta:
        table = "deletion_jobs"


class DeletionJobItemAction(StrEnum):
    """기능명세서 §1.3: "삭제 작업 항목" — 데이터 카테고리별 삭제 진행상황을 추적하는 하위 테이블.
    34개 ERD 요약에는 이름이 없었지만 실제 스펙엔 명시됨."""

    ANONYMIZE = "ANONYMIZE"
    HARD_DELETE = "HARD_DELETE"
    RETAIN_FOR_AUDIT = "RETAIN_FOR_AUDIT"


class DeletionJobItem(models.Model):
    """기능명세서 §1.3 확인: job_id·data_category·action·status·completed_at.
    (테이블명은 스펙에 명시되지 않아 관례상 deletion_job_items로 추정)"""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    job = fields.ForeignKeyField("models.DeletionJob", related_name="items")
    data_category = fields.CharField(max_length=50)  # 예: health_input, challenge_history 등
    action = fields.CharEnumField(enum_type=DeletionJobItemAction)
    status = fields.CharField(max_length=20, default="PENDING")
    completed_at = fields.DatetimeField(null=True)

    class Meta:
        table = "deletion_job_items"
