import random

from app.models.health import MissionTemplateVersion
from app.models.mission_recommendation import ElementRecommendationScore
from app.models.notifications import ProfileAccessibility

# 사용자에게 예측모델 추천 점수가 하나도 없을 때(콜드 스타트) 쓰는 균등 가중치
DEFAULT_WEIGHT = 1.0
# score가 0 이하로 나와도 뽑힐 확률이 완전히 0이 되지 않도록 하는 최소 가중치
MIN_WEIGHT = 0.01


class MissionTemplateRepository:
    def __init__(self):
        self._model = MissionTemplateVersion

    async def _is_senior_mode(self, user_id) -> bool:
        accessibility = await ProfileAccessibility.get_or_none(user_id=user_id)
        return bool(accessibility and accessibility.senior_mode)

    async def _get_eligible_templates(self, user_id) -> list[MissionTemplateVersion]:
        """활성화된 것 중, 시니어 모드면 senior_safe=True인 것만."""

        query = self._model.filter(is_active=True)
        if await self._is_senior_mode(user_id):
            query = query.filter(senior_safe=True)
        return await query

    async def _get_latest_scores_by_element(self, user_id) -> dict[str, float]:
        """오행별 "가장 최근" 예측모델 점수. computed_at 내림차순으로 가져와서
        오행별로 처음 나오는 것(=가장 최근 것)만 남긴다 (append-only 테이블이라 이력이 여러 개 있음)."""

        rows = (
            await ElementRecommendationScore.filter(user_id=user_id)
            .order_by("-computed_at")
            .values("five_element", "score")
        )
        latest: dict[str, float] = {}
        for row in rows:
            element = row["five_element"]
            if element not in latest:
                latest[element] = float(row["score"])
        return latest

    def _weighted_sample_without_replacement(
        self, items: list[MissionTemplateVersion], weights: list[float], k: int
    ) -> list[MissionTemplateVersion]:
        """Efraimidis-Spirakis 알고리즘. 중복 없이 가중치 기반으로 k개를 뽑는다.
        random.choices는 복원추출(중복 허용)이라 카드 3장이 서로 겹칠 수 있어서 이 방식을 씀."""

        keyed = [
            (random.random() ** (1.0 / max(w, MIN_WEIGHT)), item)
            for item, w in zip(items, weights, strict=True)
        ]
        keyed.sort(key=lambda pair: pair[0], reverse=True)
        return [item for _, item in keyed[:k]]

    async def pick_weighted_three(self, user_id) -> list[MissionTemplateVersion]:
        """카드 3장 후보 선정 로직 v2:
        1) 시니어 모드면 senior_safe=False 미션은 애초에 후보에서 제외
        2) 예측모델이 "부족하다"고 판단한 오행(점수 높은 것)일수록 더 자주 뽑히게 가중치 적용
        3) 예측모델 결과가 아직 없는 사용자(콜드 스타트)는 완전 균등 = 기존 무작위 방식과 동일하게 동작
        """

        eligible = await self._get_eligible_templates(user_id)
        if len(eligible) < 3:
            raise ValueError(
                f"조건에 맞는 활성 미션 템플릿이 3개 미만입니다({len(eligible)}개). 시드 데이터를 확인하세요."
            )

        element_scores = await self._get_latest_scores_by_element(user_id)
        weights = [element_scores.get(t.five_element, DEFAULT_WEIGHT) for t in eligible]

        return self._weighted_sample_without_replacement(eligible, weights, k=3)

    async def get_by_id(self, template_id) -> MissionTemplateVersion | None:
        return await self._model.get_or_none(id=template_id)
