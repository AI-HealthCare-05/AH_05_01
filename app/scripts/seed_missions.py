"""개발/테스트용 미션 템플릿 시드 데이터.

카드 생성(get_or_create_today)이 동작하려면 mission_template_versions에
활성(is_active=True) 데이터가 최소 3개 있어야 함. 실행:

    uv run python -m app.scripts.seed_missions

아직 실제 미션 콘텐츠(문구, 난이도 밸런싱)는 기획 확정 전이라 임시 데이터입니다.
"""

import asyncio

from tortoise import Tortoise

from app.core.db.databases import TORTOISE_ORM
from app.models.health import MissionExecType, MissionTemplateVersion

SEED_MISSIONS = [
    dict(
        template_key="check_water",
        five_element="WATER",
        title="물 한 잔 마시기",
        guide_text="상온의 물을 한 잔(약 200ml) 마셔보세요.",
        exec_type=MissionExecType.CHECK,
        target_value=1,
        unit="count",
        difficulty=1,
        senior_safe=True,
    ),
    dict(
        template_key="timer_stretch",
        five_element="WOOD",
        title="5분 스트레칭",
        guide_text="가볍게 목과 어깨를 5분간 스트레칭해보세요.",
        exec_type=MissionExecType.TIMER,
        target_value=300,
        unit="sec",
        difficulty=1,
        senior_safe=True,
    ),
    dict(
        template_key="sensor_steps_1000",
        five_element="EARTH",
        title="1000걸음 걷기",
        guide_text="가까운 거리를 1000걸음 걸어보세요.",
        exec_type=MissionExecType.SENSOR_STEPS,
        target_value=1000,
        unit="steps",
        difficulty=2,
        senior_safe=True,
    ),
    dict(
        template_key="sensor_stair_5",
        five_element="METAL",
        title="계단 5층 오르기",
        guide_text="엘리베이터 대신 계단으로 5층을 올라가보세요.",
        exec_type=MissionExecType.SENSOR_FLOORS_CLIMBED,
        target_value=5,
        unit="floors",
        difficulty=3,
        senior_safe=False,
    ),
    dict(
        template_key="timer_breathing",
        five_element="FIRE",
        title="3분 심호흡",
        guide_text="편안한 자세로 3분간 천천히 심호흡해보세요.",
        exec_type=MissionExecType.CHECK,
        target_value=1,
        unit="count",
        difficulty=1,
        senior_safe=True,
    ),
]


async def seed() -> None:
    await Tortoise.init(config=TORTOISE_ORM)
    for data in SEED_MISSIONS:
        exists = await MissionTemplateVersion.filter(template_key=data["template_key"], version=1).exists()
        if exists:
            print(f"이미 존재: {data['template_key']}")
            continue
        await MissionTemplateVersion.create(version=1, is_active=True, **data)
        print(f"생성됨: {data['template_key']}")
    await Tortoise.close_connections()


if __name__ == "__main__":
    asyncio.run(seed())
