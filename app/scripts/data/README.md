# app/scripts/data — 미션 CSV 파일 안내

⚠️ 2026-09-14 추가 — 승인된 CSV가 리포지토리에 커밋된 적이 없어서, 누군가 예전 파일로
재임포트하면 승인된 내용(제자리걸음 센서형 전환 등)이 되돌아갈 위험이 있다는 지적을
받고 추가함.

## 최신 승인본 — 이걸 쓰세요

**`mission_templates_222_v2.csv`**

- 222개 미션, 홍주 팀장님 승인 완료 (2026-09-13)
- `MARCH_PLACE_04`(제자리 걷기)가 `SENSOR_STEPS_IN_PLACE`(300~500걸음, 100걸음 간격),
  안전 안내 문구도 "미끄럽지 않은 곳에서 천천히 발을 옮겨 걸어요. 힘들면 잠시 멈춰
  쉬어도 돼요."로 승인된 상태
- 임포트: `uv run python -m app.scripts.import_missions_csv app/scripts/data/mission_templates_222_v2.csv`

## `mission_templates_200.csv` — 예전 버전, 쓰지 마세요

- 200개, 승인 전 초안 상태(예: `MARCH_PLACE_04`가 아직 `SELF_TIMER`·5~10분·다른 안전문구)
- 기록 보존 목적으로만 남겨둠. **이 파일로 재임포트하면 승인된 내용이 되돌아갑니다.**
