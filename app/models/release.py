"""릴리즈 승인 · QA · 모델 감사 도메인 (OPTIONAL, 문서 §7)
ERD 문서 OPTIONAL: release_approvals, qa_release_checks, model_audit_logs

이 세 테이블은 "제품 기능"이 아니라 "팀/운영 프로세스" 성격이라, 컬럼 추정이 특히 불확실합니다.
실제로 어떤 승인 절차(role_key가 뭘 의미하는지 등)를 쓰실지 팀장님과 직접 확인하시는 걸 추천드려요.
"""

import uuid
from enum import StrEnum

from tortoise import fields, models


class ReleaseApprovalStatus(StrEnum):
    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


class ReleaseApproval(models.Model):
    """OPTIONAL. uq_approval_per_role — 같은 release에 같은 role이 중복 승인 못 함(문서 §5).
    ⚠️ 컬럼명 "role" → MySQL 8 키워드라 문서(§8)대로 role_key로 개명."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    release_id = fields.UUIDField()  # 어떤 릴리즈 단위를 가리키는지 문서에 명시 없음, 추정
    role_key = fields.CharField(max_length=30)  # 문서 §8: role → role_key로 개명
    status = fields.CharEnumField(enum_type=ReleaseApprovalStatus, default=ReleaseApprovalStatus.PENDING)
    approved_at = fields.DatetimeField(null=True)

    class Meta:
        table = "release_approvals"
        unique_together = (("release_id", "role_key"),)  # uq_approval_per_role


class QaReleaseCheck(models.Model):
    """OPTIONAL. 컬럼 상세 전혀 없음 — QA 체크리스트 항목별 통과여부 정도로 최소 추정."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    release_id = fields.UUIDField()
    check_name = fields.CharField(max_length=100)
    passed = fields.BooleanField(default=False)
    checked_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "qa_release_checks"


class ModelAuditLog(models.Model):
    """OPTIONAL. AI 모델(assessment 등) 배포/변경 이력 감사 로그로 추정."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    model_release = fields.ForeignKeyField("models.ModelRelease", related_name="audit_logs")
    action = fields.CharField(max_length=50)  # DEPLOYED / ROLLED_BACK 등
    actor = fields.CharField(max_length=100, null=True)
    logged_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "model_audit_logs"
