"""각 미션(template_key)의 "가장 최신 버전"만 정확히 일괄 활성화하는 스크립트.

⚠️ 2026-09-14 신규 - 지현님 팀 질문("데모 전 DB를 초기화하거나 새 환경에 세팅할 때
222개를 하나씩 켜야 하나요?")에 대한 답. activate_mission_version.py는 한 번에 미션
하나씩만 정확히 켜는 용도라 대량 세팅엔 느림. 그렇다고 activate_all_missions_for_testing.py
(테스트 전용)를 쓰면 "검수상태를 아예 확인 안 하고 무조건 다 켠다"는 문제 외에도,
같은 미션의 예전 버전(v1)과 새 버전(v2)이 둘 다 is_active=False였다가 이 스크립트로
동시에 켜져서 카드 뽑기에 같은 미션이 두 번 나오는 위험이 있음.

이 스크립트는 그 문제를 피한다: template_key별로 "가장 높은 version 하나만" 활성화하고,
같은 template_key의 나머지 버전은 비활성 상태로 유지(이미 활성화돼 있었다면 꺼짐).
activate_mission_version.py를 222번 반복 호출하는 것과 결과는 동일하되, 한 번에 처리한다.

실행 (미리보기만):
    uv run python -m app.scripts.activate_all_latest_versions

실제로 활성화하려면 --confirm 추가:
    uv run python -m app.scripts.activate_all_latest_versions --confirm
"""

import asyncio
import sys

from tortoise import Tortoise

from app.core.db.databases import TORTOISE_ORM
from app.models.health import MissionTemplateVersion


async def activate_all_latest(confirm: bool) -> None:
    await Tortoise.init(config=TORTOISE_ORM)

    all_versions = await MissionTemplateVersion.all()
    by_key: dict[str, list[MissionTemplateVersion]] = {}
    for v in all_versions:
        by_key.setdefault(v.template_key, []).append(v)

    to_activate = []  # 최신 버전인데 아직 비활성인 것
    to_deactivate = []  # 최신이 아닌데 활성 상태인 것(잘못 켜져 있던 예전 버전)

    for versions in by_key.values():
        latest = max(versions, key=lambda v: v.version)
        if not latest.is_active:
            to_activate.append(latest)
        for v in versions:
            if v.version != latest.version and v.is_active:
                to_deactivate.append(v)

    print(f"미션 종류: {len(by_key)}개")
    print(f"새로 활성화할 최신 버전: {len(to_activate)}개")
    print(f"비활성화할 예전 버전(잘못 켜져 있던 것): {len(to_deactivate)}개")

    if not confirm:
        print("\n미리보기만 했습니다. 실제로 적용하려면 --confirm을 붙여서 다시 실행하세요.")
        await Tortoise.close_connections()
        return

    for v in to_activate:
        v.is_active = True
        await v.save(update_fields=["is_active"])
    for v in to_deactivate:
        v.is_active = False
        await v.save(update_fields=["is_active"])

    print(f"완료: {len(to_activate)}개 활성화, {len(to_deactivate)}개 비활성화")
    await Tortoise.close_connections()


if __name__ == "__main__":
    asyncio.run(activate_all_latest(confirm="--confirm" in sys.argv))
