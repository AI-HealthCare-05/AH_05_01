#!/usr/bin/env python3
"""
TMTN 카드 CSV -> app/src/main/assets/missions.json

문구는 앱 코드에서 고치지 않는다. 팀이 검수한 CSV 를 고치고 이 스크립트를 다시 돌린다.

    python android/tools/build_missions_json.py TMTN_오늘의운세_200카드.csv

출력: android/app/src/main/assets/missions.json
"""
import csv, json, os, sys

AXIS = {"木": ("WOOD", "뻗는 기운"), "火": ("FIRE", "타오르는 기운"),
        "土": ("EARTH", "다지는 기운"), "金": ("METAL", "가다듬는 기운"),
        "水": ("WATER", "흐르는 기운")}

# 축 -> 댐 재료 (Figma v4 기준: 木=나뭇가지, 水=물길)
MAT = {"WOOD": ("BRANCH", "나뭇가지", "움직임·유산소"),
       "FIRE": ("STONE", "받침돌", "버티는 힘·근력"),
       "EARTH": ("SOIL", "다짐흙", "끼니·잠·리듬"),
       "METAL": ("LEAF", "새잎", "가다듬기·기록"),
       "WATER": ("WATERWAY", "물길", "수분·회복")}

KNOWN_TYPES = {"SELF_CHECK", "SELF_TIMER",
               "MODEL_ACTIVE_TIME", "MODEL_DISTANCE", "MODEL_STAIR_COUNT"}


def main(src: str) -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    out_path = os.path.join(here, "..", "app", "src", "main", "assets", "missions.json")

    rows = list(csv.DictReader(open(src, encoding="utf-8-sig")))
    missions, problems = [], []

    for r in rows:
        hanja = r["축"]
        if hanja not in AXIS:
            problems.append(f"{r['미션ID']}: 모르는 축 {hanja}")
            continue
        code, label = AXIS[hanja]
        mcode, mname, mhint = MAT[code]

        t = r["유형"].strip()
        if t not in KNOWN_TYPES:
            problems.append(f"{r['미션ID']}: 모르는 유형 {t}")
        if "{num}" not in r["오늘의한줄_템플릿"]:
            problems.append(f"{r['미션ID']}: 템플릿에 {{num}} 이 없음")

        missions.append({
            "id": r["미션ID"],
            "axis": code, "axisHanja": hanja, "axisLabel": label,
            "area": r["영역"], "action": r["행운의행동"], "type": t,
            "modelBacked": r["모델연동"].strip() == "Y",
            "measureKeys": [x for x in r["측정데이터_핵심"].split(",") if x],
            "startCondition": r["측정시작조건"], "completeRule": r["완료판정"],
            "places": [p.strip() for p in r["행운의위치_후보"].split(",") if p.strip()],
            "numMin": int(r["행운의숫자_최소"]), "numMax": int(r["행운의숫자_최대"]),
            "numStep": int(r["행운의숫자_간격"]), "unit": r["단위"],
            "timeSlots": [p.strip() for p in r["시간대"].split(",") if p.strip()],
            "fortune": r["오늘의운세_해석"], "lineTemplate": r["오늘의한줄_템플릿"],
            "safetyNote": r["수행안내_안전문구"], "safetyTag": r["안전태그"],
            "seniorSafe": r["시니어안전"].strip() == "Y",
            "rewardMaterial": mcode, "rewardName": mname, "rewardHint": mhint,
        })

    if problems:
        print("검증 실패:")
        for p in problems:
            print("  -", p)
        return 1

    doc = {"version": "1.0", "source": os.path.basename(src),
           "count": len(missions), "missions": missions}
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(missions)}장 -> {os.path.normpath(out_path)}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        raise SystemExit(2)
    raise SystemExit(main(sys.argv[1]))
