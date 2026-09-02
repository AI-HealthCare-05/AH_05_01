"""건강 참고 분석 도메인
ERD 문서 CORE: assessment_jobs, assessment_results (+ model_releases, 문서 3장 다이어그램에 등장)

⚠️ "탈로스 필수 요구사항"이라 제거 불가 (문서 §1, §7). 컬럼 상세는 문서에 없어
이전 TMTN 논의(당뇨/고혈압 참고 확률 + 코호트 근거 분리)를 기준으로 추정.
"""

import uuid
from enum import StrEnum

from tortoise import fields, models


class ModelRelease(models.Model):
    """문서 3장 다이어그램: "model_releases ||--o{ assessment_jobs : 모델 버전".
    분석에 쓰인 모델 버전을 감사 추적하기 위한 테이블로 추정."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    model_version = fields.CharField(max_length=30, unique=True)
    released_at = fields.DatetimeField(auto_now_add=True)
    is_active = fields.BooleanField(default=True)  # 현재 서비스에 쓰이는 버전인지

    class Meta:
        table = "model_releases"


class AssessmentJobStatus(StrEnum):
    PENDING = "PENDING"
    DONE = "DONE"
    FAILED = "FAILED"


class AssessmentJob(models.Model):
    """문서: health_input_snapshots ||--o{ assessment_jobs (입력 스냅샷 근거)."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="assessment_jobs")
    health_input_snapshot = fields.ForeignKeyField(
        "models.HealthInputSnapshot", related_name="assessment_jobs"
    )
    model_release = fields.ForeignKeyField("models.ModelRelease", related_name="assessment_jobs")
    status = fields.CharEnumField(enum_type=AssessmentJobStatus, default=AssessmentJobStatus.PENDING)
    created_at = fields.DatetimeField(auto_now_add=True)


    class Meta:
        table = "assessment_jobs"


class AccessStatus(StrEnum):
    """문서 §6: 동의 철회 트랜잭션에서 assessment_results.access_status가 바뀐다고 언급됨
    → 동의 철회 시 과거 분석결과 접근을 막는 용도로 추정."""

    ACCESSIBLE = "ACCESSIBLE"
    RESTRICTED = "RESTRICTED"  # 동의 철회 후 접근 제한


class AssessmentResult(models.Model):
    """문서: assessment_jobs ||--|| assessment_results (1:1), uq_result_per_job으로 중복 방지.
    기능명세서 §9.2.1 확인: "assessment_results는 job_id·score·band·factors·model_version·
    calibration_version·copy_version을 저장한다." (1차 추정의 diabetes_reference_percent 등에서 전면 교체)
    질환별로 여러 row가 생기는 구조로 추정(하나의 job에 질환별 score/band가 각각 있을 수 있음
    → 이 경우 disease 필드를 uq_result_per_job에 포함시켜야 함, 팀 확인 필요)."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    job = fields.OneToOneField("models.AssessmentJob", related_name="result")  # uq_result_per_job
    score = fields.DecimalField(max_digits=5, decimal_places=2)  # 참고 점수(0~100 등, 팀 스케일 확인 필요)
    band = fields.CharField(max_length=20)  # 예: LOW / MODERATE / HIGH 등급
    factors = fields.JSONField(null=True)  # 기여 요인 목록, 예: [{"name": "waist_cm", "rank": 1}]
    model_version = fields.CharField(max_length=30)
    calibration_version = fields.CharField(max_length=30, null=True)
    copy_version = fields.CharField(max_length=30, null=True)  # 결과 설명 문구(안전 문구 등)의 버전
    access_status = fields.CharEnumField(enum_type=AccessStatus, default=AccessStatus.ACCESSIBLE)
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "assessment_results"


class TmtnIndexResult(models.Model):
    """34개 ERD 요약에는 이름이 없었지만, 기능명세서 §6(틈틈지수) 확인:
    "index view model은 source_result_ids·calculated_at·display_state·
    composite_formula_version을 포함한다." 여러 assessment_results를 종합해 만드는
    "틈틈지수" 하나를 나타내는 테이블로 추정. (테이블명은 명세에 명시 안 됨, 관례상 추정)"""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="tmtn_index_results")
    source_result_ids = fields.JSONField()  # 이 지수를 구성한 assessment_results id 목록
    calculated_at = fields.DatetimeField(auto_now_add=True)
    display_state = fields.CharField(max_length=20)  # 예: VISIBLE / HIDDEN / PENDING
    composite_formula_version = fields.CharField(max_length=30)  # 종합 공식(가중치 등)의 버전

    class Meta:
        table = "tmtn_index_results"
