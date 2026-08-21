# TMTN 공동 개발 환경·버전 기준

기준일: 2026-08-21

상태: 팀 공통 기준선 v1

이 문서는 “누구 컴퓨터에서는 되고 다른 사람 컴퓨터에서는 안 되는” 상황을 막기 위한 단일 버전 기준입니다. 팀원은 임의로 major/minor 버전을 올리지 않고, 버전 변경은 별도 PR에서 함께 검증합니다.

## 기준 파일의 우선순위

문서와 설정이 다르면 다음 순서로 판단합니다.

1. CI와 빌드 설정: `.github/workflows/`, Gradle wrapper, Dockerfile
2. 잠금·버전 파일: `uv.lock`, `.python-version`, `libs.versions.toml`, Compose image tag
3. 프로젝트 제약: `pyproject.toml`, Gradle build script
4. 이 문서와 README

불일치를 발견하면 설정에 몰래 맞추지 말고 문서와 설정을 같은 PR에서 수정합니다.

## 팀 공통 버전표

### 백엔드·로컬 도구

| 항목 | 팀 기준 | 강제 위치 | 비고 |
|---|---:|---|---|
| Python | `3.13.15` | `.python-version`, Dockerfile, CI | 3.14 사용 금지. `asyncmy` 호환 문제 |
| Python 허용 범위 | `>=3.13,<3.14` | `pyproject.toml` | 3.13의 patch 업데이트만 허용 |
| uv | `0.12.5` | `pyproject.toml`, Dockerfile, CI | 다른 버전이면 uv가 실행을 중단 |
| Python 패키지 | `uv.lock`에 기록된 정확한 버전 | `uv.lock` | `pip install` 직접 사용 금지 |
| IANA timezone DB | `tzdata` (`uv.lock` 기준) | `pyproject.toml`, `uv.lock` | Windows의 `Asia/Seoul` 지원 |
| Git | `2.45+` | 문서 기준 | `switch`, `restore`, 최신 보안 수정 사용 |
| Docker Engine | `29.6+` | 문서 기준 | Docker Desktop 사용 가능 |
| Docker Compose | `docker compose` plugin v2 이상 | 문서 기준 | 구형 `docker-compose` v1 금지 |

Docker Desktop의 앱 버전은 OS별 배포 번호가 달라 정확한 patch를 강제하지 않습니다. 대신 아래 명령이 성공하고 Engine·Compose 최소 기준을 만족해야 합니다.

```bash
docker --version
docker compose version
docker compose config --quiet
```

### 컨테이너 이미지

| 서비스 | 고정 이미지 | 용도 |
|---|---|---|
| API/Worker Python | `python:3.13.15-slim` | FastAPI·AI Worker base |
| uv | `ghcr.io/astral-sh/uv:0.12.5` | 컨테이너 의존성 설치 |
| MySQL | `mysql:8.0.46` | 운영·로컬 DB, ERD 검증 버전과 일치 |
| Redis | `redis:7.2.15-alpine` | Streams·cache. 7.2 계열 사용 |
| Nginx | `nginx:1.28.3-alpine` | reverse proxy·TLS entry |
| Certbot | `certbot/certbot:v5.7.0` | 인증서 발급·갱신 |

`latest`, `alpine`, `8.0`, `3.13`처럼 실행 시 내용이 달라지는 가변 태그를 새로 사용하지 않습니다. 운영 릴리스에서는 가능하면 검증한 image digest까지 기록합니다.

## Android 기준선

Android 프로젝트를 만들 때 아래 값을 최초 기준으로 사용합니다. alpha·beta·RC 라이브러리는 ADR과 팀 승인 없이 사용하지 않습니다.

### Android 도구chain

| 항목 | 고정 버전·값 | 선택 이유 |
|---|---:|---|
| Android Studio | `Quail 2 | 2026.1.2` stable | AGP 9.1 지원 공식 stable IDE |
| JDK | `17` | AGP 9.1의 기본·요구 JDK |
| Android Gradle Plugin | `9.1.1` | stable, API 36 지원 |
| Gradle Wrapper | `9.3.1` | AGP 9.1.1 기본 호환 버전 |
| Kotlin | `2.4.10` | Kotlin 2.4 최신 bugfix stable |
| SDK Build Tools | `36.0.0` | AGP·Android 16 기준 |
| `compileSdk` | `36` | Android 16 API 사용 |
| `targetSdk` | `36` | 2026-08-31 Google Play 신규 앱 기준 충족 |
| `minSdk` | `28` | Health Connect 실제 사용 가능 최소 Android 9 |
| Java/Kotlin bytecode target | `17` | JDK·AGP 기준 통일 |
| Compose BOM | `2026.08.00` | Compose stable 라이브러리 묶음 |

### Android 핵심 라이브러리 기준

Android 프로젝트가 생기면 `android/gradle/libs.versions.toml` 한 곳에 다음 버전을 기록합니다.

| 영역 | 기준 버전 | 정책 |
|---|---:|---|
| Firebase Android BOM | `34.16.0` | FCM 라이브러리는 개별 버전 대신 BOM 사용 |
| Health Connect client | `1.1.0` | stable만 사용, 읽기 전용 최소 권한 |
| Navigation Compose | `2.9.8` | stable. `2.10.0-rc` 사용 금지 |
| Lifecycle | `2.11.0` | stable |
| DataStore | `1.2.1` | 일반 설정 저장. refresh token 원문 저장 금지 |
| Room | `2.8.4` | 로컬 read cache, 서버 상태를 대체하지 않음 |
| WorkManager | `2.11.2` | 제한된 백그라운드 동기화 |

네트워크 클라이언트와 DI는 아직 프로젝트 결정이 없습니다. Retrofit/Ktor, Hilt/Koin 중 하나를 Android 구조 ADR에서 선택한 뒤 stable 버전을 `libs.versions.toml`에 고정합니다. 팀원이 개인 취향으로 둘을 섞어 추가하지 않습니다.

### Android에서 반드시 commit하는 파일

- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`
- `settings.gradle.kts`, 모든 `build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties` 중 비밀이 없는 공통 설정
- `AndroidManifest.xml`, ProGuard/R8 공통 규칙

### Android에서 commit하지 않는 파일

- `local.properties`, `.gradle/`, 모든 `build/`
- `google-services.json` 원본과 FCM service account key
- `.jks`, `.keystore`, signing password
- APK, AAB, APKS

## 최초 설치 절차

### 1. 저장소와 uv 확인

```bash
git clone https://github.com/AI-HealthCare-05/AH_05_01.git
cd AH_05_01
uv --version
```

uv가 없다면 공식 설치 방법으로 `0.12.5`를 설치합니다. 다른 uv가 설치되어 있어도 이 프로젝트는 `0.12.5`가 아니면 실행을 거부합니다.

### 2. Python 설치·확인

```bash
uv python install 3.13.15
uv run python --version
```

예상 출력:

```text
Python 3.13.15
```

기존 `.venv`가 Python 3.14로 만들어졌다면 재사용하지 않습니다. `.venv`에는 소스가 없어야 하며 삭제 가능한 로컬 산출물임을 확인한 뒤 다음처럼 다시 만듭니다.

```bash
uv venv --clear --python 3.13.15
uv sync --frozen --group app
```

### 3. 역할별 의존성

백엔드 개발자:

```bash
uv sync --frozen --group app
```

AI 개발자:

```bash
uv sync --frozen --group app --group ai
```

`uv.lock`을 바꾸지 않는 일반 설치에는 반드시 `--frozen`을 사용합니다. 패키지를 추가할 때만 `uv add <package> --group <group>`을 사용하고 변경된 `pyproject.toml`과 `uv.lock`을 함께 PR에 올립니다.

### 4. 환경 변수

PowerShell:

```powershell
Copy-Item envs/example.local.env .env
```

macOS/Linux:

```bash
cp envs/example.local.env .env
```

`.env`의 예시 값을 개인 로컬 값으로 바꾸되 파일 자체는 commit하지 않습니다. 실제 팀 비밀은 GitHub Secrets 또는 승인된 비밀 저장소에서 전달합니다. 메신저·Notion·PR에 붙여 넣지 않습니다.

### 5. 설치 검증

```bash
uv --version
uv run python --version
uv lock --check
uv run ruff check .
uv run ruff format . --check
docker compose config --quiet
```

모든 명령이 성공해야 merge할 수 있습니다.

### 6. MySQL 통합 테스트

개발용 Compose는 MySQL 볼륨을 처음 초기화할 때 `.env`의 `DB_USER`에 `test` 데이터베이스 전용 권한을 자동 부여합니다. 전역 DB 생성 권한은 부여하지 않습니다.

```bash
docker compose up -d mysql
docker compose ps mysql
uv run coverage run -m pytest app
uv run coverage report -m
```

`mysql`이 `healthy`가 된 뒤 테스트를 실행합니다. 초기화 스크립트가 추가되기 전에 이미 만든 볼륨이라면 다음 명령을 한 번 실행합니다.

```bash
docker compose exec mysql bash /docker-entrypoint-initdb.d/01-grant-test-database.sh
```

이 스크립트는 반복 실행해도 같은 권한만 유지합니다. 테스트 세션은 격리된 `test` 데이터베이스를 생성하고 종료 시 삭제하며, 서비스 데이터베이스는 사용하지 않습니다.

## Android 프로젝트 생성 시 체크리스트

- [ ] Android Studio stable 버전이 팀 기준과 일치한다
- [ ] JDK 17을 사용한다
- [ ] AGP·Gradle wrapper·Kotlin 버전을 위 표대로 설정했다
- [ ] `compileSdk=36`, `targetSdk=36`, `minSdk=28`이다
- [ ] Compose BOM과 Firebase BOM을 사용한다
- [ ] stable dependency만 사용한다
- [ ] `libs.versions.toml`과 Gradle wrapper를 commit했다
- [ ] debug/staging/release build type과 API base URL을 분리했다
- [ ] release signing 값은 로컬 파일이나 GitHub Secrets에서 주입한다
- [ ] `./gradlew lint testDebugUnitTest assembleDebug`가 성공한다

## 서비스·데이터 버전

| 항목 | 규칙 |
|---|---|
| 애플리케이션 버전 | Semantic Versioning `MAJOR.MINOR.PATCH` |
| 최초 개발 버전 | `0.1.0` |
| Git tag | `v0.1.0` 형식 |
| Docker API image | `app-0.1.0` 형식 |
| Docker Worker image | `ai-0.1.0` 형식 |
| Android `versionName` | 앱 릴리스 SemVer와 일치 |
| Android `versionCode` | 배포마다 증가하는 정수, 절대 재사용 금지 |
| API | `/v1` 계약. breaking change는 `/v2` |
| DB migration | Aerich migration 파일을 순서대로 commit |
| 문서·동의·규칙 | 각 문서와 응답에 독립 version 필드 유지 |
| 모델·프롬프트 | model version, feature version, prompt version 기록 |

백엔드와 Android가 항상 동시에 업데이트된다고 가정하지 않습니다. 서버는 최소 지원 앱 버전과 현재 앱 버전을 구분하고, v1 안에서는 구버전 앱의 계약을 깨지 않습니다.

## 버전 변경 절차

1. `chore/deps-<date>` 또는 이슈 기반 브랜치에서만 변경합니다.
2. 왜 올리는지, 보안 공지·호환 변경·rollback을 PR에 적습니다.
3. Python은 `.python-version`, `pyproject.toml`, Dockerfile, CI를 함께 수정합니다.
4. Android는 version catalog, Gradle wrapper, AGP·Kotlin, CI 문서를 함께 수정합니다.
5. Compose·Firebase는 BOM 단위로 업데이트합니다.
6. lockfile과 생성 파일 diff를 검토합니다.
7. 백엔드·Android 전체 CI와 staging smoke test를 통과합니다.
8. release branch에서는 보안 hotfix 외의 도구chain 업그레이드를 하지 않습니다.

Patch 보안 업데이트는 가능한 빨리 반영하고, minor/major 업데이트는 스프린트 시작 시 별도 PR로 처리합니다. preview 버전이 꼭 필요하면 ADR에 사용 이유, 격리 범위, 제거 조건을 기록합니다.

## 공식 근거

- [uv Python 버전과 `.python-version`](https://docs.astral.sh/uv/concepts/python-versions/)
- [uv `required-version` 설정](https://docs.astral.sh/uv/reference/settings/)
- [Android Studio Quail 2·AGP 지원 범위](https://developer.android.com/studio/releases/)
- [AGP 9.1.1 호환 Gradle·JDK](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Android 16 SDK 설정](https://developer.android.com/about/versions/16/setup-sdk)
- [Google Play target API 기준](https://developer.android.com/google/play/requirements/target-sdk)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Health Connect 지원 OS](https://developer.android.com/health-and-fitness/health-connect/availability)
- [AndroidX stable 버전표](https://developer.android.com/jetpack/androidx/versions)
- [Firebase Android BoM](https://firebase.google.com/support/release-notes/android)
