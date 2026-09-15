"""특정 미션의 특정 버전만 정확히 활성화(승인)하는 스크립트.

⚠️ 2026-09-13 신규 - 지현님 팀 질문("활성화 방법이 관리자 화면인지 스크립트인지")에
대한 답: 지금까지는 둘 다 없었음(있던 건 activate_all_missions_for_testing.py뿐인데,
이건 "검수 절차를 통째로 건너뛰는 테스트 전용"이라 데모·운영에 쓰면 안 됨). 이 스크립트가
"검수 통과한 특정 버전 하나만" 정확히 켜는 진짜 승인 스크립트 역할을 한다.

활성화하면 같은 template_key의 다른 버전(예: v1)은 자동으로 비활성화된다 - 한
template_key에 동시에 두 버전이 활성 상태면 카드 뽑기 후보 풀에 둘 다 섞여 들어가서
"같은 미션이 두 번 나온다"는 문제가 생기기 때문(승인 A안의 "v1 보존"은 "기록으로
남긴다"는 뜻이지 "동시에 노출한다"는 뜻이 아님).

실행 (미리보기만, 실제로 안 바꿈):
    uv run python -m app.scripts.activate_mission_version MARCH_PLACE_04 2

실제로 활성화하려면 --confirm 추가:
    uv run python -m app.scripts.activate_mission_version MARCH_PLACE_04 2 --confirm
"""

import asyncio
import sys

from tortoise import Tortoise

from app.core.db.databases import TORTOISE_ORM
from app.models.health import MissionTemplateVersion


async def activate_version(template_key: str, version: int, confirm: bool) -> None:
    await Tortoise.init(config=TORTOISE_ORM)

    target = await MissionTemplateVersion.get_or_none(template_key=template_key, version=version)
    if target is None:
        print(f"찾을 수 없음: {template_key} v{version}")
        await Tortoise.close_connections()
        return

    siblings = await MissionTemplateVersion.filter(template_key=template_key).exclude(version=version)
    currently_active_siblings = [s for s in siblings if s.is_active]

    print(f"대상: {template_key} v{version} (현재 is_active={target.is_active})")
    if currently_active_siblings:
        print(f"같이 비활성화될 이전 버전: {[f'v{s.version}' for s in currently_active_siblings]}")

    if not confirm:
        print("\n미리보기만 했습니다. 실제로 활성화하려면 --confirm을 붙여서 다시 실행하세요.")
        await Tortoise.close_connections()
        return

    for sibling in currently_active_siblings:
        sibling.is_active = False
        await sibling.save(update_fields=["is_active"])

    target.is_active = True
    await target.save(update_fields=["is_active"])

    print(f"완료: {template_key} v{version} 활성화, 이전 버전 {len(currently_active_siblings)}개 비활성화")
    await Tortoise.close_connections()


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--confirm"]
    if len(args) != 2:
        print("사용법: uv run python -m app.scripts.activate_mission_version <template_key> <version> [--confirm]")
        sys.exit(1)
    asyncio.run(activate_version(args[0], int(args[1]), confirm="--confirm" in sys.argv))
