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
