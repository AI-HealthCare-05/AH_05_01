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
    health_input_snapshot = fields.ForeignKeyField("models.HealthInputSnapshot", related_name="assessment_jobs")
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
    "틈틈지수" 하나를 나타내는 테이블로 추정. (테이블명은 명세에 명시 안 됨, 관례상 추정)

    ⚠️ 2026-09-15 추가 - 틈튼지수 vNext(TuntunScorePeerService)의 결과도 이 테이블에
    남기도록 확장. 문홍주 팀장님(SHAP/XAI) 요청: "전후 비교(지난주 대비)는 model/
    calibration/input(aggregation)/background/explainer 5종 버전이 모두 같을 때만
    표시해야 한다 - 하나라도 다르면 기준이 다른 값끼리의 비교가 된다." 지금은 브릿지
    응답에 model_version·formula_version 2개만 있고 나머지 3개는 모델 쪽에서 아직
    안 내려줌(calibration_version은 모델 쪽에서도 확인 불가 상태) - 그래서 전부
    nullable로 두고, 모델 쪽이 값을 내려주는 대로 채워지게 설계함. 5개 전부 채워지기
    전까지는 이 테이블로 "지난주 대비" 비교를 만들면 안 됨(버전 검증 없이 비교하는 것
    자체가 금지 사항 - approved_claims.json의 reject.background_diff_as_change 참고).
    """

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="tmtn_index_results")
    source_result_ids = fields.JSONField()  # 이 지수를 구성한 assessment_results id 목록
    calculated_at = fields.DatetimeField(auto_now_add=True)
    display_state = fields.CharField(max_length=20)  # 예: VISIBLE / HIDDEN / PENDING
    composite_formula_version = fields.CharField(max_length=30)  # 종합 공식(가중치 등)의 버전

    # ⚠️ vNext 전용 - 위 3개 필드(assessment_results 기반 Mock 지수)와는 다른 값 체계라
    # 이 5개는 vNext 결과를 기록할 때만 채워짐(구 Mock 지수 기록에는 전부 null로 남음).
    reference_date = fields.DateField(null=True)  # 이 결과가 어느 날짜 기준 조회였는지
    peer_input_revision = fields.CharField(max_length=140, null=True)  # 신체정보+운동습관 스냅샷 조합
    peer_model_version = fields.CharField(max_length=60, null=True)
    peer_calibration_version = fields.CharField(max_length=60, null=True)
    peer_aggregation_version = fields.CharField(max_length=60, null=True)  # 운동 집계 "규칙"의 버전(Q6 정책 확정 후)
    peer_background_id = fields.CharField(max_length=120, null=True)
    peer_explainer_version = fields.CharField(max_length=60, null=True)

    class Meta:
        table = "tmtn_index_results"


class InitialHabitSnapshot(models.Model):
    """⚠️ 2026-09-15 신규, 2026-09-16 갱신 - 강호님(모델) "초기습관_산식과_모델표시_확정_v1"
    반영. 가입 설문(신체정보+운동습관) 기준 "초기 습관 점수"를 최초 1회만 계산해서
    고정한다. 이후 미션 완료·추가 운동·일상적인 재조회로 이 값을 절대 덮어쓰지 않음
    (user OneToOneField로 "사용자당 1개, 재생성 시도하면 UNIQUE 위반"을 DB 레벨에서 강제).

    ⚠️ 세부 배점(저강도20·중고강도50·근력30)은 확정돼서 계산 자체는 준비됐지만, "가입
    당시 완성 설문"을 정확히 식별하는 방법(온보딩이 신체정보+운동습관을 어떻게
    저장하는지)이 아직 확인 전이라 policy_status는 계속 'pending'으로 유지 중 - 확인
    되는 대로 practice_score_service.py의 계산 호출부만 마저 연결하면 됨(모델 필드는
    이미 준비). "서로 무관한 가장 오래된 두 행을 가입 당시로 단정하지 말라"는 원칙
    때문에, 확인 전까지는 절대 policy_status='approved'로 실제 점수를 채우지 않음."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.OneToOneField("models.User", related_name="initial_habit_snapshot")
    input_revision = fields.CharField(max_length=140)  # 가입 시점 신체정보+운동습관 스냅샷 조합
    # ⚠️ 2026-09-16 추가 - initial_habit.py의 DEFINITION('leisure_bouts10_strength_days_v1')
    # 과 일치해야 계산이 통과함. 이 정의(10분 이상 지속 활동 기준)가 실제 온보딩 설문
    # 문항과 일치하는지는 별도 확인 필요 - 확인 전까지는 이 필드도 비워둠(null).
    input_definition = fields.CharField(max_length=60, null=True)
    formula_version = fields.CharField(max_length=60)  # 'pending-selection' - 확인 전
    policy_status = fields.CharField(max_length=20)  # 'pending' | 'approved'
    score = fields.FloatField(null=True)
    # ⚠️ 2026-09-16 추가 - 항목별 기여도(low/moderate_vigorous/strength) 보존. 화면에서
    # "왜 이 점수인지" 보여줄 때, 그리고 나중에 배점 정책이 바뀔 때 감사 근거로 씀.
    contributions = fields.JSONField(null=True)
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "initial_habit_snapshots"


class InitialHabitConversionAudit(models.Model):
    """⚠️ 2026-09-16 신규 - 기존 pending 사용자를 일괄 전환할 때의 감사 기록. 강호님
    문서 §5 요구사항: "이전 pending/새 점수/산식/변경 시각/이유를 감사 기록으로
    남기고 트랜잭션과 유일 제약으로 이중 생성을 막는다." InitialHabitSnapshot 자체는
    user당 1개뿐이라(OneToOne) 전환 이력이 그 안에 안 남으니, 별도 테이블로 보존."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="initial_habit_conversion_audits")
    previous_policy_status = fields.CharField(max_length=20)
    previous_score = fields.FloatField(null=True)
    previous_formula_version = fields.CharField(max_length=60)
    new_policy_status = fields.CharField(max_length=20)
    new_score = fields.FloatField(null=True)
    new_formula_version = fields.CharField(max_length=60)
    new_contributions = fields.JSONField(null=True)
    reason = fields.CharField(max_length=60)  # 예: 'SIGNUP_SOURCE_CONFIRMED_BACKFILL'
    converted_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "initial_habit_conversion_audits"
