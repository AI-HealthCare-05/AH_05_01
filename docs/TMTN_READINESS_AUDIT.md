# 틈튼(TMTN) 저장소 준비도 점검

점검일: 2026-08-21

대상: `AH_05_01` 저장소

기준: TMTN 최신 Android 요구사항, API 명세, ERD, 강의 메모 PDF, 현재 코드와 CI 설정

## 결론

이 저장소는 GitHub에 올려 공동 작업을 시작할 수 있는 **FastAPI 백엔드 템플릿**입니다. 그러나 현재 상태만으로 틈튼 서비스를 완성하거나 APK를 만들 수는 없습니다.

- 현재 구현: 인증·사용자 API를 포함한 백엔드 기초, MySQL·Redis·Nginx Docker 구성, 테스트와 CI의 초안
- 현재 미구현: Android 앱 전체, TMTN 핵심 도메인 대부분, 실제 AI 워커와 Redis Stream 처리, 운영 보안·관측성
- API 범위: TMTN OpenAPI 계획은 56개 경로·64개 작업인 반면 현재 라우터는 5개 작업
- APK: Android Gradle 프로젝트, `AndroidManifest.xml`, Compose 화면, 서명·빌드 설정이 전혀 없음

따라서 현재 코드는 “없애야 할 웹 코드”가 아닙니다. **APK가 호출할 서버 백엔드**로 발전시킬 코드입니다. Android 코드는 별도 클라이언트로 추가해야 합니다.

## PDF 내용과 현재 폴더의 대응

강의 메모 PDF에 있는 구조는 웹 브라우저 전용이 아니라 모바일 앱 백엔드에도 그대로 필요합니다.

| PDF의 항목 | 현재 상태 | 판단 |
|---|---|---|
| `app/apis/v1` 버전 라우터 | 있음 | 유지. 단, 공개 경로 계약을 `/api/v1` 또는 `/v1` 중 하나로 확정해야 함 |
| Router → Service → Repository | 인증·사용자에 적용 | TMTN 각 도메인으로 확장 필요 |
| Core(DB, JWT, 설정) | 있음 | 운영 보안과 세션 회전·폐기 보강 필요 |
| Tests | 인증·사용자 테스트가 있음 | 도메인·계약·통합·Android 테스트 추가 필요 |
| AI Worker 분리 | 폴더와 컨테이너만 있음 | `ai_worker/main.py`와 작업 구현이 비어 있음 |
| Redis | 컨테이너만 있음 | Stream consumer group, 멱등성, 재시도, DLQ 구현 없음 |
| Docker/Nginx/배포 스크립트 | 있음 | 운영 포트·비밀·이미지 태그·헬스체크 보강 필요 |
| API 하위호환성 | `v1` 폴더만 있음 | OpenAPI 계약 검사와 deprecation 정책 추가 필요 |

## 갖춰진 항목

- Git 저장소와 GitHub 원격 저장소가 연결되어 있고, 점검 시 작업 트리는 깨끗함
- FastAPI 앱과 `/api/v1` 라우터 조립 구조
- 회원가입, 로그인, 토큰 갱신, 내 정보 조회·수정 API
- Router / Service / Repository / DTO / Model 계층 분리의 초안
- Tortoise ORM, Aerich 마이그레이션, MySQL 연결 설정
- JWT 발급·검증과 비밀번호 해시 처리
- Docker Compose 기반 MySQL, Redis, FastAPI, AI Worker, Nginx 구성
- Ruff, Mypy, Pytest·Coverage용 도구와 GitHub Actions 초안
- `.env`, `.venv`, 캐시 파일을 제외하는 기본 `.gitignore`
- 실제 `.env`와 가상환경·캐시·빌드 산출물은 현재 Git 추적 대상이 아님

## 빠진 항목과 우선순위

### P0 — 개발을 시작하기 전에 결정·보완

1. **Android 앱 프로젝트**
   - Kotlin, Jetpack Compose, Gradle, AndroidManifest, 화면·내비게이션, 네트워크·로컬 저장소가 없음
   - FCM, Health Connect, Sharesheet, 위치 권한, TalkBack, Android Keystore 구현이 없음
   - APK/AAB 빌드와 서명, `debug`/`staging`/`release` 변형, Android CI가 없음

2. **저장소 구조 결정**
   - 한 저장소에서 관리한다면 현재 Python `app/`과 Android 기본 모듈 `app/`의 이름이 충돌함
   - 권장 목표 구조:

     ```text
     .
     ├── backend/             # 현재 FastAPI·worker·infra 이동 대상
     ├── android/             # Android Compose 프로젝트
     ├── contracts/           # tmtn_openapi.yaml, 공통 예제
     └── docs/                # 요구사항·ERD·협업 문서
     ```

   - 구조 변경은 별도 ADR과 전용 PR로 수행하고 기능 개발과 섞지 않음

3. **API 계약 드리프트 해소**
   - 현재: `/api/v1/auth/signup`, `GET /api/v1/auth/token/refresh`
   - 최신 계약: `/v1/auth/sign-up`, `POST /v1/auth/refresh`
   - 현재 로그인 응답은 access token만 반환하고 refresh token을 쿠키에 저장하지만, Android 요구사항은 refresh token을 Keystore에 저장하는 계약임
   - 모바일 구현 전에 `tmtn_openapi.yaml`을 저장소의 단일 계약으로 넣고 서버·Android가 함께 따르도록 해야 함

4. **인증·개인정보 모델 수정**
   - 실행 시마다 달라질 수 있는 기본 JWT secret은 다중 워커·재시작에서 토큰을 무효화할 수 있음. 운영에서는 비밀 누락 시 즉시 시작 실패해야 함
   - refresh token 회전, 해시 저장, 기기별 세션, 재사용 탐지, 로그아웃·폐기가 없음
   - `users.email`과 `phone_number`에 DB UNIQUE 제약이 없어 동시 가입에서 중복 가능
   - 현재 가입은 전체 생년월일·성별·전화번호를 필수 수집하지만 최신 요구사항은 데이터 최소화와 출생연도·선택 성별을 전제로 함
   - 계정 삭제·데이터 내보내기·동의 이력·감사 로그가 없음

5. **TMTN 핵심 도메인**
   - 최신 ERD는 51개 테이블을 정의하지만 현재는 사실상 사용자 테이블만 구현됨
   - 카드 세트, 카드 선택, 챌린지 상태 전이, 기운 원장, 활동 기록, 타임라인, 리포트, 알림, Health Connect, 공유, 개인정보 도메인이 없음
   - 카드 선택·시작·완료·skip에는 idempotency key, 서버 시각, 트랜잭션, UNIQUE 제약이 필수

6. **현 상태의 품질 게이트 결과와 잔여 과제**
   - import 정렬과 wildcard import 8건을 수정하여 `ruff check .`와 포맷 검사가 통과함
   - Windows의 Python 3.14에서 `asyncmy==0.2.11` 설치가 실패했으며, 현재는 `.python-version=3.13.15`와 `requires-python >=3.13,<3.14`로 재발을 차단함
   - Python 3.13.15와 MySQL 8.0.46에서 Mypy 52개 파일과 Pytest 9개 전체 테스트가 통과했고 테스트 커버리지는 91%임
   - 새 MySQL 볼륨은 `DB_USER`에 격리된 `test` 데이터베이스 전용 권한을 자동 부여함
   - 운영 Compose는 `infra/docker/.env`를 기대해 예시 env만으로 독립 검증에 실패함

### P1 — 첫 수직 기능 전에 구현

- `GET /health/live`, `GET /health/ready`와 DB·Redis readiness
- RFC 7807 또는 팀 표준 형태의 전역 에러 응답과 request/trace ID
- OpenAPI 변경 감지와 Android SDK 생성 또는 계약 테스트
- Redis Stream consumer group, `XACK`, pending 복구, 제한 재시도, DLQ, 멱등 처리
- 데이터베이스 마이그레이션 CI와 롤백·백업 절차
- 구조화 로그, 민감정보 마스킹, 메트릭·알람
- 테스트 DB 격리, 최소 커버리지 기준, 핵심 상태 전이·동시성·멱등성 테스트
- Dependabot, 비밀 탐지, SAST, 이미지 취약점 검사
- 개인정보처리방침·동의 문서의 버전 관리와 보존·파기 작업

### P2 — 배포 전 보강

- 운영 Redis와 MySQL 포트를 공인망에 노출하지 않도록 Compose·보안 그룹 수정
- 현재 고정된 Nginx·Redis·MySQL·Certbot tag를 운영 릴리스에서 digest까지 고정
- TLS, 백업 복구 연습, 장애 대응·롤백 런북
- 성능 목표와 부하 테스트, 카드 생성·AI 작업의 타임아웃·폴백
- APK/AAB 서명키 관리, Play App Signing 또는 팀 비밀 저장소, 난독화·릴리스 노트
- 접근성, 오프라인 읽기 캐시, 저사양·다크모드·시니어 모드 QA

## GitHub에 올려도 되는 것

### 올려도 됨

- 직접 작성했거나 사용 허가가 확인된 소스 코드
- `pyproject.toml`, `uv.lock`, Dockerfile, Compose, Nginx 설정
- 값이 전부 예시인 `example.*.env`
- 마이그레이션, 테스트, OpenAPI 계약, ERD·요구사항·협업 문서
- 재생성 가능한 작은 seed와 라이선스가 명확한 정적 자산

### 올리면 안 됨

- `.env`, 실제 DB·Redis 비밀번호, JWT secret, OAuth·FCM 키
- Android keystore, `.jks`, `.keystore`, signing password
- `.venv`, `__pycache__`, IDE 설정, 로그, DB volume, 인증서
- APK/AAB와 Docker 이미지 같은 재생성 가능한 대용량 빌드 산출물
- 원본 건강 데이터, 개인 데이터, 사용 허가가 불명확한 이미지·폰트·강의 자료
- 학습된 모델과 대용량 데이터는 Git LFS·모델 레지스트리·오브젝트 스토리지를 검토

강의 템플릿에서 가져온 코드는 기능이 일반적이라는 이유만으로 자동 재배포 권한이 생기지 않습니다. 강사·교육기관의 과제 규정과 라이선스를 확인하고, 필요하면 `LICENSE`와 `NOTICE`에 출처를 기록합니다. PDF 원본도 팀 내부 메모로만 사용하고 배포 권한이 불명확하면 저장소에는 요약만 남깁니다.

## 저장소에 추가할 기준 산출물

최신본을 검토한 뒤 다음처럼 배치하는 것을 권장합니다.

```text
contracts/
└── tmtn_openapi.yaml

docs/specs/
├── TMTN_요구사항정의서.md
├── TMTN_API명세서.md
├── TMTN_ERD.md
├── TMTN_브랜드컬러가이드.md
└── TMTN_미션템플릿설계서.md

backend/migrations-or-schema/
├── tmtn_schema_mysql8.sql
└── tmtn_mission_seed.sql
```

민감정보·개인 경로·회의 메모·폐기된 PWA 결정은 제거하고, 문서마다 버전·상태·기준일·담당자를 표시합니다. 특히 별도 `AH_05_01(app)` 폴더의 “React/PWA + Capacitor” 문서는 최신 “Android 네이티브 Compose” 결정과 충돌하므로 그대로 복사하지 않습니다.

## 권장 구현 순서

1. ADR로 모노레포 구조와 공개 API base path 확정
2. OpenAPI·요구사항·ERD의 검토본을 저장소에 편입
3. 고정된 Python 3.13.15 환경에서 Ruff·Mypy·Pytest·Compose 검증 복구
4. 인증·세션·동의·개인정보 모델을 최신 계약으로 재설계
5. Android Compose 셸과 네트워크·Keystore·공통 UI 상태 구성
6. `daily-bootstrap → 카드 3장 → 선택 → 시작 → 완료 → 원장` 수직 슬라이스 구현
7. 알림·Health Connect·공유·KNHANES 참고 기능을 권한·고지와 함께 추가
8. 보안·접근성·성능·배포·APK/AAB 릴리스 게이트 통과

이 문서는 구현 완료를 뜻하지 않습니다. 체크된 항목은 PR과 CI 증거가 생길 때마다 갱신합니다.
