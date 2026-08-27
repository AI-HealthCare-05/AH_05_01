# ADR 0001 — Android 프로젝트를 `android/` 하위 독립 Gradle 프로젝트로 둔다

- 상태: **제안** (팀 확인 필요)
- 날짜: 2026-08-27
- 관련: README "저장소 구조", `docs/TMTN_READINESS_AUDIT.md`

## 배경

README 가 지적한 대로 Python 백엔드의 `app/` 과 Android 관례상의 `app` 모듈은 이름이 겹친다.
README 는 `backend/` `android/` `contracts/` `docs/` 형태의 전면 모노레포 재구성을 권장했다.

## 결정

전면 재구성을 **지금은 하지 않는다.** 대신 저장소 루트에 `android/` 를 추가하고,
그 안에 `settings.gradle.kts` 를 두어 **독립된 Gradle 프로젝트**로 만든다.
Android 모듈 경로는 `:app` 이지만 실제 디렉터리는 `android/app/` 이라 루트 `app/` 과 충돌하지 않는다.

## 이유

- 전면 재구성은 진행 중인 모든 PR 을 깨뜨리고 백엔드 import 경로를 전부 바꾼다.
  5주 일정에서 감당할 비용이 아니다.
- Android Studio 는 `android/` 폴더를 열면 되고, PyCharm 은 루트를 열면 된다. 서로 간섭하지 않는다.
- 나중에 전면 모노레포로 갈 때 `android/` 는 그대로 옮기면 된다. 되돌리기 비용이 낮다.

## 결과

- Android Studio 에서 **반드시 `android/` 폴더를 열어야 한다.** 루트를 열면 모듈이 안 잡힌다.
- CI 에 Android job 을 추가할 때 `working-directory: android` 를 지정해야 한다.
- 백엔드·Android 가 공유할 계약(OpenAPI)은 아직 공유 폴더가 없다. 당분간 수기로 맞춘다.

## 함께 미룬 결정 (별도 ADR 필요)

| 항목 | 지금 | 이유 |
|---|---|---|
| 네트워크 클라이언트 (Retrofit / Ktor) | **없음** | 목업 단계라 아직 필요 없다. 섞어 쓰기 전에 ADR 로 하나만 고른다 |
| DI (Hilt / Koin) | 수동 `AppContainer` | 위와 같음. 지금 규모에서는 수동으로 충분하다 |
| 로컬 저장 (Room / DataStore) | SharedPreferences | MVP 범위. 기록이 늘면 Room 으로 옮긴다 |
| 멀티 모듈 (core/domain/data/feature) | 단일 `:app` | 첫 APK 를 빨리 뽑기 위해. 화면이 30개를 넘으면 분리한다 |
