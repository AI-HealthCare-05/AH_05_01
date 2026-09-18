"""틈튼일보 "기록 기사"(records 모드) 서비스.

⚠️ 2026-09-15 신규 - 문홍주 팀장님(SHAP/XAI) 검토 회신 반영. 데모 범위가 "기록 기사"로
좁혀지면서, approved_claims.json의 released=true claim 5개만 이번 범위에 해당한다:
  - claim.records.completed_card
  - claim.records.rest_and_restart
  - claim.lifestyle.aerobic_recorded
  - claim.lifestyle.strength_days_recorded
  - claim.waist.estimate_notice
나머지(당뇨·고혈압 모델 방향 claim)는 전부 released=false(P0-3 안정성 실험 대기)라
이번 범위에서 아예 안 씀 - approved_model_claims에 절대 안 넣는다.

GPT 호출 없음 - claim.text는 approved_claims.json 원문에 플레이스홀더만 치환해서
그대로 씀(편집·의역 금지가 계약이므로 애초에 GPT를 거칠 이유가 없음). "주변 기록 기사"
(claim 문장 주변을 채우는 일반 문안) 생성은 이번 범위 밖 - 필요 여부 확인 후 별도 진행.
"""

from decimal import Decimal

from app.dtos.weekly_editorial import EditorialClaim, WeeklyEditorialResponse
from app.models.prediction import PredictionResult, PredictionResultStatus, SubmodelType
from app.models.users import User
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.services.record_service import RecordService

FALLBACK_TEXT = "이번 주에 남긴 움직임을 함께 살펴봐요."
# ⚠️ approved_claims.json의 fallback_reason_codes 목록은 전부 모델 안정성 관련이라
# "이번 주에 해당하는 기록 claim이 하나도 없다"는 상황을 위한 코드가 없음 - 그 목록에
# 없는 새 코드를 만듦(문서 코드와 안 헷갈리게 구분되는 이름으로).
FALLBACK_REASON_NO_RECORDS = "NO_RECORDS_THIS_WEEK"


class WeeklyEditorialService:
    def __init__(self):
        self.record_service = RecordService()
        self.exercise_repo = ExerciseHabitRepository()

    async def get_weekly_editorial(self, user: User) -> WeeklyEditorialResponse:
        claims: list[EditorialClaim] = []
        linked_record_ids: list[str] = []

        completed_claims, completed_ids = await self._completed_card_claims(user)
        claims += completed_claims
        linked_record_ids += completed_ids

        rest_claim = await self._rest_and_restart_claim(user)
        if rest_claim is not None:
            claims.append(rest_claim)

        lifestyle_claims = await self._lifestyle_claims(user)
        claims += lifestyle_claims

        waist_claim = await self._waist_estimate_claim(user)
        if waist_claim is not None:
            claims.append(waist_claim)

        if not claims:
            return WeeklyEditorialResponse(
                mode="records",
                approved_model_claims=[],
                fallback_reason=FALLBACK_REASON_NO_RECORDS,
                fallback_text=FALLBACK_TEXT,
                linked_record_ids=[],
            )

        return WeeklyEditorialResponse(
            mode="records",
            approved_model_claims=claims,
            fallback_reason=None,
            fallback_text=None,
            linked_record_ids=linked_record_ids,
        )

    async def _completed_card_claims(self, user: User) -> tuple[list[EditorialClaim], list[str]]:
        """claim.records.completed_card - "이번 주 {미션명} 카드가 기록에 남았어요."
        완료된 카드 1건당 claim 1개(evidence_required: "완료 기록 1건 이상")."""

        report = await self.record_service.get_weekly_report(user)
        claims: list[EditorialClaim] = []
        record_ids: list[str] = []
        for day in report.days:
            if day.status != "COMPLETED":
                continue
            # ⚠️ WeeklyReportResponse.days는 상태만 담고 있어서, 미션명이 필요한 이
            # claim을 위해 그날의 상세를 다시 조회함(요청 한 번당 최대 7일이라 부담 적음).
            detail = await self.record_service.get_day_detail(user, day.date)
            if detail.mission_title is None:
                continue
            # ⚠️ 2026-09-17 추가(QA 3번) - 틈튼일보에 완료한 미션명은 있었는데 완료일이
            # 없었음. day.date(해당 기록의 서비스 날짜)는 이미 있던 값이라 문구에만
            # 추가함 - 새로 조회하지 않음.
            claims.append(EditorialClaim(
                id="claim.records.completed_card",
                text=f"{day.date.strftime('%m월 %d일')} {detail.mission_title} 카드가 기록에 남았어요.",
            ))
            record_ids.append(str(day.date))
        return claims, record_ids

    async def _rest_and_restart_claim(self, user: User) -> EditorialClaim | None:
        """claim.records.rest_and_restart - 명시적 쉼(REST) 기록 다음에 재시작(COMPLETED)
        기록이 있을 때만. 무기록(INCOMPLETE)을 쉼으로 해석하지 않음(forbidden_when)."""

        report = await self.record_service.get_weekly_report(user)
        sorted_days = sorted(report.days, key=lambda d: d.date)
        for prev_day, next_day in zip(sorted_days, sorted_days[1:], strict=False):
            if prev_day.status == "REST" and next_day.status == "COMPLETED":
                return EditorialClaim(
                    id="claim.records.rest_and_restart",
                    text="쉬었다가 다시 이어간 날이 기록에 남아 있어요.",
                )
        return None

    async def _lifestyle_claims(self, user: User) -> list[EditorialClaim]:
        """claim.lifestyle.aerobic_recorded / strength_days_recorded.

        ⚠️ 2026-09-18 수정(07_claim_표시정책_결정) - claim 승인 상태·문구·환산 공식
        자체는 그대로 유지한다(released=false로 바꾸거나 삭제하지 않음). 다만 원래
        계약은 "이번 주 실측"(mission_lineage 기반 검증된 집계)인데, 그 집계 정책
        (Q6)이 아직 안 정해져서 mission_lineage가 "blocked" 상태 - 이 상태에서
        "최근 입력한 평소 습관 설문값"을 "이번 주 실적"인 것처럼 보여주면 안 된다는
        결정(정책 §3: "평소 운동습관 설문값으로 대체하지 않는다. 집계가 없으면 숨긴다.
        0분으로 표시하지 않는다")에 따라, 검증된 실제 집계가 없는 지금은 이 claim들을
        만들지 않는다(빈 리스트). 아래 계산식·claim id·문구는 Q6 정책이 확정되고
        mission_lineage 집계가 실제로 연결되면 그 검증된 값을 넣어서 그대로 재사용할
        것 - 이번 수정은 "무엇을 넣을지"를 설문값에서 검증된 집계로 바꾸는 것이지,
        공식이나 승인된 문구 자체를 고치는 게 아니다.
        """

        # ⚠️ 검증된 이번 주 실측 집계(mission_lineage)가 아직 연결 전이라 항상 빈
        # 리스트를 반환한다. 아래는 그 집계가 준비됐을 때 그대로 쓸 계산식 참고용:
        #   aerobic_min = moderate_minutes_this_week + 2 * vigorous_minutes_this_week
        #   claim.lifestyle.aerobic_recorded: f"이번 주 유산소 환산 시간은 {aerobic_min}분이에요."
        #   claim.lifestyle.strength_days_recorded: f"근력 운동은 {strength_days_this_week}일 기록됐어요."
        return []

    async def _waist_estimate_claim(self, user: User) -> EditorialClaim | None:
        """claim.waist.estimate_notice - "추정" 표기 고지. evidence_required:
        predicted_waist_cm 산출 성공, supported=true, pregnancy_status=nonpregnant.
        forbidden_when: 임신 상태가 nonpregnant가 아닐 때, OUT_OF_SUPPORT_RANGE일 때."""

        if user.gender == "FEMALE" and user.is_pregnant is not False:
            return None  # nonpregnant가 확정된 경우만 통과 - None/True는 전부 제외

        latest = await (
            PredictionResult.filter(user_id=user.id, submodel_type=SubmodelType.WAIST_CM_ESTIMATE)
            .order_by("-computed_at")
            .first()
        )
        if latest is None or latest.status != PredictionResultStatus.COMPUTED or latest.value is None:
            return None
        if not isinstance(latest.value, Decimal) or latest.value <= 0:
            return None

        return EditorialClaim(
            id="claim.waist.estimate_notice",
            text="오늘 기준 추정 허리둘레예요. 실제로 재신 값과는 다를 수 있어요.",
        )
