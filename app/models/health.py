"""신체입력 · 미션 템플릿 도메인
ERD 문서 CORE: health_input_snapshots, mission_template_versions
"""

import uuid
from enum import StrEnum

from tortoise import fields, models


class HealthInputSnapshot(models.Model):
    """문서: "예측 모델 입력 · append-only".
    기능명세서 §2 확인: user_id·measured_at·values·units·source·created_at.
    ⚠️ ERD 문서 §8: "values" → MySQL 예약어라 실제 컬럼명은 input_values로 개명됨.
    고정 컬럼(waist_cm, height_cm...)이 아니라 유연한 key-value 구조로 설계된 것으로 확인."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="health_input_snapshots")
    measured_at = fields.DatetimeField()  # 사용자가 실제로 측정/입력한 시점
    input_values = fields.JSONField()  # 예: {"height_cm": 170, "weight_kg": 65, "waist_cm": null}
    units = fields.JSONField()  # 예: {"height_cm": "cm", "weight_kg": "kg"}
    source = fields.CharField(max_length=20, default="MANUAL")  # MANUAL / ESTIMATED / HEALTH_CONNECT
    created_at = fields.DatetimeField(auto_now_add=True)  # append-only, UPDATE 금지

    class Meta:
        table = "health_input_snapshots"


class StrengthIntensity(StrEnum):
    LIGHT = "LIGHT"  # 가볍게
    MODERATE = "MODERATE"  # 적당히
    HARD = "HARD"  # 힘들게


class ExerciseHabitSnapshot(models.Model):
    """v2(2026-08-27) 신규: 온보딩 A08(운동습관 입력) 화면 대응.

    health_input_snapshots(신체 측정치, 유연한 JSON)와는 성격이 달라서 별도 테이블로 분리:
    - 여긴 "몸무게/키" 같은 측정값이 아니라 "운동을 얼마나/어떻게 하는지"에 대한 자기보고 습관값
    - Figma A08 화면 구조가 고정돼 있어서(근력운동 횟수/강도, 유산소 저·중·고강도 분) 굳이
      JSON으로 유연하게 둘 필요 없이 고정 컬럼으로 명확하게 설계함
    - E04 화면("계산에 쓰인 값")에서 신체정보랑 같이 보여지지만, 계산 시점에 두 테이블을
      각각 조회해서 합치면 되므로 같은 테이블일 필요는 없음
    - health_input_snapshots와 마찬가지로 append-only (팀 설계 철학 일관성 유지)
    """

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="exercise_habit_snapshots")

    strength_weekly_count = fields.SmallIntField()  # 0(안 함)~5(주5회 이상)
    strength_intensity = fields.CharEnumField(enum_type=StrengthIntensity, null=True)  # count=0이면 null 허용

    aerobic_low_minutes = fields.SmallIntField(default=0)  # 저강도 유산소, 분/주
    aerobic_moderate_minutes = fields.SmallIntField(default=0)  # 중강도 유산소, 분/주
    aerobic_high_minutes = fields.SmallIntField(default=0)  # 고강도 유산소, 분/주

    recorded_at = fields.DatetimeField(auto_now_add=True)  # append-only, UPDATE 금지

    class Meta:
        table = "exercise_habit_snapshots"


class MissionExecType(StrEnum):
    """challenges.exec_type과 동일한 값 집합을 공유해야 함 (미션 원본 → 카드 → 챌린지로 이어짐).

    실제 안드로이드 MissionSensorService.kt 기준으로 확정 (2026-08-26):
    제자리 계단운동(STAIR_IN_PLACE)은 필요 없다고 확인되어 제외.
    """

    TIMER = "TIMER"
    CHECK = "CHECK"
    SENSOR_STEPS = "SENSOR_STEPS"                    # 실외 걷기 (StepCounterManager)
    SENSOR_FLOORS_CLIMBED = "SENSOR_FLOORS_CLIMBED"  # 실제 계단 (StairClimbManager)
    SENSOR_STEPS_IN_PLACE = "SENSOR_STEPS_IN_PLACE"  # 제자리걸음 (StepCounterManager 재사용)
    SENSOR_RUNNING_DISTANCE = "SENSOR_RUNNING_DISTANCE"  # GPS 거리 목표 (RunningManager)
    SENSOR_RUNNING_DURATION = "SENSOR_RUNNING_DURATION"  # 케이던스 시간 목표 (RunningCadenceManager)
    SENSOR_WALKING_DURATION = "SENSOR_WALKING_DURATION"  # 걷기 시간 목표 (WalkingCadenceManager)


class MissionTemplateVersion(models.Model):
    """문서: "카드가 참조하는 원본". 버전 관리 테이블이라 과거 버전도 남아있어야 함
    (card_options가 특정 버전을 참조, 기능명세서 9.1 "과거 카드와 분석 결과는 당시 사용한
    미션·규칙·모델·문구 버전으로 재현할 수 있다"에 근거).

    v2 (2026-08-26): "TMTN 오늘의운세 200카드" CSV 전체 필드를 담도록 확장.
    target_value_min/max/step: CSV의 "행운의숫자" 범위. 예측모델/LLM이 완성되기 전까지는
    카드 생성 시 target_value_min을 임시 목표값으로 사용 (TODO: 모델 완성되면 교체).
    """

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    template_key = fields.CharField(max_length=50)  # CSV의 "미션ID" 그대로 사용
    version = fields.IntField(default=1)
    five_element = fields.CharField(max_length=10)  # WOOD/FIRE/EARTH/METAL/WATER (CSV "축" 매핑)
    domain = fields.CharField(max_length=30, null=True)  # CSV "영역": 유산소/근력운동/식사수면생활리듬/기록/수분회복
    title = fields.CharField(max_length=50)  # CSV "행운의행동"
    guide_text = fields.TextField()  # CSV "수행안내_안전문구" (기존 필드명 유지, 실제 내용은 안전문구)
    exec_type = fields.CharEnumField(enum_type=MissionExecType)  # CSV "유형" 매핑

    # --- 목표값: 범위로 저장, 실제 사용은 당분간 min만 (모델 완성 전까지 임시) ---
    target_value = fields.SmallIntField()  # 임시 목표값 = target_value_min과 동일하게 채움
    target_value_min = fields.SmallIntField(null=True)  # CSV "행운의숫자_최소"
    target_value_max = fields.SmallIntField(null=True)  # CSV "행운의숫자_최대"
    target_value_step = fields.SmallIntField(null=True)  # CSV "행운의숫자_간격"
    unit = fields.CharField(max_length=10)  # CSV "단위" (분/회/칸/m 등, 자유 문자열)

    difficulty = fields.SmallIntField(default=1)  # CSV에 없음, 기본값만 유지
    senior_safe = fields.BooleanField(default=True)  # CSV "시니어안전"

    # --- v2 신규 필드 (CSV 전용) ---
    line_text_template = fields.CharField(
        max_length=200, null=True
    )  # CSV "오늘의한줄_템플릿", {place}/{num}/{unit} 플레이스홀더 포함
    fortune_text = fields.CharField(max_length=200, null=True)  # CSV "오늘의운세_해석"
    location_candidates = fields.JSONField(null=True)  # CSV "행운의위치_후보" 콤마 분리 리스트
    time_of_day = fields.JSONField(null=True)  # CSV "시간대" 콤마 분리 리스트, 예: ["아침","오후"]
    safety_tag = fields.CharField(max_length=30, null=True)  # CSV "안전태그" (HIGH_IMPACT, BALANCE 등)
    review_status = fields.CharField(max_length=20, default="검수대기")  # CSV "검수상태"

    is_active = fields.BooleanField(default=False)  # ⚠️ 승인 전까지 기본 비활성 (안전한 기본값)
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "mission_template_versions"
        unique_together = (("template_key", "version"),)
