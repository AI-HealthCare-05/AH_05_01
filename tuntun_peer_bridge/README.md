# 튼튼지수 앱 연결 검토본 v0.2 — 2026-09-09

혁수님께: 기존 v0.1 모델·또래 참조를 그대로 사용하면서 출생연월, 전체 앱 입력, 영역별 등수 표시와 실모델 HTTP 연결을 추가했습니다. 아래 순서대로 검증 후 앱/서버 연동 결과를 회신해 주세요. 이 ZIP만으로 현재 앱이 자동 수정되거나 배포되지는 않습니다.

## 이번 변경과 유지 사항

- 생년월일의 '일'을 요구하지 않습니다. 출생연도·월과 서버가 정한 한국 기준일로 나이를 계산합니다. 생일 월에는 가능한 낮은 만 나이를 월말 전까지 사용하고 `ageContext`에 범위와 근사 여부를 반환합니다. 예: 1991년 9월생은 2026-09-09에 34~35세, 사용값 34세; 9월 30일에는 35세입니다. 19세 경계가 불확실하면 건강모델을 계산하지 않습니다.
- 신체·당뇨·고혈압·생활습관 카드는 `components[].rankDisplay.text`를 표시합니다. 예: **또래 100명 중 약 21등**. 기존 `absoluteReferenceScore`는 상세 참고값으로만 사용합니다. 기존 `display.text`의 상위 % 문구를 카드에 연결하지 않습니다.
- 홈 종합은 `compositeDisplay.text`로 **튼튼지수 75.6점**처럼 표시합니다. 종합은 가용 4영역 백분위의 동일 비중 평균이며 종합 자체의 등수나 백분위가 아닙니다. 기존 '양호구간' 등 절대점수 구간은 새 종합에 그대로 붙이지 않습니다 (`bandLabel=null`).
- 저강도 시간·근력 강도·취침/기상은 누락 없이 받아 `habitContext` 설명에 반영합니다. 사용자가 승인한 범위에 따라 기존 점수에는 임의 가산하지 않습니다. 재학습·건강점수 산식 변경은 없습니다.
- 근력 입력의 **횟수와 운동한 일수**를 구분합니다. 기존 모델은 주당 일수이며 5일 이상을 5로 처리합니다. 양수인데 단위가 불명확하거나 세션 수라면 일수로 임의 변환하지 않습니다.
- 응답에는 `inputRevision`, `schemaVersion`, 모델·참조·산식·표시 버전과 `isMock=false`가 있습니다. 모델 오류를 고정 80점으로 바꾸는 fallback은 없습니다.

## 1. 압축 해제 및 환경

ZIP을 새 폴더에 풀고 README, src, core_bundle이 보이는 최상위 폴더에서 실행합니다. 모델 환경은 **Python 3.14.7**과 requirements.txt의 정확한 버전입니다. 3.14.0rc2나 서버의 3.13에서 모델을 직접 로드하거나 환경 검사를 우회하면 안 됩니다. 서버가 3.13이면 별도 3.14.7 프로세스의 HTTP 결과를 호출합니다.

PowerShell 예시입니다. `python` 명령이 실제 3.14.7을 가리키도록 먼저 설치/경로를 맞춰 주세요.

```powershell
python --version
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

전달 메시지에 기재된 manifest SHA-256과 `TRUSTED_MANIFEST_SHA256.txt`가 일치하는지 확인합니다. ZIP 내부의 해시 파일만을 독립된 신뢰 근거로 삼지 마세요. 이후:

```powershell
$releasePin = (Get-Content .\TRUSTED_MANIFEST_SHA256.txt -Raw).Trim()
.\.venv\Scripts\python.exe -X utf8 -m src.tuntun_app_vnext validate --release . --manifest-sha256 $releasePin
.\.venv\Scripts\python.exe -X utf8 -m src.tuntun_app_vnext infer --release . --manifest-sha256 $releasePin --request examples/base_64kg.json
.\.venv\Scripts\python.exe -X utf8 -m src.tuntun_app_vnext serve --release . --manifest-sha256 $releasePin --port 8766
```

마지막 명령은 계속 실행됩니다. 종료는 Ctrl+C. 별도 터미널에서 아래 검사를 실행합니다. 이 클라이언트는 표준 라이브러리만 사용하므로 서버의 Python 3.13에서도 실행할 수 있습니다.

```powershell
python -X utf8 tools/check_tuntun_app_endpoint.py --receipt endpoint-check.json
```

브리지는 `127.0.0.1:8766`에만 바인딩하며 공개 운영 서버가 아닙니다. 기존 백엔드가 이 로컬 서비스에 요청하도록 연결하세요. 별도 호스트/컨테이너라면 내부망 라우팅·접근통제를 백엔드 환경에 맞게 구성해야 합니다. Android 기기에서 127.0.0.1은 기기 자신이므로 앱은 백엔드 주소를 호출해야 합니다. 기존 `/v2` 응답 DTO를 조용히 교체하지 말고 별도 경로/기능 플래그로 검토하세요.

## 2. 요청 계약

`POST /score/peer/v2`, `Content-Type: application/json`, `X-Tuntun-Schema: tuntun-app-vnext-v0.2`. 전체 예시는 examples/*.json, 정확한 DTO는 request.schema.json과 response.schema.json입니다.

| 앱 입력 | 요청 필드 | 변환·반영 |
|---|---|---|
| 출생연도 / 월 | birthYear / birthMonth | 정수, 정확한 생일을 만들지 않음 |
| 성별 | sex | male / female |
| 임신 체크 | pregnancyStatus | 여성은 pregnant / nonpregnant / unknown; 남성은 not_applicable. 누락은 unknown이며 여성 미응답을 비임신으로 추정하지 않음 |
| 키·체중 | heightCm / weightKg | cm / kg, 미입력 null. 0이나 문자열로 보내지 않음 |
| 근력 주 빈도 | strengthWeeklyCount | 0~5; 5는 5일+. 백엔드의 실제 6·7일은 5로 상한 처리 |
| 근력 빈도 의미 | strengthFrequencyUnit | days / sessions / null. 앱 문구를 '주 몇 일'로 명확히 하거나 기존 항목의 일수 의미를 확인한 뒤 days로 전송. 세션 수를 days로 허위 표기하지 않음 |
| 근력 강도 | strengthIntensity | 가볍게(15회+) → light, 적당히(10~12회) → moderate, 힘들게(8회) → hard. 안 함이면 null |
| 저·중·고강도 분/주 | aerobicLowMinutes / aerobicModerateMinutes / aerobicVigorousMinutes | 실제 0은 0, 미응답은 null. 점수용 환산시간은 중강도+2×고강도, 두 항목 모두 입력돼야 계산 |
| 취침 / 기상 | bedtime / wakeTime | HH:MM 또는 null. 자정을 넘어도 계산. 두 시각이 같으면 0시간으로 단정하지 않음 |
| 기준일 | referenceDate | 백엔드가 한국 날짜 YYYY-MM-DD로 지정. 예시 재현은 2026-09-09로 고정 |
| 입력 개정 ID | inputRevision | 저장 시 새 opaque ID 발행, 응답까지 보존. 개인정보를 ID에 넣지 않음 |

취침~기상 간격은 실제 수면시간이나 수면의 질이 아닙니다. 강도 선택과 저강도 분을 화면 설명에 노출하되, 강도를 높이면 점수가 오른다고 안내하지 마세요. 선택 입력이 없으면 설명 영역을 숨기거나 미입력으로 표시하세요.

## 3. 표시와 오류 처리

영역은 componentKey로 매핑합니다. 종합은 `scoreAvailable`부터 확인하고 가용 영역 수가 4 미만이면 부분 산출을 표시합니다. 0개이면 점수를 숨기세요 (`isPartialScore`는 기존 계약상 0개에서도 true). 영역 `available=false`이면 rankDisplay.text 또는 미산출을 표시하며 null을 0점/100등으로 바꾸면 안 됩니다. `rankRange`와 `tieNotice`는 동점 설명에 사용할 수 있습니다.

등수 환산은 `round_half_up(100-peerPercentile)`를 1~100으로 제한합니다. 실제 100명 순번이 아니므로 **약**을 유지합니다. 개발 표본의 동일 성별·연령대 상대 위치이며 전국민의 정확한 순위가 아닙니다.

409는 스키마 버전 불일치, 422는 입력/계약 오류, 503은 모델 실행 오류입니다. 모든 실패에서 새 점수를 표시하지 말고 재시도/오류 상태를 표시합니다. 이전 값이 남아 있다면 과거 결과임을 명확히 해야 하며 새 입력 결과로 표시하면 안 됩니다. 모델 파일/환경 검증 실패는 HTTP 시작 전에 종료됩니다. `GET /health`는 로드 여부 확인용입니다.

브리지는 요청 캐시·DB·요청 본문 로그를 사용하지 않습니다. 앱 저장과 조회의 연결은 백엔드에서 구현해야 합니다. 저장 성공 후 최신 입력 전체로 재호출하고 캐시를 무효화하며, 응답 inputRevision이 최신 저장 ID와 다르면 화면 갱신에 사용하지 마세요.

## 4. 실제 모델 대조값

1991년 1월생, 남성, 170cm, 근력 4**일**/주·적당히, 저60/중120/고0분, 2026-09-09 기준입니다. 앱의 '4회'가 4일을 의미한다는 확인이 이 예시의 전제입니다.

| 체중 | 종합 | 신체 | 당뇨 | 고혈압 | 생활습관 |
|---|---:|---:|---:|---:|---:|
| 64kg | 75.6점 | 약 21등 | 약 34등 | 약 19등 | 약 24등 |
| 164kg | 21.1점 | 약 100등 | 약 98등 | 약 94등 | 약 24등 |

정밀 값은 *.expected.json에 있습니다. 두 입력에 모두 80/74/78/76/90을 표시하면 이 실모델 결과와 일치하지 않습니다. 이전 절대 종합 96.33/63.18과 새 백분위 평균 종합 75.59/21.08을 혼용하지 마세요.

점수 기반 자체는 유지했습니다: 신체는 기존 예측 허리둘레와 잔차 참조를 사용하는 참고점수, 당뇨·고혈압은 각각 기존 보정확률 p의 100×(1-p), 생활습관은 유산소 달성점수 min(100,환산분/150×100)와 근력 달성점수 min(100,일수/2×100)의 가용 평균입니다. 또래 생활습관 백분위에는 두 항목이 모두 필요합니다. 상세 알고리즘과 검증 한계는 core_bundle 및 CORE_VALIDATION_REPORT.md에 있습니다.

## 5. 앱 수용 검사 및 회신

1. Mock 플래그와 고정 응답 경로를 끄고 모델 브리지 health를 확인합니다.
2. 위 64kg 입력을 저장해 75.6점과 영역 등수를 확인합니다. 네트워크 응답과 UI 양쪽에서 동일한 inputRevision인지 확인합니다.
3. 체중만 164kg으로 저장해 21.1점 및 새 등수로 바뀌는지, 앱 재시작 후에도 저장한 입력과 결과가 일치하는지 확인합니다.
4. 64kg으로 되돌려 원래 값으로 돌아오는지 확인합니다. 비동기 요청을 빠르게 반복해 오래된 응답이 최신 응답을 덮지 않는지도 확인합니다.
5. 저강도·근력 강도·취침/기상만 변경해 설명이 바뀌고 수치가 유지되는지 확인합니다. 중강도·고강도·근력 일수는 0과 미입력을 구분합니다. 구간화·포화·동점 때문에 모든 작은 입력 변화가 표시 등수 변화로 이어지는 것은 아닙니다.
6. 생일 월 전/중/말과 19세 경계, 임신/임신상태 미상, 선택 수면 미입력, 잘못된 입력, 모델 서버 중단을 확인합니다. 실패 시 80점 Mock이 나타나면 안 됩니다.
7. 백엔드 연결 후 아래 명령의 URL을 실제 검토용 경로로 바꿔 다시 검사합니다. 인증이 필요하면 토큰을 환경변수에 보관하고 `--token-env TUNTUN_REVIEW_TOKEN` 옵션을 사용합니다.

```powershell
python -X utf8 tools/check_tuntun_app_endpoint.py --url https://REVIEW-BACKEND/score/peer/v2 --receipt backend-check.json
```

회신에는 앱 빌드/commit, 서버 commit, Python 버전, manifest pin, endpoint-check/backend-check 결과, 위 1~6의 확인 여부를 적어 주세요. 실사용자 개인정보가 담긴 로그는 보내지 마세요. 이 패키지의 로컬 API PASS는 실제 앱 UI 또는 운영 반영 PASS를 의미하지 않습니다.

## 유지되는 검토 과제

또래 참조는 2019~2021 개발자료, 비가중·영역별 가용 집단, 성별 및 19~39/40~64/65+ 연령대입니다. **40·65세 경계에서 순위가 크게 달라지는 문제가 해결되지 않은 로컬 검토 후보입니다.** 출생연월 정책은 나이를 계산하는 방식이며 이 경계 문제를 해소하지 않습니다. 운영 기본안 채택, 연속 연령 참조, 임상 성능 검증을 승인한 전달본이 아닙니다.
