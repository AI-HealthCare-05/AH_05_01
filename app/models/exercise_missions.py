"""틈새 운동(exercise mission) - TMtn_UI_V17 FEATURE_RULES.md §5 반영.

⚠️ 2026-09-11 신규 - "오늘의 카드" 완료 뒤 추가로 할 수 있는 가벼운 운동. 하루 최대 2회,
완료당 재료 1개. 기존 Challenge/DailyCardSelection(1:1 관계)과는 별개 테이블로 둔다 -
여기에 억지로 끼워 넣으면 "카드 실천일"과 "카드첩 장수"의 의미가 깨진다(문서 원칙:
"추가 운동 완료 횟수를 카드 실천일이나 카드첩 장수로 더하지 않는다").

exercise_mission_catalog: MissionTemplateVersion을 그대로 참조 - 제목/가이드/exec_type/
목표/재료는 새로 안 만들고 기존 템플릿에서 가져온다. 이 테이블은 "그중 어떤 걸 틈새
운동으로 노출할지"만 관리한다.

exercise_mission_sessions: Challenge와 거의 같은 구조(타이머/카운터 진행 필드, 낙관적
잠금용 version)를 재사용 - 측정 검증 로직을 그대로 쓰기 위해서다. reward_slot(1 또는 2,
미지급이면 null)로 "몇 번째 보상인지"를 표시하고, UNIQUE(user, service_date, reward_slot)로
같은 날 같은 슬롯이 중복 지급되는 걸 DB 레벨에서 막는다(NULL은 유니크 제약에서 여러 개
허용되므로 미지급 세션은 여러 개 있어도 된다).
"""

import uuid
from enum import StrEnum

from tortoise import fields, models


class ExerciseMissionSessionState(StrEnum):
    ACTIVE = "ACTIVE"
    PAUSED = "PAUSED"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"


class ExerciseMissionCatalog(models.Model):
    """"오늘 틈새 운동으로 고를 수 있는 것" 목록. template_version 1개당 카탈로그 항목 1개
    (같은 템플릿을 여러 번 등록하지 않음 - unique)."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    template_version = fields.ForeignKeyField(
        "models.MissionTemplateVersion", related_name="exercise_catalog_entries", unique=True
    )
    is_active = fields.BooleanField(default=True)
    display_order = fields.IntField(default=0)
    created_at = fields.DatetimeField(auto_now_add=True)
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "exercise_mission_catalog"


class ExerciseMissionSession(models.Model):
    """사용자가 실제로 시작한 틈새 운동 1회. Challenge와 같은 진행 필드 구조를 재사용해서
    기존 측정 검증 함수(목표 달성 확인, 시간 단위 변환)를 그대로 쓸 수 있게 한다."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="exercise_mission_sessions")
    service_date = fields.DateField()  # KST 기준 - app.core.time_utils.service_today() 재사용
    catalog_entry = fields.ForeignKeyField("models.ExerciseMissionCatalog", related_name="sessions")
    # 시작 시점의 제목/exec_type/목표/재료를 스냅샷으로 고정 - Challenge.mission_snapshot과 동일한 이유
    # (그 뒤 카탈로그·템플릿이 바뀌어도 이미 시작한 세션의 판정 기준은 안 바뀌어야 함).
    template_snapshot = fields.JSONField()
    state = fields.CharEnumField(enum_type=ExerciseMissionSessionState, default=ExerciseMissionSessionState.ACTIVE)

    # --- 타이머/카운터 진행 필드 - Challenge와 동일한 구조(재사용 목적) ---
    target_duration_seconds = fields.IntField(null=True)
    accumulated_duration_seconds = fields.IntField(default=0)
    started_at = fields.DatetimeField(null=True)
    last_paused_at = fields.DatetimeField(null=True)
    target_count = fields.IntField(null=True)
    accumulated_count = fields.IntField(default=0)
    last_synced_at = fields.DatetimeField(null=True)

    version = fields.IntField(default=1)  # 낙관적 잠금(optimistic lock)

    completed_at = fields.DatetimeField(null=True)
    awarded_at = fields.DatetimeField(null=True)
    # 1 또는 2 = 지급 확정(몇 번째 보상인지), null = 아직 미지급. 서버에서 1~2 범위만 허용.
    reward_slot = fields.IntField(null=True)
    # POST /exercise-mission-sessions/{id}/complete 요청의 중복 방지용 - Challenge의
    # idempotency_key와 같은 목적.
    idempotency_key = fields.CharField(max_length=64, null=True, unique=True)

    created_at = fields.DatetimeField(auto_now_add=True)
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "exercise_mission_sessions"
        # NULL은 MySQL UNIQUE 제약에서 "다른 값"으로 취급되어 여러 개 허용됨 - 그래서
        # 미지급(reward_slot=null) 세션은 하루에 여러 개 있어도 되고, 지급 확정된(1 또는 2)
        # 세션만 사용자·날짜당 슬롯별로 하나씩만 존재하게 된다.
        unique_together = (("user", "service_date", "reward_slot"),)
