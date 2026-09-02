from app.dtos.companion import (
    MATERIAL_INFO,
    STAGE_DEFINITIONS,
    CardCollectionResponse,
    CardHistoryItem,
    CompanionResponse,
    MaterialHistoryResponse,
    MaterialItem,
    StageItem,
    StageUpPendingResponse,
)
from app.models.challenges import Challenge, ChallengeState
from app.models.companion import CompanionStageLog
from app.models.records import DailyRecordNote
from app.models.users import User
from app.repositories.companion_repository import CompanionRepository


class CompanionService:
    def __init__(self):
        self.repo = CompanionRepository()

    async def get_dam_status(self, user: User) -> CompanionResponse:
        state = await self.repo.get_or_create(user.id)
        counts = state.five_element_completion_counts or {}

        materials = [
            MaterialItem(
                element=element,
                material_name=info["material_name"],
                domain_label=info["domain_label"],
                count=counts.get(element, 0),
            )
            for element, info in MATERIAL_INFO.items()
        ]
        total_materials = sum(m.count for m in materials)

        current_stage, next_threshold = self._calculate_stage(total_materials)
        materials_needed = (next_threshold - total_materials) if next_threshold is not None else 0

        stages = [
            StageItem(
                stage_number=s["stage_number"],
                label=s["label"],
                threshold=s["threshold"],
                completed=total_materials >= s["threshold"],
            )
            for s in STAGE_DEFINITIONS
        ]

        return CompanionResponse(
            current_stage=current_stage,
            total_materials=total_materials,
            next_stage_threshold=next_threshold,
            materials_needed_for_next=materials_needed,
            materials=materials,
            stages=stages,
        )

    def _calculate_stage(self, total_materials: int) -> tuple[int, int | None]:
        """total_materials 기준으로 "지금 몇 단계인지"와 "다음 단계 임계값"을 계산.
        G01 화면의 "3단계 몸통 연결하기 41/70"이 정확히 이 계산 방식 —
        현재 단계는 이미 넘은 임계값 중 가장 높은 것, 진행률 분모는 다음 임계값."""

        current_stage = 0
        for stage in STAGE_DEFINITIONS:
            if total_materials >= stage["threshold"]:
                current_stage = stage["stage_number"]
            else:
                return current_stage, stage["threshold"]
        return current_stage, None  # 전부 넘었으면(120개 이상) 5단계 완료, 다음 단계 없음

    async def _completed_challenges_with_snapshot(self, user: User, five_element: str | None = None):
        """완료된 챌린지를 최신순으로. mission_snapshot에 title·five_element가 그대로 있어서
        따로 join 안 해도 됨(카드 확정 시점 스냅샷)."""

        query = Challenge.filter(
            selection__card_set__user=user, state=ChallengeState.COMPLETED
        ).order_by("-updated_at")
        challenges = await query.prefetch_related("selection__card_set")
        items = []
        for c in challenges:
            snapshot = c.mission_snapshot or {}
            element = snapshot.get("five_element")
            if five_element is not None and element != five_element:
                continue
            info = MATERIAL_INFO.get(element, {"material_name": "?", "domain_label": "?"})
            items.append(
                CardHistoryItem(
                    title=snapshot.get("title", ""),
                    five_element=element or "",
                    material_name=info["material_name"],
                    domain_label=info["domain_label"],
                    completed_at=c.updated_at.isoformat(),
                )
            )
        return items

    async def get_material_history(self, user: User, element: str) -> MaterialHistoryResponse:
        """G05: 재료 하나 눌렀을 때 - 총 개수 + 최근 5개."""

        state = await self.repo.get_or_create(user.id)
        counts = state.five_element_completion_counts or {}
        info = MATERIAL_INFO[element]
        history = await self._completed_challenges_with_snapshot(user, five_element=element)
        return MaterialHistoryResponse(
            element=element,
            material_name=info["material_name"],
            domain_label=info["domain_label"],
            count=counts.get(element, 0),
            recent_history=history[:5],
        )

    async def get_card_collection(self, user: User, element: str | None = None) -> CardCollectionResponse:
        """G06: 카드첩 - 완료된 카드 전체(선택적으로 element 필터)."""

        items = await self._completed_challenges_with_snapshot(user, five_element=element)
        return CardCollectionResponse(total_count=len(items), cards=items)

    async def get_stage_up_pending(self, user: User) -> StageUpPendingResponse | None:
        """G07: 아직 못 본 단계 상승이 있으면 요약해서 반환, 없으면 None(라우터에서 204로 처리)."""

        state = await self.repo.get_or_create(user.id)
        logs = await CompanionStageLog.filter(user=user).order_by("stage_number")
        if not logs:
            return None
        latest = logs[-1]
        if latest.stage_number <= state.last_seen_stage_number:
            return None

        previous_log = None
        for log in logs:
            if log.stage_number == latest.stage_number - 1:
                previous_log = log
                break
        start_at = previous_log.reached_at if previous_log else user.created_at
        end_at = latest.reached_at

        materials_gained = latest.total_materials_at_stage - (
            previous_log.total_materials_at_stage if previous_log else 0
        )
        days_practiced = await Challenge.filter(
            selection__card_set__user=user,
            state=ChallengeState.COMPLETED,
            updated_at__gte=start_at,
            updated_at__lte=end_at,
        ).count()
        days_rested = await DailyRecordNote.filter(
            user=user, is_rest_day=True, updated_at__gte=start_at, updated_at__lte=end_at
        ).count()

        element_counts: dict[str, int] = {}
        completed_in_range = await Challenge.filter(
            selection__card_set__user=user,
            state=ChallengeState.COMPLETED,
            updated_at__gte=start_at,
            updated_at__lte=end_at,
        )
        for c in completed_in_range:
            element = (c.mission_snapshot or {}).get("five_element")
            if element:
                element_counts[element] = element_counts.get(element, 0) + 1
        top_element = max(element_counts, key=element_counts.get) if element_counts else None
        top_material_name = MATERIAL_INFO[top_element]["material_name"] if top_element else None

        stage_label = next(
            (s["label"] for s in STAGE_DEFINITIONS if s["stage_number"] == latest.stage_number), ""
        )

        return StageUpPendingResponse(
            previous_stage=latest.stage_number - 1,
            new_stage=latest.stage_number,
            new_stage_label=stage_label,
            materials_gained_this_stage=materials_gained,
            days_practiced_this_stage=days_practiced,
            days_rested_this_stage=days_rested,
            top_material_name=top_material_name,
        )

    async def mark_stage_seen(self, user: User) -> None:
        """G07 화면을 실제로 봤다고 표시 - 다음부터 같은 단계로는 다시 안 뜸."""

        state = await self.repo.get_or_create(user.id)
        logs = await CompanionStageLog.filter(user=user).order_by("-stage_number").limit(1)
        if logs:
            state.last_seen_stage_number = logs[0].stage_number
            await state.save(update_fields=["last_seen_stage_number"])
