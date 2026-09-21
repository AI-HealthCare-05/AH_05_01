"""⚠️ 테스트 전용 - import_missions_csv.py로 가져온 미션(200개)을 일괄 활성화한다.

CSV 자체의 "검수상태" 컬럼이 200개 전부 "검수대기"임 - 즉 실제로는 아무도 검수 안
했는데, 이 스크립트를 돌리면 그 검수 절차를 그냥 건너뛰고 활성화하는 셈이다.
그래서 이건 "승인 스크립트"가 아니라 "로컬/테스트 환경에서 카드 생성 흐름을 200개
미션으로 테스트해보기 위한" 용도로만 써야 한다. 팀 공용 서버 DB에는 절대 돌리지 말 것 -
실제 승인 절차(검수상태를 실제로 검토해서 개별 활성화하는 것)가 따로 필요하다.

⚠️ 2026-09-14 추가 - 같은 template_key에 여러 버전(v1, v2...)이 있으면 이 스크립트는
"비활성 상태인 것 전부"를 무조건 켜버려서, 예전 버전과 새 버전이 동시에 활성화될 수
있다(카드 뽑기에 같은 미션이 두 번 나오는 위험). 데모·새 환경 세팅처럼 "각 미션마다
최신 버전 하나만 정확히 켜고 싶다"면 activate_all_latest_versions.py를 대신 쓸 것.

실행:
    uv run python -m app.scripts.activate_all_missions_for_testing

기본은 미리보기(dry-run)만 하고 실제로는 안 바꿈. 진짜로 활성화하려면 --confirm을 붙일 것:
    uv run python -m app.scripts.activate_all_missions_for_testing --confirm
"""

import asyncio
import sys

from tortoise import Tortoise

from app.core.db.databases import TORTOISE_ORM
from app.models.health import MissionTemplateVersion


async def activate_all(confirm: bool) -> None:
    await Tortoise.init(config=TORTOISE_ORM)

    inactive = await MissionTemplateVersion.filter(is_active=False)
    print(f"현재 비활성 미션: {len(inactive)}개")

    if not confirm:
        print("\n미리보기만 했습니다. 실제로 활성화하려면 --confirm을 붙여서 다시 실행하세요.")
        print("⚠️ 팀 공용 서버 DB가 아니라 로컬/테스트 DB인지 다시 한번 확인하세요.")
        await Tortoise.close_connections()
        return

    for mission in inactive:
        mission.is_active = True
        await mission.save(update_fields=["is_active"])

    print(f"활성화 완료: {len(inactive)}개")
    await Tortoise.close_connections()


if __name__ == "__main__":
    asyncio.run(activate_all(confirm="--confirm" in sys.argv))
