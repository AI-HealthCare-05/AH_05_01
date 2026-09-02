"""챌린지 · 이벤트 · 보상 도메인
ERD 문서 4장에 전체 컬럼이 명시된 유일한 클러스터. 여기에 실제 구현하신
mission_sessions.py(타이머/카운터 진행), mission_records.py(센서 로그)의 로직을
공식 이름(challenges, sensor_measurement_events)으로 이식했습니다.
"""

import uuid
from enum import StrEnum

from tortoise import fields, models


class ChallengeExecType(StrEnum):
    """health.py의 MissionExecType과 반드시 같은 값 집합을 유지해야 함.
    2026-08-26: 실제 MissionSensorService.kt 기준 6종류로 확정 (STAIR_IN_PLACE 제외)."""

    TIMER = "TIMER"
    CHECK = "CHECK"
    SENSOR_STEPS = "SENSOR_STEPS"
    SENSOR_FLOORS_CLIMBED = "SENSOR_FLOORS_CLIMBED"
    SENSOR_STEPS_IN_PLACE = "SENSOR_STEPS_IN_PLACE"
    SENSOR_RUNNING_DISTANCE = "SENSOR_RUNNING_DISTANCE"
    SENSOR_RUNNING_DURATION = "SENSOR_RUNNING_DURATION"
    SENSOR_WALKING_DURATION = "SENSOR_WALKING_DURATION"


class ChallengeState(StrEnum):
    """mission_sessions.py의 MissionSessionStatus(running/paused/completed, 소문자)를
    ERD 문서 4장 표기(대문자)로 통일. READY/SKIPPED는 명세 쪽 흐름(카드 확정 전/건너뛰기)에서 추가."""

    READY = "READY"
    ACTIVE = "ACTIVE"  # 기존 mission_sessions.py의 RUNNING과 동일
    PAUSED = "PAUSED"  # 기존과 동일
    COMPLETED = "COMPLETED"  # 기존과 동일
    SKIPPED = "SKIPPED"


class Challenge(models.Model):
    """문서: "option과 1:1". state 전이는 낙관적 잠금(version)으로 관리(문서 §6).

    타이머/카운터 진행 필드는 mission_sessions.py(MissionSession)에서 그대로 이식했습니다.
    핵심 로직(폰이 꺼졌다 켜져도 진행시간이 유지되는 방식)은 그대로 유지:
      - ACTIVE 상태: accumulated_duration_seconds + (지금 - started_at)
      - PAUSED 상태: accumulated_duration_seconds 그대로 (더 흐르지 않음)
    CHECK 타입은 이 진행 필드들을 안 쓰고 완료 이벤트 하나로 바로 끝남
    (mission_sessions.py 원본 주석: "SELF_CHECK 미션은 이 테이블을 아예 쓰지 않고" 부분을
    challenges 테이블 하나로 통합했으므로, CHECK 타입은 단순히 이 필드들을 null/0으로 둠).
    """

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    selection = fields.OneToOneField("models.DailyCardSelection", related_name="challenge")
    exec_type = fields.CharEnumField(enum_type=ChallengeExecType)
    mission_snapshot = fields.JSONField()  # 확정 시점의 미션 원본 내용을 통째로 복사(스냅샷)
    state = fields.CharEnumField(enum_type=ChallengeState, default=ChallengeState.READY)

    # --- 타이머(TIMER, SENSOR_RUNNING) 전용 필드 — mission_sessions.py에서 이식 ---
    target_duration_seconds = fields.IntField(null=True)
    accumulated_duration_seconds = fields.IntField(default=0)
    started_at = fields.DatetimeField(null=True)  # 현재 구간이 시작된 시각. ACTIVE일 때만 값 있음
    last_paused_at = fields.DatetimeField(null=True)  # 마지막으로 일시정지한 시각 (기록용)

    # --- 카운터(SENSOR_STEPS, SENSOR_FLOORS_CLIMBED) 전용 필드 — mission_sessions.py에서 이식 ---
    target_count = fields.IntField(null=True)  # 목표 걸음수/계단수
    accumulated_count = fields.IntField(default=0)  # 누적 걸음수/계단수
    last_synced_at = fields.DatetimeField(null=True)  # 클라이언트 센서값을 마지막으로 반영한 시각

    version = fields.IntField(default=1)  # 낙관적 잠금(optimistic lock)용
    created_at = fields.DatetimeField(auto_now_add=True)
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "challenges"


class ChallengeEventType(StrEnum):
    START = "START"
    PAUSE = "PAUSE"
    RESUME = "RESUME"
    COMPLETE = "COMPLETE"
    SKIP = "SKIP"


class ChallengeEvent(models.Model):
    """문서: "append-only 원본 이벤트". 기록·리포트·포인트의 유일한 출처(문서 §2).
    idempotency_key UNIQUE로 재전송 시 중복 이벤트 생성을 막음.

    ⚠️ 2026-08-27 Figma 핸드오프(HANDOFF.md §3.3) 반영: "기록 시각은 업로드 시각이 아니라
    사용자가 실제로 완료한 시각을 저장합니다." server_at은 "서버가 이 요청을 받은 시각"이라
    오프라인 후 늦게 동기화되면 실제 완료 시점이랑 어긋남. occurred_at을 클라이언트가
    보내는 "진짜 완료한 시각"으로 별도로 받음(안 보내면 server_at으로 대체 — 온라인 상태
    실시간 완료는 둘이 사실상 같은 시각이라 문제 없음)."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    challenge = fields.ForeignKeyField("models.Challenge", related_name="events")
    event_type = fields.CharEnumField(enum_type=ChallengeEventType)
    server_at = fields.DatetimeField(auto_now_add=True)  # 서버가 요청을 받은 시각 (감사·정렬용)
    occurred_at = fields.DatetimeField(null=True)  # 사용자가 실제로 완료/건너뛴 시각 (클라이언트 제공)
    idempotency_key = fields.CharField(max_length=100, unique=True)  # uq_event_idem
    version = fields.IntField()
    payload = fields.JSONField(null=True)  # skip_reason 등 이벤트별 부가 데이터

    class Meta:
        table = "challenge_events"


class FiveElement(StrEnum):
    """오행. 문서 §2: "값으로만 저장", 다른 체계와 합산·환산하지 않음."""

    WOOD = "WOOD"
    FIRE = "FIRE"
    EARTH = "EARTH"
    METAL = "METAL"
    WATER = "WATER"


class PointLedger(models.Model):
    """문서: "중복 보상 0건이 불변조건". source_event_id UNIQUE가 이걸 강제(문서 §4)."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="point_ledger_entries")
    source_event = fields.OneToOneField(
        "models.ChallengeEvent", related_name="point_ledger_entry"
    )  # uq_point_per_event
    delta = fields.IntField()
    element = fields.CharEnumField(enum_type=FiveElement)
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "point_ledger"


class QualificationState(StrEnum):
    """문서 §4의 sensor_measurement_events.qualification_state 값 추정
    (달리기 속도가 기준을 넘겼는지 판정 상태)."""

    BELOW_THRESHOLD = "BELOW_THRESHOLD"
    QUALIFYING = "QUALIFYING"
    QUALIFIED = "QUALIFIED"


class SensorChallengeConfig(models.Model):
    """OPTIONAL 1:1. SENSOR_RUNNING 전용 설정. 문서 §2 "설계 변경 1건" — 센서 전용 값을
    challenges에서 분리한 이유가 "센서 기능을 미뤄도 Core 테이블(challenges)을 안 건드리게" 하기 위함.
    기능명세서 §4(SENSOR_RUNNING) 확인: target_duration_seconds·valid_duration_seconds·
    speed_threshold_kmh·continuous_qualifying_seconds·sampling_request_interval_ms·mission_version."""

    challenge = fields.OneToOneField(
        "models.Challenge", related_name="sensor_config", pk=True
    )
    speed_threshold_kmh = fields.DecimalField(max_digits=4, decimal_places=1)
    continuous_qualifying_seconds = fields.IntField()
    sampling_request_interval_ms = fields.IntField()  # 명세 표현에 맞춰 필드명 통일(요청 간격)
    valid_duration_seconds = fields.IntField(default=0)  # 유효 속도 구간에서 누적된 실제 시간
    mission_version = fields.CharField(max_length=30)  # 이 센서 기준이 고정된 미션 버전

    class Meta:
        table = "sensor_challenge_configs"


class SensorMeasurementEvent(models.Model):
    """SENSOR_RUNNING/SENSOR_STEPS/SENSOR_FLOORS_CLIMBED 공통 센서 측정 로그.

    mission_records.py(MissionRecord)의 역할을 이 테이블로 통합했습니다.
    원래 mission_records.py는 challenge와 무관하게 daily_card_id(BigInt placeholder)만
    참조했는데, 이제 daily_card_sets~challenges 체계가 갖춰졌으니 challenge FK로 정리.
    device_label(테스트용 기기 식별)은 운영 스키마에는 불필요해 제거 — 로그인 연동 전
    테스트 단계에서만 쓰던 필드라 challenge.user를 통해 사용자를 알 수 있음.

    measurement_type으로 러닝(속도 기반)과 걸음/계단(카운트 기반)을 한 테이블에서 구분:
      - RUNNING: speed_kmh, qualification_state 사용
      - STEP / STAIR: value(걸음수/계단수), qualification_state는 안 씀(null)
    """

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    challenge = fields.ForeignKeyField("models.Challenge", related_name="sensor_measurements")
    measurement_type = fields.CharField(max_length=20)  # STEP / STAIR / STEP_IN_PLACE / RUN_DISTANCE_M / RUN_DURATION / WALK_DURATION
    event_timestamp_ns = fields.BigIntField(null=True)  # RUNNING 전용, 실제 경과시간 계산용
    speed_kmh = fields.DecimalField(max_digits=4, decimal_places=1, null=True)  # RUNNING 전용
    value = fields.IntField(null=True)  # STEP/STAIR 전용 (mission_records.py의 value 그대로)
    qualification_state = fields.CharEnumField(enum_type=QualificationState, null=True)  # RUNNING 전용
    recorded_at = fields.DatetimeField()  # 기기에서 측정된 시각 (mission_records.py에서 이식)
    received_at = fields.DatetimeField(auto_now_add=True)  # 서버가 받은 시각

    class Meta:
        table = "sensor_measurement_events"
