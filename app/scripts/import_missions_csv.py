"""TMTN_오늘의운세_200카드 CSV를 mission_template_versions로 가져오는 스크립트.

실행:
    uv run python -m app.scripts.import_missions_csv /path/to/csv_경로.csv

원칙:
- 전부 is_active=False로 들어감 (승인 전 안전한 기본값). 승인 후 팀에서 직접 켤 것.
- 목표값은 range(min/max/step) 전부 저장하되, 실제 target_value는 당분간 min으로 채움
  (예측모델/LLM 완성 전까지의 임시 조치, 완성되면 카드 생성 로직에서 교체할 것).

⚠️ 2026-09-13 버그 수정(팀 QA 지적: "임포터가 조회·생성 모두 version=1로 고정돼 있어서
승인된 A안 - 기존 v1 보존 + 수정분만 v2로 신규 등록 - 이 실제로는 동작 안 함").

예전엔 "template_key + version=1이 이미 있으면 무조건 건너뜀"이라, CSV에서 값을 고쳐도
(예: MARCH_PLACE_04를 SELF_TIMER에서 SENSOR_STEPS_IN_PLACE로 바꾼 것) v1 row가 이미
있으면 새 값이 영원히 반영될 방법이 없었음. 이제는:
1. template_key로 가장 최신 버전을 찾음
2. 없으면(신규 미션) version=1로 생성
3. 있는데 CSV 값과 완전히 같으면(unchanged) 아무것도 안 함 - v1 유지
4. 있는데 CSV 값이 다르면(changed) 기존 버전은 그대로 두고 latest.version + 1로 새로 생성
   (v1 보존 + 수정분만 새 버전 등록 - 승인된 A안 그대로)
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
    # ⚠️ 2026-09-12 추가 - MARCH_PLACE_04(제자리 걷기) 승인분 반영(홍주 팀장님 승인,
    # 03_CSV_변경검증.json). CSV "유형" 값이 우리 MissionExecType enum 문자열과 그대로
    # 일치해서 매핑이 단순함.
    "SENSOR_STEPS_IN_PLACE": MissionExecType.SENSOR_STEPS_IN_PLACE,
}

# CSV "축"(한자 오행) -> 우리 five_element 값 매핑
FIVE_ELEMENT_MAP = {
    "木": "WOOD",
    "火": "FIRE",
    "土": "EARTH",
    "金": "METAL",
    "水": "WATER",
}

# ⚠️ 버전 비교 대상 필드. is_active/created_at 등 "메타" 필드는 내용 비교에서 제외 -
# 승인 상태가 바뀐 것만으로 새 버전을 만들면 안 되므로.
_COMPARE_FIELDS = (
    "five_element",
    "domain",
    "title",
    "guide_text",
    "exec_type",
    "target_value",
    "target_value_min",
    "target_value_max",
    "target_value_step",
    "unit",
    "senior_safe",
    "line_text_template",
    "fortune_text",
    "location_candidates",
    "time_of_day",
    "safety_tag",
)


def split_candidates(raw: str | None) -> list[str]:
    """ "가까운 산책로,가까운 공원,지금 있는 곳" 같은 콤마 구분 문자열을 리스트로."""

    if not raw or not raw.strip():
        return []
    return [item.strip() for item in raw.split(",") if item.strip()]


def _fields_equal(existing: MissionTemplateVersion, new_fields: dict) -> bool:
    return all(getattr(existing, field) == new_fields[field] for field in _COMPARE_FIELDS)


async def import_csv(csv_path: str) -> None:
    await Tortoise.init(config=TORTOISE_ORM)

    created = 0
    updated = 0
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
            exec_type = EXEC_TYPE_MAP.get(row["유형"].strip())
            five_element = FIVE_ELEMENT_MAP.get(row["축"].strip())
            if exec_type is None or five_element is None:
                skipped_error += 1
                errors.append(f"{mission_id}: 알 수 없는 유형({row['유형']}) 또는 축({row['축']})")
                continue

            target_min = int(row["행운의숫자_최소"])
            target_max = int(row["행운의숫자_최대"]) if row["행운의숫자_최대"].strip() else None
            target_step = int(row["행운의숫자_간격"]) if row["행운의숫자_간격"].strip() else None

            new_fields = {
                "five_element": five_element,
                "domain": row["영역"].strip(),
                "title": row["행운의행동"].strip(),
                "guide_text": row["수행안내_안전문구"].strip(),
                "exec_type": exec_type,
                "target_value": target_min,  # TODO: 예측모델/LLM 완성되면 이 값 대신 모델 추천값 사용
                "target_value_min": target_min,
                "target_value_max": target_max,
                "target_value_step": target_step,
                "unit": row["단위"].strip(),
                "senior_safe": (row["시니어안전"].strip() == "Y"),
                "line_text_template": row["오늘의한줄_템플릿"].strip(),
                "fortune_text": row["오늘의운세_해석"].strip(),
                "location_candidates": split_candidates(row["행운의위치_후보"]),
                "time_of_day": split_candidates(row["시간대"]),
                "safety_tag": (row["안전태그"].strip() or None) if row.get("안전태그") else None,
            }
            review_status = row["검수상태"].strip()

            latest = await MissionTemplateVersion.filter(template_key=mission_id).order_by("-version").first()

            if latest is None:
                await MissionTemplateVersion.create(
                    template_key=mission_id,
                    version=1,
                    review_status=review_status,
                    is_active=False,
                    **new_fields,
                )
                created += 1
                continue

            if _fields_equal(latest, new_fields):
                skipped_existing += 1
                continue

            # ⚠️ v1(latest) 보존 - update() 안 하고 새 row를 만듦. 승인된 A안 그대로.
            await MissionTemplateVersion.create(
                template_key=mission_id,
                version=latest.version + 1,
                review_status=review_status,
                is_active=False,
                **new_fields,
            )
            updated += 1

        except Exception as exc:  # noqa: BLE001 - 임포트 스크립트라 개별 행 에러는 계속 진행
            skipped_error += 1
            errors.append(f"{mission_id}: {exc}")

    print(
        f"신규 생성: {created}개 / 새 버전 등록(내용 변경): {updated}개 / "
        f"변경 없음: {skipped_existing}개 / 에러: {skipped_error}개"
    )
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
