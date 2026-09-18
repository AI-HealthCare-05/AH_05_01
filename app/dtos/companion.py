from typing import Literal

from pydantic import BaseModel

# CSV 오행-영역 매핑과 정확히 일치 (지난번 CSV 분석에서 확인된 매핑)
# 木=나뭇가지(유산소), 火=받침돌(근력), 土=다짐흙(생활리듬), 金=새잎(기록), 水=물길(수분회복)
MATERIAL_INFO = {
    "WOOD": {"material_name": "나뭇가지", "domain_label": "움직임·유산소"},
    "FIRE": {"material_name": "받침돌", "domain_label": "근력"},
    "EARTH": {"material_name": "다짐흙", "domain_label": "생활리듬"},
    "METAL": {"material_name": "새잎", "domain_label": "식사·기록"},
    "WATER": {"material_name": "물길", "domain_label": "수분"},
}

# G03 화면 그대로. 임계값은 "총 재료 개수"의 누적 기준값.
STAGE_DEFINITIONS = [
    {"stage_number": 1, "label": "물 터 잡기", "threshold": 5},
    {"stage_number": 2, "label": "기둥 세우기", "threshold": 15},
    {"stage_number": 3, "label": "몸통 연결하기", "threshold": 35},
    {"stage_number": 4, "label": "물길 안정화", "threshold": 70},
    {"stage_number": 5, "label": "튼튼한 댐 완성", "threshold": 120},
]


# ⚠️ 2026-09-18 추가(UI/UX 핸드오프 FR01~08 "첫 복구") - PR #21 소스 기준 이식.
# first_repair_completed=False(기본값)면 STAGE_DEFINITIONS와 완전히 동일한 리스트를
# 반환하므로, 이 함수를 그냥 추가하는 것만으로는 기존 호출부(인자 없이 STAGE_DEFINITIONS를
# 직접 참조하던 곳)에 아무 영향이 없음 - 첫 복구를 마친 계정만 1단계 임계값이 5→1로
# 내려감(2~5단계는 그대로).
def stage_definitions(first_repair_completed: bool = False) -> list[dict]:
    """첫 복구를 마친 계정만 1개로 1단계. 기존 계정과 2~5단계 기준은 유지한다."""
    return [
        {**stage, "threshold": 1, "label": "첫 빈틈 받치기"}
        if first_repair_completed and stage["stage_number"] == 1
        else stage
        for stage in STAGE_DEFINITIONS
    ]


class MaterialItem(BaseModel):
    element: str
    material_name: str
    domain_label: str
    count: int


class StageItem(BaseModel):
    stage_number: int
    label: str
    threshold: int
    completed: bool


class CompanionResponse(BaseModel):
    """G01(댐 홈) + G02(재료 도감) + G03(댐 단계 안내) 화면을 전부 커버."""

    current_stage: int  # 0~5. 0이면 아직 1단계도 안 됨
    total_materials: int
    next_stage_threshold: int | None  # 5단계(최대) 도달했으면 None
    materials_needed_for_next: int  # 다음 단계까지 남은 개수, 최대 단계면 0
    materials: list[MaterialItem]
    stages: list[StageItem]


# ⚠️ 2026-09-18 추가(UI/UX 핸드오프 FR01~08) - "첫 선물은 완료한 운동 카드와 별개이며
# 서버가 중복 지급을 막는다."
FirstRepairStatus = Literal["ELIGIBLE", "GIFT_RECEIVED", "COMPLETED", "UNAVAILABLE"]


class FirstRepairResponse(BaseModel):
    status: FirstRepairStatus
    gift_element: str = "WOOD"
    gift_count: int
    companion: CompanionResponse


class CardHistoryItem(BaseModel):
    """G05(재료별 상세)와 G06(카드첩)이 공유하는 항목 하나 - 완료한 챌린지 1건."""

    title: str
    five_element: str
    material_name: str
    domain_label: str
    completed_at: str


class MaterialHistoryResponse(BaseModel):
    """G05: 재료 하나를 눌렀을 때 - 그 재료의 총 개수 + 최근 5개 이력."""

    element: str
    material_name: str
    domain_label: str
    count: int
    recent_history: list[CardHistoryItem]  # 최근 5개만


class CardCollectionResponse(BaseModel):
    """G06: 완료한 카드 전체(뒷면 색인 없이, 완료된 것만) - 선택한 element로 필터 가능."""

    total_count: int
    cards: list[CardHistoryItem]


class StageUpPendingResponse(BaseModel):
    """G07: 아직 안 본 단계 상승이 있으면 이 내용, 없으면 엔드포인트가 204를 줌."""

    previous_stage: int
    new_stage: int
    new_stage_label: str
    materials_gained_this_stage: int
    days_practiced_this_stage: int
    days_rested_this_stage: int
    top_material_name: str | None
