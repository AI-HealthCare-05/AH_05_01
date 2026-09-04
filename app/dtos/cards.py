from datetime import date
from uuid import UUID

from app.dtos.base import BaseSerializerModel


class CardWindowResponse(BaseSerializerModel):
    """기능명세서 §3 확인: "card_window view model은 draw_state·option_back_ids·
    selected_option_id·challenge_state를 포함한다."

    선택 전에는 option_back_ids만 내려가고(카드 내용은 숨김), 선택 후에만 challenge에
    실제 미션 내용(mission_snapshot)이 채워짐. — 홈에서 "미선택 카드 앞면 노출 금지" 원칙과 동일.
    """

    draw_state: str  # "AWAITING_SELECTION" / "SELECTED"
    service_date: date
    set_id: UUID
    option_back_ids: list[UUID]  # 항상 3개, 내용은 노출 안 됨
    selected_option_id: UUID | None = None
    challenge_id: UUID | None = None
    challenge_state: str | None = None
    is_rest_day: bool = False  # ⚠️ 2026-09-01 추가: daily_record_notes에만 있고 여기 없어서,
    # 앱 재시작하면 쉬어가기 표시가 사라지던 버그를 고치려고 넣음.


class CardRevealResponse(BaseSerializerModel):
    """카드 확정 성공 시 반환하는 "카드 공개 화면" 데이터.

    ⚠️ 2026-08-31 Figma 새 디자인(B06 "오늘의 틈 노트") 반영 — 오늘의 운세·행운의 위치·
    오늘의 한 줄을 추가로 내려줌. 세 필드 다 CSV 임포트 때부터 mission_template_versions에
    이미 있던 값(fortune_text/location_candidates/line_text_template)을 이제서야 노출하는 것.
    """

    challenge_id: UUID
    exec_type: str
    title: str
    guide_text: str
    five_element: str
    domain: str | None = None
    target_value: int
    unit: str
    state: str
    # ⚠️ 2026-09-04 반영: TIMER형을 "이어하기"로 재진입할 때 타이머가 멈춰있는 것처럼
    # 보이던 버그. 클라이언트가 elapsed_seconds를 매번 0부터 로컬로만 세고 있어서,
    # 화면을 벗어났다 돌아오면(뒤로가기→홈→다시 이어하기 등) 그 사이 실제로 흐른 시간이
    # 반영이 안 됐음. 서버가 accumulated_duration_seconds + (지금 - started_at)을 계산해서
    # 내려주면, 클라이언트는 이 값부터 이어서 세면 됨. ACTIVE가 아니면(READY 등) 0.
    elapsed_seconds: int = 0
    fortune_text: str | None = None  # "오늘의 운세" (CSV fortune_text 그대로)
    lucky_location: str | None = None  # "행운의 위치" (location_candidates 중 하나)
    line_text: str | None = None  # "오늘의 한 줄" (line_text_template의 {place}/{num}/{unit} 채운 결과)
