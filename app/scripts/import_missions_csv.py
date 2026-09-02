"""TMTN_오늘의운세_200카드 CSV를 mission_template_versions로 가져오는 스크립트.

실행:
    uv run python -m app.scripts.import_missions_csv /path/to/TMTN_오늘의운세_200카드_지현님.csv

원칙:
- 전부 is_active=False로 들어감 (승인 전 안전한 기본값). 승인 후 팀에서 직접 켤 것.
- 이미 같은 template_key(미션ID)가 있으면 건너뜀 (재실행해도 중복 안 생김).
- 목표값은 range(min/max/step) 전부 저장하되, 실제 target_value는 당분간 min으로 채움
  (예측모델/LLM 완성 전까지의 임시 조치, 완성되면 카드 생성 로직에서 교체할 것).
"""

import asyncio
import csv
import sys

from tortoise import Tortoise

from app.core.db.databases import TORTOISE_ORM
from app.models.health import MissionExecType, MissionTemplateVersion

# CSV "유형" -> 우리 exec_type 매핑 (전수 분석으로 확정된 매핑, TMTN_CSV_미션데이터_분석.md 참고)
EXEC_TYPE_MAP = {
    "SELF_CHECK": MissionExecType.CHECK,
    "SELF_TIMER": MissionExecType.TIMER,
    "MODEL_ACTIVE_TIME": MissionExecType.SENSOR_WALKING_DURATION,
    "MODEL_DISTANCE": MissionExecType.SENSOR_RUNNING_DISTANCE,
    "MODEL_STAIR_COUNT": MissionExecType.SENSOR_FLOORS_CLIMBED,
}

# CSV "축"(한자 오행) -> 우리 five_element 값 매핑
FIVE_ELEMENT_MAP = {
    "木": "WOOD",
    "火": "FIRE",
    "土": "EARTH",
    "金": "METAL",
    "水": "WATER",
}


def split_candidates(raw: str | None) -> list[str]:
    """"가까운 산책로,가까운 공원,지금 있는 곳" 같은 콤마 구분 문자열을 리스트로."""

    if not raw or not raw.strip():
        return []
    return [item.strip() for item in raw.split(",") if item.strip()]


async def import_csv(csv_path: str) -> None:
    await Tortoise.init(config=TORTOISE_ORM)

    created = 0
    skipped_existing = 0
    skipped_error = 0
    errors: list[str] = []

    with open(csv_path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)

    print(f"CSV에서 {len(rows)}개 행을 읽었습니다.")

    for row in rows:
        mission_id = row["미션ID"].strip()

        try:
            exists = await MissionTemplateVersion.filter(template_key=mission_id, version=1).exists()
            if exists:
                skipped_existing += 1
                continue

            exec_type = EXEC_TYPE_MAP.get(row["유형"].strip())
            five_element = FIVE_ELEMENT_MAP.get(row["축"].strip())
            if exec_type is None or five_element is None:
                skipped_error += 1
                errors.append(f"{mission_id}: 알 수 없는 유형({row['유형']}) 또는 축({row['축']})")
                continue

            target_min = int(row["행운의숫자_최소"])
            target_max = int(row["행운의숫자_최대"]) if row["행운의숫자_최대"].strip() else None
            target_step = int(row["행운의숫자_간격"]) if row["행운의숫자_간격"].strip() else None

            await MissionTemplateVersion.create(
                template_key=mission_id,
                version=1,
                five_element=five_element,
                domain=row["영역"].strip(),
                title=row["행운의행동"].strip(),
                guide_text=row["수행안내_안전문구"].strip(),
                exec_type=exec_type,
                target_value=target_min,  # TODO: 예측모델/LLM 완성되면 이 값 대신 모델 추천값 사용
                target_value_min=target_min,
                target_value_max=target_max,
                target_value_step=target_step,
                unit=row["단위"].strip(),
                senior_safe=(row["시니어안전"].strip() == "Y"),
                line_text_template=row["오늘의한줄_템플릿"].strip(),
                fortune_text=row["오늘의운세_해석"].strip(),
                location_candidates=split_candidates(row["행운의위치_후보"]),
                time_of_day=split_candidates(row["시간대"]),
                safety_tag=(row["안전태그"].strip() or None) if row.get("안전태그") else None,
                review_status=row["검수상태"].strip(),
                is_active=False,  # 승인 전 항상 비활성
            )
            created += 1

        except Exception as exc:  # noqa: BLE001 - 임포트 스크립트라 개별 행 에러는 계속 진행
            skipped_error += 1
            errors.append(f"{mission_id}: {exc}")

    print(f"생성: {created}개 / 이미 존재: {skipped_existing}개 / 에러: {skipped_error}개")
    if errors:
        print("\n에러 목록:")
        for e in errors[:20]:
            print(" -", e)

    await Tortoise.close_connections()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("사용법: uv run python -m app.scripts.import_missions_csv <csv_경로>")
        sys.exit(1)
    asyncio.run(import_csv(sys.argv[1]))
