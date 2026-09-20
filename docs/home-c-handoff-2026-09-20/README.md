# 시안 C · 실제 댐 단계 연동 · 홈 + XAI · EC2 전달본

대상은 **팀 내부 검토·시연 서버**입니다. `tmtn-home-c-ec2-2026-09-20-live-dam` 폴더의 ZIP 하나만 사용하세요. 이전 v3~v7, 시안 C, reviewed, cheer, card-fix ZIP을 뒤에 덮어쓰지 마세요.

이번 수정은 주간일보에 기존 GET /api/v1/companion의 현재 댐 단계(0~5)와 누적 재료 수를 연결합니다. 서버·DB 변경은 없습니다. “지금의 내 댐”으로 표시하고, 조회 중·실패 시 임의의 단계 그림을 띄우지 않습니다. 댐 탭과 공유하는 그림 시트의 3~5단계 행 좌표도 바로잡았습니다. 상세 동작은 `docs/LIVE_DAM.md`에 있습니다.

뒤로가기 후 완료 카드에 남아 있던 옛 장식 프레임을 제거한 수정도 포함합니다. 홈·공개·완료 다시보기가 같은 크림색 이중 테두리와 Pretendard를 사용합니다. 옛 프레임 코드·이미지·전용 폰트 4개도 해시 확인 후 백업·삭제합니다. 새 백엔드 변경은 없습니다. 상세 원인과 확인 경로는 `docs/CARD_DESIGN_AUDIT.md`에 있습니다.

시안 C의 선택 전·실천 중 홈 구성을 유지하면서, 사용자 후속 요청에 따라 완료 후에는 틈튼이 응원으로 전환합니다. 서버에서 확인된 틈새운동 0·1·2회에 따라 응원이 달라지며, 일보의 댐·비버 배치와 운동 복귀 배너도 수정했습니다. 이전 사용자 흐름 오류 수정은 모두 포함됩니다. `docs/CHEER_UPDATE.md`와 `docs/UX_AUDIT.md`에서 확인 범위를 볼 수 있습니다.

- 기준: PR #21, `65500f33321762db85624c51390ed4a3b49b4994` (기준 전달본에서 확인한 커밋).
- 디자인: [시안 C · 미션 중심 홈](https://www.figma.com/design/ByGT2uoinUBxAQOBqAK7sM/TMTN-Final-App-UI?node-id=1655-20).
- 파일: `files/`에 기존 저장소와 동일한 경로의 소스·모델·설정. `apply_handoff.py`가 경로를 맞춰 자동 적용합니다.
- APK: `tmtn-home-c.apk`는 실제 서버 연결용 `com.tmtn.app`입니다. `tmtn-home-c-review.apk`는 별도 앱 `com.tmtn.app.challengeqa`입니다. 검토 APK도 제품 실행 화면에서는 기존 서버와 연결되며, 별도 검토 아이콘에서 예시 데이터 화면을 엽니다.
- 기존 서버 주소 `http://54.144.123.148/api/v1/`를 유지합니다. 새 주소 입력이나 Android 소스 수정이 필요 없습니다.
- DB·회원·비밀키·.env·Android 서명키·local.properties는 전달/덮어쓰기 대상이 아닙니다.

## 1. 기존 프로젝트에 적용 — 두 담당자 공통

ZIP을 **기존 프로젝트 밖의 별도 폴더**에 풉니다. 아래 `/전달본`, `/기존/AH_05_01`만 실제 경로로 바꾸세요. 저장소 루트에는 `android`, `app`, `pyproject.toml`이 함께 있습니다.

Linux:
```bash
python3 /전달본/apply_handoff.py --project /기존/AH_05_01
python3 /전달본/apply_handoff.py --project /기존/AH_05_01 --apply
```

Windows:
```powershell
py -3 "C:/전달본/apply_handoff.py" --project "C:/기존/AH_05_01"
py -3 "C:/전달본/apply_handoff.py" --project "C:/기존/AH_05_01" --apply
```

첫 명령은 검사만 합니다. PR #21 원본과 앞서 전달한 v1~v7, 시안 C, reviewed, cheer 및 card-fix 파일은 자동 인식합니다. 적용기는 원본을 `.handoff-backups/날짜/`에 백업하고, 알려진 폐기 걷기·카드 자산과 코드를 정리합니다. 다시 실행해도 같은 파일을 재수정하지 않습니다.

**충돌 0개일 때만 적용됩니다.** 담당자가 별도로 바꾼 파일은 강제로 덮지 않고 전체 적용을 중단합니다. 충돌 파일 목록으로 차이를 먼저 확인하세요. 이 보호를 건너뛰려고 과거 ZIP을 섞거나 임의로 해시를 고치지 마세요.

탐색기로 덮어쓸 경우에는 `files/` **안의 내용**을 프로젝트 루트에 복사합니다. 단순 복사는 폐기 파일을 삭제하지 않으므로 위 자동 적용을 권장합니다. Windows Android 빌드 도구에도 폐기 걷기·카드 파일의 해시 확인·백업·정리가 포함되어 있습니다.

## 2. Android 담당자 — 바로 APK 빌드

Android Studio에서 기존 프로젝트의 `android/`를 열어 Sync 후 Build APK를 실행합니다. 기존 SDK·JDK 설정과 서명키를 그대로 사용하세요. 고정된 Gradle/AGP/Kotlin 버전을 올릴 필요가 없습니다.

Windows(프로젝트 루트):
```powershell
powershell -ExecutionPolicy Bypass -File tools/build-handoff-apk.ps1
```

Linux/macOS:
```bash
cd android
bash gradlew :app:assembleDebug --no-configuration-cache --console=plain
```

출력: `android/app/build/outputs/apk/debug/app-debug.apk`.

- Android SDK 35, JDK 17 toolchain. Wrapper 실행용 JDK는 기존 Android Studio 설정을 유지합니다.
- 일반 앱을 빌드할 때 `-I challenge-preview.init.gradle`를 붙이지 마세요. 이 옵션은 별도 검토 앱용입니다.
- 일반 APK에는 제품 실행 아이콘만 표시합니다. 예시 데이터 화면은 별도 검토 APK에서만 활성화됩니다.
- 이번 Android 버전은 `1.0.3`, `versionCode=4`입니다. 새로운 서버 API나 DB 변경은 이번 화면 수정에 필요하지 않습니다. 기존 XAI를 처음 적용한다면 아래 서버 절차는 그대로 필요합니다.
- ZIP의 일반 APK는 PR #21 원본에 이번 전달본을 자동 적용한 별도 폴더에서 빌드했습니다.
- 기존 휴대폰 앱과 서명이 다르면 설치 시 업데이트가 거절될 수 있습니다. 데이터를 유지하려면 팀의 기존 서명키로 빌드하세요. 앱을 지울 필요는 없습니다.
- 공개 release 빌드의 모델 승인 플래그는 임의로 변경하지 않았습니다. 이번 전달 목적에는 debug APK를 사용하세요.

## 3. 서버 담당자 — EC2 Linux x86_64

Windows의 `runtime/python.exe`는 사용하지 않습니다. `model_assets/xai/payload`의 원본 모델·배경과 Linux Dockerfile·고정 requirements를 모두 포함했습니다. API는 기존 Python **3.13.15**, XAI는 별도 Python **3.14.7 + SHAP 0.49.1**입니다.

기존 EC2의 MySQL·Redis 및 `.env`가 준비된 상태에서 진행합니다. 아래 명령은 기존 `docker-compose.yml` 기준입니다. 배포용 Compose 파일 이름이 다르면 첫 `-f`에 그 파일을 지정하세요. **DB 백업을 완료한 후** 마이그레이션을 적용합니다.

```bash
cd /기존/AH_05_01

# 자산 해시 검사 + 내부 토큰 자동 생성. 기존 .env와 기존 XAI 토큰은 보존.
python3 tools/prepare-xai-ec2.py

# 두 새 이미지 빌드. 외부 Python 패키지 다운로드를 위한 네트워크가 필요.
docker compose -f docker-compose.yml -f compose.xai-review.yml build fastapi tuntun-xai

# 기존 DB 연결 설정을 사용한 마이그레이션 확인/적용.
docker compose -f docker-compose.yml -f compose.xai-review.yml run --rm --no-deps fastapi uv run --no-sync aerich heads
docker compose -f docker-compose.yml -f compose.xai-review.yml run --rm --no-deps fastapi uv run --no-sync aerich upgrade

# API 네트워크를 공유하는 기존 또래/허리둘레 브릿지도 함께 재생성.
docker compose -f docker-compose.yml -f compose.xai-review.yml up -d --no-deps --force-recreate fastapi tuntun-peer-bridge tuntun-xai nginx
docker compose -f docker-compose.yml -f compose.xai-review.yml ps
docker compose -f docker-compose.yml -f compose.xai-review.yml logs --tail=50 fastapi tuntun-xai
```

적용할 신규 마이그레이션:
- `23_20260919220713_add_xai_strength_unit.py`: 근력운동 빈도 단위. 이전 값의 단위를 임의로 일수로 바꾸지 않음.
- `24_20260920014156_add_weekly_xai_snapshots.py`: 회원별·주별 실제 XAI 결과 저장.
- 기존 PR #21의 16~22번은 유지합니다. 이전 전달 자료의 다른 16번 파일을 가져오지 마세요. 이미 23/24가 적용된 DB는 Aerich가 중복 적용하지 않습니다.

`.env.xai-review`는 생성 시 0600 권한이며 API와 모델에 **같은 토큰**을 전달합니다. 토큰을 Android에 넣거나 공유 문서에 복사하지 마세요. API는 캐시/진행 작업 일관성을 위해 worker **1개**, XAI는 loopback **8776**만 사용합니다. EC2 보안 그룹에 8776을 열지 마세요. 운영 중 토큰 파일을 재생성할 필요가 없습니다.

기존 `.env`의 DB_HOST는 컨테이너 기준으로 `mysql`(또는 기존 DB 주소)을 유지합니다. 기존 또래 비교/허리둘레 브릿지 URL·JWT·OAuth·DB/Redis 설정을 지우지 마세요. 이 전달본은 DB 볼륨을 삭제하거나 초기화하지 않습니다.

### 서버 설치 뒤 모델 단독 확인

```bash
docker compose -f docker-compose.yml -f compose.xai-review.yml exec -T tuntun-xai python -B model_service/verify_personal.py --payload /app/payload --out /tmp/xai-check
```

합성 입력 5건의 실제 추론·기여도 합·지원하지 않는 입력 차단을 확인합니다. 회원 DB를 사용하지 않습니다. 성공 마지막 줄은 `Personal SHAP verification passed`입니다.

## 4. XAI가 앱에서 표시되는 조건과 최종 확인

1. 일반 APK로 기존 EC2에 로그인합니다.
2. 분석 동의를 켜고, 신체 정보와 운동 정보를 저장합니다. 과거 입력에서 근력 빈도의 단위가 없으면 **운동 정보를 다시 저장**해 주간 일수로 확정해야 합니다.
3. 틈튼일보 → 내 활동 비교/계산 이야기를 엽니다. 활동 비교가 먼저 나오고, 개인 SHAP 계산 완료 후 설명이 표시됩니다.
4. 입력값을 바꾸고 다시 저장하면 새 입력 버전의 결과가 나와야 합니다. 동의 철회 후 개인 결과와 주간 점수 이력은 숨겨져야 합니다.
5. 주간면은 월요일~일요일 기준입니다. 이번 주와 지난 주의 실천 기록을 비교하고, **실제로 저장된 XAI 참고점수**를 최근 8주 그래프로 보여줍니다. 같은 주에는 최신 정상 결과를 보관합니다.
6. 이전 주의 실천 기록만 있고 개인 XAI 결과를 저장한 적이 없다면 실천 비교만 가능합니다. 과거 점수를 추정하거나 0점으로 만들어 채우지 않습니다. 모델/기준 버전이 다른 두 주의 점수 차이도 비교하지 않습니다.

API 경로:
- `GET /api/v1/tuntun-score/personal`: pending → ready, 동일 입력의 실제 개인 SHAP.
- `GET /api/v1/tuntun-score/personal/history`: 저장된 최근 8주 참고점수.
- 기존 주간 실천 조회는 그대로 유지.

상태별 조치:
- `not_configured`: Compose override, XAI URL/토큰을 확인하고 API·모델을 함께 재시작.
- `input_required` / `strength_days_unconfirmed`: 앱에서 누락 정보/운동 정보 다시 저장.
- `consent_required`: 해당 회원의 분석 동의 확인.
- `pending` / `busy`: 계산 대기. 앱의 재조회 흐름을 사용.
- `calculation_failed`: XAI 로그·모델 해시·두 Python 환경을 확인.
- `release_review_required`: 공개 운영 차단. 이번 내부 시연용 Compose는 ENV=dev이며, 모델의 공개 승인 상태를 위조하지 않습니다.

사용자가 승인한 **내부 시연용 읽을거리 40건**은 local/dev에서 표시됩니다. 해당 승인은 임상 검증·모델 공개 승인·출처 사용허락을 대신하지 않습니다. 일반 사용자가 사용하는 운영 인스턴스에 이 내부 시연 설정을 적용하지 마세요.

## 5. 시안 C에서 반영한 것

- 카드 뽑기 전 큰 미션 영역 → 선택 후 크림색 이중 테두리 카드 전체. 원본 카드 디자인과 CSV 문구 연동 유지.
- 기본 높이 418dp 기준. 긴 문구·큰 글자는 잘라내지 않고 높이 확장.
- 오늘 카드 완료 후에는 카드 전문 대신 응원 이미지·문구와 작은 카드 다시보기 링크를 표시합니다. 틈새운동 2회 완료 후에는 달성 이미지와 내 댐 이동 버튼을 표시합니다.
- 완료 카드 화면·틈새운동 목록에서 뒤로가기·카드 다시보기도 같은 새 카드 컴포넌트를 사용합니다. 완료 카드는 ‘실천 완료’를 표시하며 시작 버튼을 보여주지 않습니다.
- 일보 표지의 댐과 기존 비버를 같은 물가에 겹쳐 배치합니다. 진행 중 복귀 배너는 진한 초록 바탕·흰 글씨, 같은 위아래 여백을 사용합니다.
- 카드→미션 버튼 8dp, 버튼 묶음→틈새운동 32dp.
- 기존 GPT 생성 운동 매트와 닫힘/열림 자물쇠. 미션 완료 전 잠김, 완료 후 노란 면.
- Pretendard 신문형 틈튼일보, 둥근 테두리, 기존 원본 비버에 작은 신문만 추가. 날짜·장식 화살표 없음.
- 흰 허리둘레 카드, 초록 숫자, 실제 표시값 ±5cm의 긴 줄자. 주황 표시는 값의 위치이며 측정/건강 등급이 아님.
- 기존 알림함 진입은 작은 종 아이콘으로 유지.
- 쉼·포기·일시정지도 같은 홈 구조. 폐기된 홈 마스코트 배치로 분기하지 않음.
- v7의 하루 5개 틈새운동, 센서 응원/일시정지/달성 **정지 이미지**, 월요일 주간 기준, 생년월 문구 개선, 런치 GIF, 내부 XAI/주간 기록 연결은 유지.

예전 걷기 프레임·GIF·분리된 팔다리 합성, 검은 허리둘레 카드, 옛 신문 비버, 이전 색상/레이아웃 샘플은 홈에서 참조하지 않습니다. 다른 기능에서 사용하는 기존 정상 리소스를 이름만 보고 일괄 삭제하지 않습니다.

## 6. 검증 범위

실제 실행 결과는 `docs/VALIDATION.md`와 JSON·휴대폰 캡처에 있습니다. **이 PC의 Windows 및 Linux WSL 검증**이며, 담당자의 실제 EC2/MySQL에 배포하거나 실제 회원으로 최종 로그인을 수행한 결과는 아닙니다. 위 서버 배포·마이그레이션과 회원 시나리오 확인은 담당자 실행 단계입니다.
