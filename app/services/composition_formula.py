"""틈튼지수 합산 산식.

⚠️ 2026-09-15 이식 - 강호님(모델) 전달본(composition.py) 원본 그대로 복사함.
compose()/InitialHabitSnapshot 로직은 한 글자도 안 고침 - 검증된 원본을 그대로 쓰고
호출부(practice_score_service.py)만 새로 작성함.
버전: tuntun-composition-2080-signup-fixed-v2-20260915"""

import math
from dataclasses import asdict, dataclass
from datetime import date
from decimal import ROUND_HALF_UP, Decimal

VERSION = "tuntun-composition-2080-signup-fixed-v2-20260915"
PRACTICE_VERSION = "practice-preserved-cumulative-v1-20260915"
HEALTH_KEYS = ("physical", "diabetes", "hypertension")


def number(value):
    if value is not None and (type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 100):
        raise ValueError("INVALID_SCORE")


def text(value):
    if not isinstance(value, str) or not value.strip():
        raise ValueError("IDENTIFIER_REQUIRED")


@dataclass(frozen=True)
class InitialHabitSnapshot:
    snapshot_id: str
    input_revision: str
    formula_version: str
    policy_status: str
    score: float | None

    def __post_init__(self):
        for value in (self.snapshot_id, self.input_revision, self.formula_version):
            text(value)
        number(self.score)
        if self.policy_status not in ("approved", "pending"):
            raise ValueError("INVALID_INITIAL_POLICY_STATUS")
        if self.policy_status == "pending" and self.score is not None:
            raise ValueError("DRAFT_INITIAL_SCORE_MUST_NOT_BE_USED")


def compose(  # noqa: C901 - Preserve the approved composition decision table as one function.
    *,
    health_components,
    initial,
    practice_score,
    practice_version,  # noqa: C901 - 원본 산식 그대로, 복잡도 이유로 리팩토링 금지
    ledger_state,
    ledger_revision,
    health_input_revision,
    as_of,
):
    """Only pass complete, authorized, mutually consistent server snapshots.

    ledger_state: loaded / confirmed_empty_new / unavailable.
    confirmed_empty_new requires a successful complete query, a genuinely new
    user and no records. It is NOT inferred from an empty filtered event list.
    DB immutability, user ownership, revision concurrency and cache invalidation
    remain the caller's responsibility. No initial questionnaire formula here.
    """
    if not isinstance(initial, InitialHabitSnapshot):
        raise ValueError("INITIAL_SNAPSHOT_REQUIRED")
    for value in (practice_version, health_input_revision, as_of):
        text(value)
    if date.fromisoformat(as_of).isoformat() != as_of:
        raise ValueError("INVALID_DATE")
    if practice_version != PRACTICE_VERSION:
        raise ValueError("PRACTICE_VERSION_MISMATCH")
    if ledger_state not in ("loaded", "confirmed_empty_new", "unavailable"):
        raise ValueError("INVALID_LEDGER_STATE")
    number(practice_score)
    if ledger_state != "unavailable":
        text(ledger_revision)
    elif ledger_revision is not None:
        raise ValueError("UNAVAILABLE_LEDGER_HAS_REVISION")
    if ledger_state == "loaded" and practice_score is None:
        raise ValueError("LOADED_PRACTICE_SCORE_REQUIRED")
    if ledger_state != "loaded" and practice_score is not None:
        raise ValueError("UNEXPECTED_PRACTICE_SCORE")
    practice = 0.0 if ledger_state == "confirmed_empty_new" else practice_score
    if not isinstance(health_components, list):
        raise ValueError("HEALTH_COMPONENTS_REQUIRED")
    selected = {}
    for c in health_components:
        key = c.get("componentKey")
        if key not in HEALTH_KEYS:
            continue  # Legacy lifestyle is deliberately excluded.
        if key in selected:
            raise ValueError("DUPLICATE_HEALTH_COMPONENT")
        if c.get("direction") != "higher_is_healthier" or c.get("valueType") != "health_direction_peer_percentile":
            raise ValueError("HEALTH_MEANING_MISMATCH")
        v = c.get("peerPercentile")
        number(v)
        if type(c.get("available")) is not bool or c["available"] != (v is not None):
            raise ValueError("HEALTH_AVAILABILITY_MISMATCH")
        for name in ("modelVersion", "referenceVersion"):
            text(c.get(name))
        selected[key] = c
    if set(selected) != set(HEALTH_KEYS):
        raise ValueError("THREE_HEALTH_COMPONENTS_REQUIRED")
    if any(len({c[name] for c in selected.values()}) != 1 for name in ("modelVersion", "referenceVersion")):
        raise ValueError("MIXED_HEALTH_VERSIONS")
    blocked = []
    if initial.policy_status != "approved":
        blocked.append("INITIAL_FORMULA_POLICY_PENDING")
    elif initial.score is None:
        blocked.append("INITIAL_SCORE_UNAVAILABLE")
    if practice is None:
        blocked.append("PRACTICE_LEDGER_UNAVAILABLE")
    for key in HEALTH_KEYS:
        if selected[key]["peerPercentile"] is None:
            blocked.append("HEALTH_COMPONENT_UNAVAILABLE:" + key)
    lifestyle = None if initial.score is None or practice is None else 0.2 * initial.score + 0.8 * practice
    total = (
        None
        if blocked
        else sum(w * selected[k]["peerPercentile"] for k, w in zip(HEALTH_KEYS, (0.1, 0.2, 0.2), strict=True))
        + 0.5 * lifestyle
    )
    return {
        "schema_version": VERSION,
        "calculation_status": "incomplete" if blocked else "calculated",
        "as_of": as_of,
        "ledger_state": ledger_state,
        "ledger_revision": ledger_revision,
        "health_input_revision": health_input_revision,
        "initial_habit_snapshot": asdict(initial),
        "practice_formula_version": practice_version,
        "health_components": [
            {k: c[k] for k in ("componentKey", "peerPercentile", "modelVersion", "referenceVersion")}
            for c in (selected[key] for key in HEALTH_KEYS)
        ],
        "practice_score": practice,
        "lifestyle_score": lifestyle,
        "composite_score": total,
        "display_score": None
        if total is None
        else float(Decimal(str(total)).quantize(Decimal(".1"), rounding=ROUND_HALF_UP)),
        "composite_blocked_reason": blocked,
        "value_type": "mixed_health_percentiles_and_product_activity_points",
        "unit": "점",
    }
