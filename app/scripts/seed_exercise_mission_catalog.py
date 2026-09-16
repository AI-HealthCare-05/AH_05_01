"""틈새 운동 카탈로그 초기 데이터 등록.

⚠️ 2026-09-12 신규 - "오늘 고를 수 있는 틈새 운동" 목록을 채우는 스크립트. 지현님 CSV의
"추천노출풀=운동우선"(01_기존미션_222_v2_제자리걸음센서형.csv) 기준으로 木(유산소)/火(근력)
계열 중 시니어안전=Y인 것만 골랐다. 土(생활리듬)/金(기록)/水(수분)은 "운동우선" 풀 자체가
비어있어 애초에 "틈새 운동"이라는 이름에 안 맞는다고 판단해 제외.

실행:
    uv run python -m app.scripts.seed_exercise_mission_catalog

원칙:
- template_version이 이미 카탈로그에 있으면 건너뜀(재실행해도 중복 안 생김).
- template_version.is_active가 False면 카탈로그 항목은 만들되 경고만 출력함 - 실제 노출은
  ExerciseMissionRepository.get_active_catalog()가 template_version__is_active=True도
  요구하므로, 팀이 그 미션을 활성화하기 전까지는 자동으로 안 보임(import_missions_csv.py의
  "승인 전 안전한 기본값" 원칙과 일관되게 여기서도 임의로 활성화하지 않음).
"""

import asyncio

from tortoise import Tortoise

from app.core.db.databases import TORTOISE_ORM
from app.models.exercise_missions import ExerciseMissionCatalog
from app.models.health import MissionTemplateVersion

# (template_key, display_order) - 木(유산소) 3개 + 火(근력) 3개
CANDIDATES = [
    ("MARCH_PLACE_04", 0),  # 제자리 걷기 - SENSOR_STEPS_IN_PLACE, 300~500걸음(승인 완료분)
    ("WALK_SLOW_01", 1),  # 천천히 걷기 - SENSOR_WALKING_DURATION, 10~20분
    ("SIDE_STEP_19", 2),  # 옆으로 한 걸음씩 움직이기 - CHECK, 10~20걸음
    ("CHAIR_STAND_01", 3),  # 의자에서 앉았다 일어서기 - CHECK, 5~15회
    ("WALL_PUSHUP_02", 4),  # 벽 짚고 밀기 - CHECK, 5~15회
    ("GRIP_OPEN_04", 5),  # 주먹 쥐었다 펴기 - CHECK, 10~20회
]


async def seed() -> None:
    await Tortoise.init(config=TORTOISE_ORM)

    created = 0
    skipped_existing = 0
    missing = []
    inactive_warning = []

    for template_key, display_order in CANDIDATES:
        template = await MissionTemplateVersion.filter(template_key=template_key).order_by("-version").first()
        if template is None:
            missing.append(template_key)
            continue

        _entry, was_created = await ExerciseMissionCatalog.get_or_create(
            template_version_id=template.id,
            defaults={"display_order": display_order, "is_active": True},
        )
        if was_created:
            created += 1
        else:
            skipped_existing += 1

        if not template.is_active:
            inactive_warning.append(template_key)

    print(f"등록: {created}개 / 이미 존재: {skipped_existing}개 / 찾을 수 없음: {len(missing)}개")
    if missing:
        print("\nDB에서 template_key를 못 찾은 항목(임포트 안 됐거나 ID가 다름):")
        for m in missing:
            print(" -", m)
    if inactive_warning:
        print("\n⚠️ 카탈로그엔 등록됐지만 원본 미션(mission_template_versions)이 아직 is_active=False라")
        print("   실제로는 화면에 안 보입니다. 팀 승인 후 해당 미션을 활성화해 주세요:")
        for w in inactive_warning:
            print(" -", w)

    await Tortoise.close_connections()


if __name__ == "__main__":
    asyncio.run(seed())
