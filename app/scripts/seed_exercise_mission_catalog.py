"""틈새 운동 카탈로그 초기 데이터 등록.

⚠️ 2026-09-12 신규 - "오늘 고를 수 있는 틈새 운동" 목록을 채우는 스크립트. 지현님 CSV의
"추천노출풀=운동우선"(01_기존미션_222_v2_제자리걸음센서형.csv) 기준으로 木(유산소)/火(근력)
계열 중 시니어안전=Y인 것만 골랐다. 土(생활리듬)/金(기록)/水(수분)은 "운동우선" 풀 자체가
비어있어 애초에 "틈새 운동"이라는 이름에 안 맞는다고 판단해 제외.

⚠️ 2026-09-18 추가(EC2 운영 DB에 카탈로그가 비어 있던 문제 대응 + 팀 확인 회신 반영) -
"운동우선+시니어안전Y" 조건을 그대로 적용하면 58개인데, 기존 6개 중 5개가 이미 그 안에
포함돼 있어 신규 후보는 53개. 이 중 TIMER(SELF_TIMER) 5종은 앱/서버가 아직 "목표 시간
전 조기 완료 확인"·"경과 시간 표시"·"일시정지"·"완료 시간 저장"을 제대로 처리 못 해서
이번엔 제외(수정·검증 후 별도 추가 예정) - 남은 48개만 추가함. target_value는 CSV
행운의숫자_최소를 그대로 쓰는 기존 원칙(import_missions_csv.py)을 그대로 따름 -
이미 MissionTemplateVersion에 임포트돼 있는 값이라 새로 정할 필요 없음.

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
    # ⚠️ 2026-09-18 추가 - 아래 48개(TIMER 5종 제외). 위 docstring 참고.
    ("WALK_PARK_02", 6),  # 공원 한 바퀴 걷기 - MODEL_ACTIVE_TIME, 10~20분
    ("WALK_AFTER_MEAL_03", 7),  # 식사 후 산책하기 - MODEL_ACTIVE_TIME, 10~15분
    ("WALK_ERRAND_05", 8),  # 걸어서 볼일 보기 - MODEL_ACTIVE_TIME, 10~20분
    ("WALK_PHONE_06", 9),  # 통화하며 걷기 - MODEL_ACTIVE_TIME, 5~10분
    ("WALK_MORNING_07", 10),  # 상쾌하게 산책하기 - MODEL_ACTIVE_TIME, 5~15분
    ("WALK_EVENING_08", 11),  # 느긋하게 산책하기 - MODEL_ACTIVE_TIME, 10~20분
    ("WALK_FRIEND_14", 12),  # 누군가와 걷기 - MODEL_ACTIVE_TIME, 10~20분
    ("WALK_SHOULDER_15", 13),  # 어깨 펴고 걷기 - MODEL_ACTIVE_TIME, 5~15분
    ("WALK_SLOWDOWN_16", 14),  # 느린 걸음 즐기기 - MODEL_ACTIVE_TIME, 10~20분
    ("WALK_ROUTE_17", 15),  # 새 길로 걷기 - MODEL_ACTIVE_TIME, 10~15분
    ("WALK_PAUSE_18", 16),  # 자리에서 잠깐 걷기 - MODEL_ACTIVE_TIME, 2~4분
    ("WALK_SUNLIGHT_26", 17),  # 밝은 곳까지 걷기 - MODEL_ACTIVE_TIME, 5~10분
    ("WALK_BREATH_27", 18),  # 호흡 맞춰 걷기 - MODEL_ACTIVE_TIME, 5~10분
    ("WALK_SHOP_29", 19),  # 가까운 가게 다녀오기 - SELF_CHECK, 1~2번
    ("WALK_RAINY_30", 20),  # 실내에서 걷기 - MODEL_ACTIVE_TIME, 5~15분
    ("WALK_REST_31", 21),  # 중간에 쉬며 걷기 - MODEL_ACTIVE_TIME, 10~20분
    ("WALK_WARMUP_36", 22),  # 몸 풀고 걷기 - MODEL_ACTIVE_TIME, 5~10분
    ("CALF_RAISE_03", 23),  # 까치발 들기 - SELF_CHECK, 10~20회
    ("SEATED_KNEE_07", 24),  # 앉아서 무릎 들기 - SELF_CHECK, 10~20회
    ("SEATED_LEG_08", 25),  # 앉아서 다리 펴기 - SELF_CHECK, 5~15회
    ("TOE_RAISE_10", 26),  # 발끝 들기 - SELF_CHECK, 10~20회
    ("HAND_PRESS_13", 27),  # 손바닥 맞대고 누르기 - SELF_CHECK, 5~10회
    ("TOWEL_PULL_14", 28),  # 수건 양끝 당기기 - SELF_CHECK, 5~10회
    ("WALL_REACH_18", 29),  # 벽에 손 뻗기 - SELF_CHECK, 5~15회
    ("CHAIR_PRESS_19", 30),  # 의자 팔걸이 누르기 - SELF_CHECK, 5~10회
    ("SEATED_HEEL_20", 31),  # 앉아서 발뒤꿈치 들기 - SELF_CHECK, 10~20회
    ("WALL_ANGEL_28", 32),  # 벽에 기대 팔 움직이기 - SELF_CHECK, 5~10회
    ("CHAIR_HALFSTAND_29", 33),  # 의자에서 반쯤 일어서기 - SELF_CHECK, 5~10회
    ("WALL_LEAN_31", 34),  # 벽에 기대 몸 밀기 - SELF_CHECK, 5~10회
    ("SEATED_MARCH_STRONG_32", 35),  # 앉아서 무릎 번갈아 들기 - SELF_CHECK, 10~20회
    ("BOOK_REACH_34", 36),  # 가벼운 물건 들기 - SELF_CHECK, 3~5회
    ("TOWEL_SQUEEZE_35", 37),  # 수건 말아 쥐기 - SELF_CHECK, 5~15회
    ("MINI_SQUAT_38", 38),  # 작게 앉았다 일어서기 - SELF_CHECK, 5~10회
    ("STAIR_COUNT_01", 39),  # 계단 10칸 이상 오르기 - MODEL_STAIR_COUNT, 10~15칸
    ("STAIR_COUNT_02", 40),  # 계단 천천히 오르기 - MODEL_STAIR_COUNT, 10~20칸
    ("STAIR_COUNT_03", 41),  # 난간 잡고 계단 오르기 - MODEL_STAIR_COUNT, 15~20칸
    ("STAIR_COUNT_04", 42),  # 가까운 계단 오르기 - MODEL_STAIR_COUNT, 15~25칸
    ("STAIR_COUNT_05", 43),  # 식사 뒤 계단 오르기 - MODEL_STAIR_COUNT, 10~20칸
    ("STAIR_COUNT_06", 44),  # 계단 20칸 이상 오르기 - MODEL_STAIR_COUNT, 20~25칸
    ("STAIR_COUNT_07", 45),  # 계단 쉬어가며 오르기 - MODEL_STAIR_COUNT, 20~30칸
    ("STAIR_COUNT_08", 46),  # 계단으로 한 번 이동하기 - MODEL_STAIR_COUNT, 15~30칸
    ("STAIR_COUNT_09", 47),  # 계단 오르기 - MODEL_STAIR_COUNT, 10~15칸
    ("STAIR_COUNT_10", 48),  # 계단 오르기로 마무리하기 - MODEL_STAIR_COUNT, 25~30칸
    ("BRISK_WALK_49", 49),  # 빠른 걸음으로 걷기 - MODEL_ACTIVE_TIME, 10~20분
    ("BODYWEIGHT_SQUAT_48", 50),  # 맨몸 스쿼트 하기 - SELF_CHECK, 10~20회
    ("KNEE_PUSHUP_50", 51),  # 무릎 대고 팔굽혀펴기 - SELF_CHECK, 8~16회
    ("WALK_STEPUP_51", 52),  # 오래 걷기 - MODEL_ACTIVE_TIME, 20~30분
    ("STEP_UP_STAIR_52", 53),  # 발판 오르내리기 - SELF_CHECK, 10~20회
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
