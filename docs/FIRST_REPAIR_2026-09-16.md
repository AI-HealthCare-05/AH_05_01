# 첫 선물과 첫 댐 복구 적용

승인일·작성일: 2026-09-16. 기준: `origin/develop`의 `d2b88b4`.
작업 브랜치: `codex/first-repair-develop-2026-09-16`.

사용자가 승인한 [Figma A21 첫 복구 흐름](https://www.figma.com/design/ByGT2uoinUBxAQOBqAK7sM/TMTN-Final-App-UI?node-id=1530-3786)을 Android와 서버에 연결했다. 원본 작업 폴더는 보존하고, 기존 최종 UI 저장소에 최신 develop을 반영한 다음 이전 로컬 UI·문서 수정분을 충돌 없이 합쳤다. 원격 푸시와 운영 서버 배포는 하지 않았다.

## 사용자 흐름

신체·운동 정보 확인 → 첫 댐 만나기 → 0단계 댐과 첫 선물 소개 → **첫 재료 받기** → **이 재료로 틈 메우기** → 재료 이동 → 서버 저장 확인 → **1단계 · 첫 빈틈 받치기** → 오늘의 카드 고르기.

- 나뭇가지 **정확히 1개**를 가입 선물로 받는다. 5개를 지급해 기존 임계값을 우회하지 않는다.
- 수령 단계에는 아직 댐에 더하지 않은 선물로 표시한다. 복구 완료 후 댐의 누적 재료가 1개가 된다.
- 복구를 저장하기 전에 1단계 성공 화면을 띄우지 않는다.
- 복구 버튼 연속 누르기는 막는다. 통신 실패 후 같은 요청을 재시도해도 추가 선물을 지급하지 않는다.
- 앱이 종료되면 `FIRST_DAM` 체크포인트와 서버 상태로 이어간다. 수령 후에는 재료 놓기부터, 저장 후에는 완료 상태부터 다시 연다.
- 기존 계정은 재료와 단계를 그대로 표시한다. 기존 계정의 구글 연결도 신규 가입으로 취급하지 않는다.

## UI와 모션

| 항목 | 적용 기준 |
|---|---|
| 글꼴 | 기존 네이티브 Pretendard |
| 제목 | 28sp / 줄높이 36sp / SemiBold |
| 본문 | 15sp / 줄높이 24sp / Regular |
| 짧은 안내 | 13sp / 줄높이 20sp / SemiBold |
| 재료 이름·수량 | 기존 `missionName`, 18sp / 26sp / SemiBold |
| 좌우 여백 / 주요 간격 | 20dp / 24dp |
| 재료 카드 | 모서리 16dp, 안쪽 여백 16dp, 글자에 따라 높이 증가 |
| 주 행동 | 기존 공통 버튼·누름 피드백 재사용, 본문과 분리된 하단 영역 |
| 일러스트 | 기존 Figma 댐 0·1단계, 수채화 틈튼이, 공통 재료 이미지 `journal_material_branch` |
| 재료 이동 | 560ms, `TmtnMotion.EaseOut` (0.23, 1, 0.32, 1), 이동·축소·회전 |
| 정착 / 결과 전환 | 최소 180ms 정착 시간, 댐 결과 240ms 전환 |
| 동작 줄이기 | 공간 이동 생략, 결과는 160ms 전환. 저장 상태·문구는 동일 |
| 작은 화면·큰 글자 | 본문 스크롤, 하단 버튼 유지. 320dp·글자 1.8배 확인 |

프레임 수를 고정한 애니메이션이 아니다. 60Hz 화면에서 560ms는 약 34프레임 분량이지만 실제 프레임 수는 기기 주사율과 렌더링에 따른다. 저장 지연이 있으면 배치 상태에서 기다리며 반복으로 튀는 모션을 재생하지 않는다.

## 서버 변경이 필요한 이유와 범위

기존 서버는 재료 5개부터 1단계를 계산했다. 승인한 “1개 선물 → 실제 1단계”를 화면 숫자만 바꿔 구현하면 재접속·댐 탭에서 다시 0단계가 된다. 따라서 **첫 복구를 완료한 새 계정에만** 첫 단계 기준을 1개로 적용한다.

| 단계 | 기존 계정 | 첫 복구 완료 계정 |
|---|---:|---:|
| 1 | 누적 5개 | 누적 1개 |
| 2 | 15개 | 15개 |
| 3 | 35개 | 35개 |
| 4 | 70개 | 70개 |
| 5 | 120개 | 120개 |

첫 복구 후 다음 단계까지 14개가 필요하다. 이후 단계의 기존 임계값은 변경하지 않았다.

### 데이터

새 `companion_first_repairs` 테이블의 기본키는 `user_id`다. `gift_received_at`, `completed_at`으로 수령·복구를 구분한다.

- 이메일 신규 가입과 구글 신규 가입에서 계정 생성과 같은 트랜잭션으로 자격 행을 만든다.
- 기존 계정은 자동 등록하거나 소급 지급하지 않는다. 마이그레이션은 테이블만 추가한다.
- 선물은 `five_element_completion_counts`에 쓰지 않는다. 복구가 완료되면 조회 시 WOOD 1개를 합산한다.
- 챌린지, 포인트 원장, 완료 카드, 실천 일수·연속 기록, 건강 점수 계산은 선물 때문에 갱신하지 않는다.
- `CompanionState` 다음 `CompanionFirstRepair` 순서로 행을 잠근다. 운동 완료도 같은 순서의 잠금 읽기로 선물 여부를 확인한다.
- 단계 로그는 중복 생성하지 않는다. 첫 단계 축하는 온보딩에서 보여주므로 댐 탭에서 반복하지 않는다.
- 계정 삭제 시 첫 복구 행도 삭제한다. 외래키의 삭제 전파도 적용된다.

### API

인증된 현재 사용자 기준이며 클라이언트가 지급 수량·대상 사용자를 지정하지 않는다.

| 메서드 | 경로 | 역할 |
|---|---|---|
| GET | `/api/v1/companion/first-repair` | `ELIGIBLE`, `GIFT_RECEIVED`, `COMPLETED`, `UNAVAILABLE` 조회 |
| POST | `/api/v1/companion/first-repair/gift` | 첫 선물 수령, 재시도해도 1개 |
| POST | `/api/v1/companion/first-repair/complete` | 수령한 선물로 첫 복구 저장 |

응답에는 `status`, `gift_element`, `gift_count`, 기존 형식의 `companion`이 들어간다. 기존 `/companion` 응답 구조는 유지한다. 재료 이력 응답에 선택적으로 호환 가능한 `welcome_gift_count`(기본 0)를 추가했다. Android는 첫 선물과 운동 카드 이력을 구분해 표시한다.

수령 전 복구 요청이나 자격 없는 기존 계정의 지급 요청은 409다. 구버전 서버에서 GET이 404/405이면 기존 댐 소개 화면을 사용한다. 일반 네트워크 오류·401·500을 구버전으로 간주하거나 성공으로 처리하지 않는다.

## 배포 순서

1. 새 마이그레이션 `19_20260916140753_add_first_repair.py`를 서버 코드와 함께 검토한다.
2. 배포 환경에서 `uv run aerich upgrade`로 테이블을 추가한다.
3. 첫 복구 API와 신규 가입 처리를 포함한 서버를 배포한다.
4. Android를 업데이트하고 **새 테스트 계정**으로 온보딩을 확인한다.

운영 DB에는 이번 작업에서 접속하거나 마이그레이션을 실행하지 않았다. API를 배포하지 않은 서버에서는 기존 댐 화면이 나오는 것이 호환 동작이다. APK만 바꿔서는 영속적인 첫 선물이 활성화되지 않는다.

롤백 때 첫 복구 데이터를 지우거나 생성된 downgrade를 무조건 실행하면 안 된다. 첫 복구를 마친 계정은 선물 합산과 단계 기준이 필요하다. UI를 되돌려도 서버의 기존 지급 데이터 읽기 처리는 유지해야 한다.

## 마이그레이션 생성·검증 방법

SQL을 직접 작성하지 않고 Aerich가 모델 변경으로 생성했다. 설치된 Aerich는 `--offline`에도 MySQL 버전 조회를 시도하여, 외부 QA 스크립트에서 **버전 조회 한 곳만 MySQL 8.0.46으로 지정**하고 공식 `migrate --offline` 명령의 비교·SQL 생성을 사용했다. 결과는 테이블 1개 추가이며 기존 테이블 변경은 없다.

PC의 uv는 0.12.3으로 저장소 요구 버전 0.12.5와 달라, 기존 Python 3.13.15 가상환경의 Aerich를 직접 실행했다. 저장소의 uv 요구 버전이나 잠금 파일을 바꾸지 않았다. MySQL 실제 적용·다중 워커 동시성 검증은 서버 배포 환경에서 추가로 확인해야 한다.

## 변경 파일

- Android `ui/onboarding/FirstRepairState.kt`, `FirstRepairScreen.kt`: 상태 전이·새 화면·모션.
- 기존 `FirstDamScreen.kt`: 구버전 서버·기존 사용자용 경로로 유지.
- `network/CardHomeApi.kt`, `network/model/FirstRepairResponse.kt`: API 연결.
- `network/model/DamModels.kt`, `ui/dam/DamHistoryScreens.kt`: 첫 선물 이력 구분.
- `ui/theme/TmtnType.kt`, `TmtnMotion.kt`: 해당 화면의 글자·모션 토큰.
- 서버 `app/models/companion.py`, `dtos/companion.py`, `repositories/companion_repository.py`, `services/companion_service.py`, `apis/v1/companion_routers.py`: 지급·저장·조회.
- `repositories/user_repository.py`, `services/users.py`: 신규 가입 등록·계정 삭제.
- `app/core/db/migrations/models/19_20260916140753_add_first_repair.py`: 생성된 스키마 변경.
- `tests/first_repair/test_first_repair.py`, Android `FirstRepairStateTest`, `FirstRepairUiTest`, `FirstRepairNetworkUiTest`: 검증.

이번 문서의 파일 목록은 첫 복구 변경분이다. 작업 폴더에는 이전에 승인된 홈·시트 모션·동의 문서·주간면 등의 로컬 수정분도 함께 보존되어 있다.

## 확인 결과

- 최신 develop `d2b88b4` 위에 이전 로컬 Android·문서를 충돌 없이 적용.
- Android JVM 테스트 102개 통과. 새 첫 복구 상태 테스트 6개 포함.
- 서버 첫 복구 테스트 11개 통과: 이메일·구글 신규 가입, 기존 계정 보존, 수령/저장 재시도, 재진입, 운동 기록 분리, 이후 단계 진행, 늦은 복구, 트랜잭션 롤백, API 계약.
- 연결된 SM-S916N, Android 16에서 관련 UI 테스트 22개 통과: 첫 복구, HTTP 통합, 기존 댐 소개, 구글 진입/재인증, 동의 문서, 월요일 시작 주간면.
- HTTP 통합은 로컬 SQLite QA DB·가짜 로그인 계정과 실제 Router → Service → Repository를 사용했다. 운영 서버나 실제 사용자 계정을 이용한 검증이 아니다.
- Ruff 검사 통과. DTO·복구 Repository·Service·Router 4개 파일 대상 mypy 검사 통과.
- Android Lint 오류 0. 저장소 기존 경고와 정보 항목은 별도 보고서에 남아 있다.
- 스크린샷을 직접 확인해 댐·캐릭터 잘림, 제목·버튼 줄맞춤, 수채화 재료 일관성을 확인했다. 큰 글자에서는 본문 스크롤로 안내 전체를 읽을 수 있다.

서버 전체 MySQL 테스트, 운영 DB 마이그레이션, 운영 계정의 전체 회원가입 E2E까지 검증했다는 의미는 아니다.
