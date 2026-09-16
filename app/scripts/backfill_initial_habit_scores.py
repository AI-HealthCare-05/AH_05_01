"""기존 pending 초기 습관 점수 사용자 일괄 전환.

⚠️ 2026-09-16 신규 - 강호님(모델) "초기습관_산식과_모델표시_확정_v1" §5 반영.
practice_score_service.py는 "새로 조회되는 사용자"에만 계산을 적용하니, 이미
policy_status='pending'으로 저장된 기존 사용자는 이 스크립트로 별도 전환해야 한다.

판정 기준은 practice_score_service.py._get_or_create_initial_snapshot()과 완전히
동일(SIGNUP_CONFIRMATION_WINDOW_SECONDS, _calculate_initial_habit_from_snapshot 재사용)
- 로직이 두 곳에서 따로 관리되면서 어긋나는 걸 막기 위해 그대로 import해서 씀.

⚠️ "가입 원본을 확인할 수 없으면 null 및 INITIAL_BASELINE_SOURCE_UNCONFIRMED를
유지한다. 현재 입력을 과거 가입값으로 소급하지 않는다"(§5-3) - 그래서 확인 안 되는
사용자는 이 스크립트가 그냥 건너뛴다(pending 그대로, 아무것도 안 바꿈). 몇 번을
다시 실행해도 같은 결과(멱등) - 이미 approved로 전환된 사용자는 필터에서 자동으로
빠지고, 확인 안 되는 사용자는 매번 같은 이유로 건너뛰어짐.

전환 자체는 각 사용자마다 (감사 기록 생성 + 스냅샷 갱신)을 하나의 트랜잭션으로
묶어서, 절반만 반영되는 상태가 안 남게 함(§5-2 "트랜잭션과 유일 제약으로 이중
생성을 막는다").

사용법:
  python -m app.scripts.backfill_initial_habit_scores          # dry-run (미리보기만)
  python -m app.scripts.backfill_initial_habit_scores --confirm  # 실제 반영
"""

import argparse
import asyncio

from tortoise import Tortoise
from tortoise.transactions import in_transaction

from app.core.db.databases import TORTOISE_ORM
from app.models.assessments import InitialHabitConversionAudit, InitialHabitSnapshot
from app.repositories.exercise_habit_repository import ExerciseHabitRepository
from app.repositories.health_repository import HealthInputRepository
from app.services.practice_score_service import (
    SIGNUP_CONFIRMATION_WINDOW_SECONDS,
    _calculate_initial_habit_from_snapshot,
)

REASON = "SIGNUP_SOURCE_CONFIRMED_BACKFILL"


async def run(confirm: bool) -> None:
    health_repo = HealthInputRepository()
    exercise_repo = ExerciseHabitRepository()

    pending_snapshots = await InitialHabitSnapshot.filter(policy_status="pending")
    print(f"pending 스냅샷 {len(pending_snapshots)}건 검토 시작")

    converted = 0
    skipped_no_pair = 0
    skipped_window = 0
    skipped_incomplete = 0

    for snapshot in pending_snapshots:
        earliest_health = await health_repo.get_earliest(snapshot.user_id)
        earliest_habit = await exercise_repo.get_earliest(snapshot.user_id)

        if earliest_health is None or earliest_habit is None:
            skipped_no_pair += 1
            continue

        gap_seconds = abs((earliest_health.created_at - earliest_habit.recorded_at).total_seconds())
        if gap_seconds > SIGNUP_CONFIRMATION_WINDOW_SECONDS:
            skipped_window += 1
            continue

        new_input_revision = f"{earliest_health.id}:{earliest_habit.id}"
        calc = _calculate_initial_habit_from_snapshot(earliest_habit, new_input_revision)

        if calc["score"] is None:
            # ⚠️ 설문 자체가 불완전(missing_inputs 있음) - 가입 시점 식별은 됐지만
            # 계산은 아직 못 함. pending 그대로 둠(강제로 0 채우지 않음, §1 요구사항).
            skipped_incomplete += 1
            continue

        print(
            f"  전환 대상: user={snapshot.user_id} score={calc['score']:.1f} "
            f"gap={gap_seconds:.0f}s revision={new_input_revision}"
        )
        converted += 1

        if not confirm:
            continue

        async with in_transaction():
            await InitialHabitConversionAudit.create(
                user_id=snapshot.user_id,
                previous_policy_status=snapshot.policy_status,
                previous_score=snapshot.score,
                previous_formula_version=snapshot.formula_version,
                new_policy_status=calc["policy_status"],
                new_score=calc["score"],
                new_formula_version=calc["formula_version"],
                new_contributions=calc["contributions"],
                reason=REASON,
            )
            snapshot.input_revision = new_input_revision
            snapshot.input_definition = calc["input_definition"]
            snapshot.formula_version = calc["formula_version"]
            snapshot.policy_status = calc["policy_status"]
            snapshot.score = calc["score"]
            snapshot.contributions = calc["contributions"]
            await snapshot.save(
                update_fields=[
                    "input_revision", "input_definition", "formula_version",
                    "policy_status", "score", "contributions",
                ]
            )

    print(
        f"\n검토 결과 - 전환 대상: {converted}건, 신체/습관 짝 없음: {skipped_no_pair}건, "
        f"가입 시점 확인 안 됨(시간 차이 큼): {skipped_window}건, 설문 미완성: {skipped_incomplete}건"
    )
    if not confirm:
        print("dry-run입니다. 실제로 반영하려면 --confirm을 붙여서 다시 실행해 주세요.")
    else:
        print(f"{converted}건 실제로 전환 완료.")


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--confirm", action="store_true", help="실제로 DB에 반영합니다 (기본은 dry-run)")
    args = parser.parse_args()

    await Tortoise.init(config=TORTOISE_ORM)
    try:
        await run(args.confirm)
    finally:
        await Tortoise.close_connections()


if __name__ == "__main__":
    asyncio.run(main())
