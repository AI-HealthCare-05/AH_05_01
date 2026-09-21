# 개발 환경

기준: 2026-09-21 `main`. 이 문서는 현재 빌드·잠금 설정을 설명하며 도구 버전을 변경하지 않습니다. 설정과 문서가 다르면 CI·Dockerfile·Gradle → 잠금 파일 → 프로젝트 제약 → 문서 순서로 확인합니다.

## 실제 버전

| 영역 | 값 | 기준 파일 |
| --- | --- | --- |
| API Python | 3.13.15 | `.python-version`, `app/Dockerfile` |
| API Python 범위 | `>=3.13,<3.14` | `pyproject.toml` |
| uv | 0.12.5 | `pyproject.toml`, CI |
| 개인 XAI Python | 3.14.7, SHAP 0.49.1 | `model_service/Dockerfile`, `requirements-linux.txt` |
| MySQL / Redis / Nginx | 8.0.46 / 7.2.15-alpine / 1.28.3-alpine | `docker-compose.yml` |
| Android 앱 | 1.0.3, versionCode 4, `com.tmtn.app` | `android/app/build.gradle.kts` |
| JDK | 17 | Android 빌드·CI |
| AGP / Gradle / Kotlin | 8.7.3 / 9.5.0 / 2.0.21 | 버전 카탈로그·wrapper |
| compileSdk / targetSdk / minSdk | 35 / 35 / 29 | Android 앱 빌드 설정 |
| Compose BOM | 2024.12.01 | `android/gradle/libs.versions.toml` |
| Room / WorkManager | 2.6.1 / 2.10.0 | Android 버전 카탈로그 |
| Retrofit / OkHttp | 2.11.0 / 4.12.0 | Android 앱 빌드 설정 |

API와 XAI는 **서로 다른 Python 환경**을 씁니다. API의 uv 환경에 XAI requirements를 덧씌우지 않습니다. 새 라이브러리·도구 버전은 별도 검토하며, 앱에 아직 연결하지 않은 Firebase·Health Connect 등을 설치된 기능으로 취급하지 않습니다.

## 로컬 API 시작

저장소 루트에서 실행합니다. uv 0.12.5를 준비하고 실제 모델이 필요하지 않은 API 개발은 `app`, `dev` 그룹만 설치합니다.

```bash
uv python install 3.13.15
uv sync --frozen --group app --group dev
uv run python --version
```

새 작업 폴더에서 `.env`가 없을 때만 예시를 복사합니다.

```bash
cp -n envs/example.local.env .env
```

PowerShell:

```powershell
if (-not (Test-Path .env)) { Copy-Item envs/example.local.env .env }
```

예시 파일은 로컬 출발점입니다. 실제 비밀값을 커밋하지 않고 아래 항목을 확인합니다.

| 설정 | 확인할 내용 |
| --- | --- |
| `ENV` | 로컬 개발은 `local`. 내부 EC2와 공개 운영은 [배포 문서](DEPLOYMENT.md) 참조 |
| `SECRET_KEY` | 환경별 비밀값. 예시 값을 공개 서버에서 재사용하지 않음 |
| `DB_HOST` | 호스트에서 API 실행 시 `127.0.0.1`, Compose 내부 API는 `mysql` 또는 실제 DB 주소 |
| `DB_PORT` / `DB_EXPOSE_PORT` | 컨테이너 내부 포트와 호스트 포트 구분. 호스트 API는 노출 포트에 연결 |
| `DB_USER`, `DB_PASSWORD`, `DB_NAME` | 개발 DB 계정·DB. 기존 서버 값 임의 변경 금지 |
| `COOKIE_DOMAIN` | 로컬에서는 빈 값으로 host-only 쿠키 사용 |
| SMTP / Google OAuth | 실제 이메일·Google 인증이 필요할 때 팀 설정 사용 |
| 모델 URL | 서버 연결을 준비하기 전에는 결과가 준비되지 않은 상태가 정상 |

```bash
docker compose up -d mysql redis
docker compose ps mysql
# MySQL 준비 후 개발 DB 마이그레이션
uv run aerich upgrade
uv run uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

API 문서: `http://127.0.0.1:8000/api/docs`. 모델 연결은 [XAI 안내](XAI.md)와 [배포 문서](DEPLOYMENT.md)를 사용합니다. 새 빈 DB의 카드 카탈로그 적재는 [콘텐츠 안내](../app/scripts/data/README.md)를 따릅니다.

## 검사 실행

### API·정책

```bash
uv run ruff check .
uv run ruff format . --check
uv run coverage run -m pytest app
uv run coverage report -m
uv run python -m pytest tests/first_repair tests/test_journal_xai_integration.py tests/test_merge_regressions.py tests_model_review -q
uv run python tools/check_xai_assets.py
```

`pytest app`은 MySQL을 사용하며 테스트 전용 `test` DB를 생성·삭제합니다. **운영 DB 계정으로 실행하지 않습니다.** 새 로컬 MySQL 볼륨은 `infra/mysql/init/01-grant-test-database.sh`가 개발 계정에 `test` DB 권한을 부여합니다. 기존 볼륨에는 초기화 스크립트가 재실행되지 않으므로 DB 담당자가 개발 계정의 해당 권한을 확인해야 합니다. 이를 위해 기존 볼륨을 삭제하지 않습니다.

AI 의존성 환경 검사:

```bash
uv sync --frozen --group app --group ai --group dev
uv run --frozen --group app --group ai python -m pytest tests/ai_environment -q
```

위 검사는 패키지 호환성 검사입니다. 실제 XAI 추론은 별도 Python 3.14.7 런타임의 검증기를 사용합니다. 동결 모델 묶음의 내부 테스트를 API Python에 함께 수집하지 않습니다.

### Android

`android/`에서 실행합니다.

```bash
./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug --no-configuration-cache --console=plain
```

PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다. 연결 기기 UI 검사는 `connectedDebugAndroidTest`이며 기존 앱과 서명이 다른 APK를 설치하려고 데이터를 지우지 않습니다. 별도 검사용 앱은 [Android 안내](../android/README.md)를 따릅니다.

### 알려진 검사 한계

Mypy는 `uv run mypy app ai_worker model_service`로 별도 확인합니다. 2026-09-21 통합 검사에는 기존 오류 104개가 남아 있으며 현재 CI의 통과 항목과 다릅니다. 실제 실행한 검사·제약은 [통합 검증 기록](INTEGRATION_2026-09-21.md)에 있습니다.

## 버전 변경

`uv.lock`, Gradle wrapper·카탈로그, Docker 이미지와 CI 설정을 함께 검토합니다. 설치만 할 때는 `--frozen`을 쓰고, 도구 버전 변경과 기능 수정을 섞지 않습니다. `.env`, `local.properties`, 서명키, 가상환경, APK와 빌드 산출물은 커밋하지 않습니다.
