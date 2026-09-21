from tortoise import timezone
from tortoise.transactions import in_transaction

from app.dtos.companion import stage_definitions
from app.models.companion import CompanionFirstRepair, CompanionStageLog, CompanionState


class CompanionRepository:
    def __init__(self):
        self._model = CompanionState

    async def get_or_create(self, user_id) -> CompanionState:
        state, _ = await self._model.get_or_create(user_id=user_id, defaults={"five_element_completion_counts": {}})
        return state

    async def get_first_repair(self, user_id) -> CompanionFirstRepair | None:
        return await CompanionFirstRepair.get_or_none(user_id=user_id)

    async def welcome_gift_count(self, user_id, *, for_update: bool = False) -> int:
        # 운동 완료 트랜잭션에서는 MySQL 스냅샷 대신 잠금 읽기로 최신 복구 상태를 읽는다.
        repair = (
            await CompanionFirstRepair.select_for_update().get_or_none(user_id=user_id)
            if for_update
            else await self.get_first_repair(user_id)
        )
        return int(repair is not None and repair.completed_at is not None)

    async def advance_first_repair(self, user_id, *, complete: bool) -> str | None:
        """사용자별 행 잠금과 단일 행으로 재시도·동시 요청의 중복 지급을 막는다."""
        async with in_transaction():
            # 운동 완료와 같은 순서로 잠근다. 역순 잠금으로 인한 교착을 피한다.
            await self.get_or_create(user_id)
            state = await self._model.select_for_update().get(user_id=user_id)
            repair = await CompanionFirstRepair.select_for_update().get_or_none(user_id=user_id)
            if repair is None:
                return "UNAVAILABLE"
            if complete and repair.gift_received_at is None:
                return "GIFT_REQUIRED"
            if repair.gift_received_at is None:
                repair.gift_received_at = timezone.now()
                await repair.save(update_fields=["gift_received_at"])
            if complete and repair.completed_at is None:
                repair.completed_at = timezone.now()
                await repair.save(update_fields=["completed_at"])
                total = sum((state.five_element_completion_counts or {}).values()) + 1
                await CompanionStageLog.get_or_create(
                    user_id=user_id, stage_number=1, defaults={"total_materials_at_stage": total}
                )
                current_stage = self._calculate_stage(total, True)
                if current_stage > 1:
                    await CompanionStageLog.get_or_create(
                        user_id=user_id, stage_number=current_stage, defaults={"total_materials_at_stage": total}
                    )
                # 온보딩에서 이미 축하하므로 댐 탭의 축하를 중복 재생하지 않는다.
                state.last_seen_stage_number = max(state.last_seen_stage_number, 1)
                await state.save(update_fields=["last_seen_stage_number"])
            return None

    def _calculate_stage(self, total_materials: int, first_repair_completed: bool = False) -> int:
        current_stage = 0
        for stage in stage_definitions(first_repair_completed):
            if total_materials >= stage["threshold"]:
                current_stage = stage["stage_number"]
            else:
                break
        return current_stage

    async def increment_element(self, user_id, element: str) -> None:
        """문서 §8.1: "중복 이벤트는 포인트를 재적립하지 않는다."
        이 함수는 point_ledger 생성이 성공한 뒤에만 호출해야 중복 증가가 안 생김.

        ⚠️ 2026-09-01 추가: 재료가 늘어난 김에 "이번에 새 단계에 도달했는지"도 같이 확인해서,
        도달했으면 CompanionStageLog에 기록해 둠(G07 화면이 나중에 이걸 읽어서 축하 보여줌).

        ⚠️ 2026-09-03 리뷰 반영: 읽고-고쳐-쓰기(read counts -> +1 -> save)라 두 트랜잭션이
        거의 동시에 같은 값을 읽으면 증가분 하나가 사라질 수 있었음. select_for_update()로
        행 잠금을 걸어서 막음 — 호출부(challenge_service.complete())가 이미 in_transaction()
        안에서 이 함수를 부르고 있어서 바로 적용 가능함.
        """

        await self.get_or_create(user_id)  # 첫 완료라 행이 아직 없으면 먼저 만들어둠(PK가 user_id라 중복 생성은 막힘)
        state = await self._model.select_for_update().get(user_id=user_id)
        counts = state.five_element_completion_counts or {}
        counts[element] = counts.get(element, 0) + 1
        state.five_element_completion_counts = counts
        await state.save(update_fields=["five_element_completion_counts", "updated_at"])

        gift_count = await self.welcome_gift_count(user_id, for_update=True)
        total_materials = sum(counts.values()) + gift_count
        new_stage = self._calculate_stage(total_materials, bool(gift_count))
        if new_stage > 0:
            await CompanionStageLog.get_or_create(
                user_id=user_id,
                stage_number=new_stage,
                defaults={"total_materials_at_stage": total_materials},
            )
