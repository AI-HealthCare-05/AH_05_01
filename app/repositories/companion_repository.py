from app.dtos.companion import STAGE_DEFINITIONS
from app.models.companion import CompanionStageLog, CompanionState


class CompanionRepository:
    def __init__(self):
        self._model = CompanionState

    async def get_or_create(self, user_id) -> CompanionState:
        state, _ = await self._model.get_or_create(
            user_id=user_id, defaults={"five_element_completion_counts": {}}
        )
        return state

    def _calculate_stage(self, total_materials: int) -> int:
        current_stage = 0
        for stage in STAGE_DEFINITIONS:
            if total_materials >= stage["threshold"]:
                current_stage = stage["stage_number"]
            else:
                break
        return current_stage

    async def increment_element(self, user_id, element: str) -> None:
        """문서 §8.1: "중복 이벤트는 포인트를 재적립하지 않는다."
        이 함수는 point_ledger 생성이 성공한 뒤에만 호출해야 중복 증가가 안 생김.

        ⚠️ 2026-09-01 추가: 재료가 늘어난 김에 "이번에 새 단계에 도달했는지"도 같이 확인해서,
        도달했으면 CompanionStageLog에 기록해 둠(G07 화면이 나중에 이걸 읽어서 축하 보여줌)."""

        state = await self.get_or_create(user_id)
        counts = state.five_element_completion_counts or {}
        counts[element] = counts.get(element, 0) + 1
        state.five_element_completion_counts = counts
        await state.save(update_fields=["five_element_completion_counts", "updated_at"])

        total_materials = sum(counts.values())
        new_stage = self._calculate_stage(total_materials)
        if new_stage > 0:
            await CompanionStageLog.get_or_create(
                user_id=user_id,
                stage_number=new_stage,
                defaults={"total_materials_at_stage": total_materials},
            )
