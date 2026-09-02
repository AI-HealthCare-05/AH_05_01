"""동반 캐릭터 · 피드백 도메인 (OPTIONAL — 제거해도 Core 무영향, ERD 문서 §7)
ERD 문서 OPTIONAL: companion_states, card_fit_feedback, recommendation_preferences, mission_replacements

이 파일은 기능명세서(2026-08-24) §8.1, §8.2, §9.1의 "데이터:" 항목을 근거로 필드를 확정했습니다.
(1차 추정본과 달라진 부분이 있어 필드가 전면 교체됨 — 하단 각 클래스 docstring 참고)
"""

import uuid

from tortoise import fields, models


class CompanionState(models.Model):
    """기능명세서 §8.1 확인: "companion_states는 five_element_completion_counts를 포함하며
    레벨·성장 단계 필드는 두지 않는다." → 1차 추정에 있던 current_stage 필드는 삭제.
    ki_total/intimacy_total도 명세에 없어 제거하고, point_ledger 합계로 조회하는 방식으로 대체.

    ⚠️ 2026-09-01 추가: last_seen_stage_number - G07(단계 상승 축하) 화면을 이미 봤는지 표시.
    "단계"는 여전히 total_materials로 그때그때 계산하는 값이라 이 컬럼에는 절대 안 저장하고,
    "마지막으로 축하 화면을 본 단계"만 저장함(중복으로 축하 화면 안 뜨게 하는 용도).
    """

    user = fields.OneToOneField("models.User", related_name="companion_state", pk=True)
    five_element_completion_counts = fields.JSONField(
        default=dict
    )  # 예: {"WOOD": 3, "FIRE": 1, "EARTH": 0, "METAL": 2, "WATER": 5}
    last_seen_stage_number = fields.IntField(default=0)
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "companion_states"


class CompanionStageLog(models.Model):
    """G07(단계 상승 축하) 화면의 "3단계에서 쌓은 것" 요약을 만들려면 "언제 그 단계에
    도달했는지" 기준점이 있어야 해서 새로 추가한 테이블. 단계에 새로 도달할 때마다
    한 줄씩 쌓임(뒤로 내려가는 일 없음)."""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="companion_stage_logs")
    stage_number = fields.IntField()
    total_materials_at_stage = fields.IntField()
    reached_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "companion_stage_logs"
        unique_together = (("user", "stage_number"),)


class CardFitFeedback(models.Model):
    """기능명세서 §8.2.1 확인: "card_fit_feedback은 challenge_id·difficulty·reason_codes·
    submitted_at을 저장한다." (1차 추정의 feeling/reason에서 전면 교체)"""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    challenge = fields.OneToOneField("models.Challenge", related_name="fit_feedback")
    difficulty = fields.CharField(max_length=20)  # 명세: "네 가지 난이도" 중 하나
    reason_codes = fields.JSONField(default=list)  # 예: ["TIME", "LOCATION"] 복수 선택 가능
    submitted_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "card_fit_feedback"


class RecommendationPreference(models.Model):
    """기능명세서 §8.2.2 확인: "recommendation_preferences는 effective_service_date·
    adjustments·source_feedback_ids를 저장한다." (1차 추정의 preferred_element 등에서 전면 교체)"""

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="recommendation_preferences")
    effective_service_date = fields.DateField()  # 이 조정이 적용되는 시작일 (다음 service_date부터)
    adjustments = fields.JSONField()  # 예: {"time_of_day": "evening", "difficulty": "easier"}
    source_feedback_ids = fields.JSONField(default=list)  # 이 조정을 이끌어낸 card_fit_feedback id 목록
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "recommendation_preferences"


class MissionReplacement(models.Model):
    """기능명세서 §3(카드 세트) 근거 확인: "mission_replacements는 challenge_id·from_version_id·
    to_version_id·reason·replaced_at을 저장하고 challenge_id를 고유키로 둔다." """

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    challenge = fields.OneToOneField("models.Challenge", related_name="replacement")  # uq_replacement_once
    from_version = fields.ForeignKeyField(
        "models.MissionTemplateVersion", related_name="replaced_from"
    )
    to_version = fields.ForeignKeyField(
        "models.MissionTemplateVersion", related_name="replaced_to"
    )
    reason = fields.CharField(max_length=100, null=True)
    replaced_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "mission_replacements"
