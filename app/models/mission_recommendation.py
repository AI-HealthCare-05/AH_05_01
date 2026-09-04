"""미션 추천 도메인 (신규, 2026-08-26)

기존 34개 ERD엔 없던 테이블. "이 사용자에게 어떤 오행(운동 영역)이 부족한지"를
예측모델(ML)이 계산한 결과를 저장하기 위해 새로 필요해짐.

⚠️ 확인 필요: 이 테이블은 팀 공식 ERD 문서엔 아직 없어요. 팀장님께 공유하고
ERD/기능명세서에도 반영하는 걸 추천드립니다.

기존 companion_states.five_element_completion_counts(완료 이력)와는 성격이 다름:
- completion_counts: "과거에 실제로 뭘 했는지"를 세는 단순 카운터 (즉시 계산 가능, 새 테이블 불필요)
- ElementRecommendationScore: "앞으로 뭘 추천해야 하는지"를 ML 모델이 판단한 결과
  (health_input_snapshots, 완료 이력 등을 모델에 넣어서 나온 출력값, 모델 없인 계산 불가)
"""

import uuid

from tortoise import fields, models


class ElementRecommendationScore(models.Model):
    """오행별 "부족 정도" 점수. 점수가 높을수록 그 오행 미션을 더 추천해야 함.

    append-only로 설계 — 모델이 재계산할 때마다 새 row를 추가하고, 카드 생성 시엔
    사용자의 오행별 "가장 최근" 점수만 조회해서 사용. (health_input_snapshots와 같은 패턴)
    """

    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="element_recommendation_scores")

    # 이 점수를 계산할 때 참고한 신체입력 스냅샷 (재현성 확보용, health_input_snapshots와 동일 목적)
    health_input_snapshot = fields.ForeignKeyField(
        "models.HealthInputSnapshot", related_name="element_recommendation_scores", null=True
    )

    five_element = fields.CharField(max_length=10)  # WOOD/FIRE/EARTH/METAL/WATER 중 하나
    score = fields.DecimalField(max_digits=5, decimal_places=2)  # 점수 스케일은 모델팀과 협의 필요
    model_version = fields.CharField(max_length=30)  # assessment_results와 동일 패턴 — 재현성 위해 버전 기록
    computed_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "element_recommendation_scores"
        # (user, five_element, model_version) 조합 유니크 — 같은 모델 버전으로 같은 오행 중복 계산 방지
        unique_together = (("user", "five_element", "model_version"),)
