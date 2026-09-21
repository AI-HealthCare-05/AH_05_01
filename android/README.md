# 틈튼 · Android

Android Studio에서 저장소 루트 대신 **`android/` 폴더**를 엽니다. 실제 API에 연결되는 Kotlin·Jetpack Compose 앱이며 패키지는 `com.tmtn.app`, 버전은 **1.0.3 / 4**입니다. 화면 흐름은 [현재 앱 안내](../docs/APP_GUIDE.md), 공통 설치는 [개발 환경](../docs/DEVELOPMENT_ENVIRONMENT.md)을 확인합니다.

## APK 빌드

JDK 17과 Android SDK 35를 설치하고 `android/`에서 실행합니다. Gradle wrapper가 지정한 9.5.0을 사용하며 시스템 Gradle로 대체하지 않습니다.

```bash
./gradlew assembleDebug
```

PowerShell: `.\gradlew.bat assembleDebug`. 결과는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 기존 앱을 업데이트하려면 같은 서명키가 필요합니다. 서명 불일치를 해결하려고 앱 데이터를 삭제하지 않습니다.

`local.properties`에는 로컬 SDK 위치를 설정합니다. Google 로그인을 쓴다면 `GOOGLE_WEB_CLIENT_ID`가 서버의 `GOOGLE_CLIENT_ID`와 같아야 합니다. 값이 없으면 Google 인증을 사용할 수 없습니다. `TEST_ACCOUNT_EMAIL`·`TEST_ACCOUNT_PASSWORD`는 선택적인 개발 편의 설정이며 배포 APK에 넣지 않습니다. 이 파일과 서명키는 커밋하지 않습니다.

현재 API 주소는 [ApiClient.kt](app/src/main/java/com/tmtn/app/network/ApiClient.kt)의 `BASE_URL`입니다. 서버 주소를 바꿀 때 API 경로 `/api/v1/`와 통신 설정을 함께 확인하고 다시 빌드합니다. 이 주소는 빌드 시 자동으로 EC2 설정을 읽는 값이 아닙니다.

## 현재 코드 위치

아래 경로는 `app/src/main/java/com/tmtn/app/` 기준입니다.

| 경로 | 역할 |
| --- | --- |
| `MainActivity.kt` | 최상위 상태·인증·온보딩·탭 연결 |
| `ui/cardhome/` | 시안 C 홈, 실제 카드, 완료 응원, 틈새운동, 센서 진행 |
| `ui/onboarding/` | 정보 입력과 첫 재료 온보딩 |
| `ui/journal/` | 월요일 기준 일보, 개인 XAI, 주간 이력 |
| `ui/reference/` | 참고정보와 허리둘레 상태·설명 |
| `ui/theme/` | 색·Pretendard·모션 토큰 |
| `network/` | Retrofit·OkHttp API와 응답 모델 |
| `sensor/` | 움직임 인식·권한·측정 서비스·저장 재시도 |
| `data/local/` | Room 기반 로컬 기록 |

하단 탭은 **홈 · 기록 · 일보 · 댐 · 내 정보**입니다. 카드·보상·기록은 서버 상태를 사용합니다. 과거의 오프라인 앱, `kr.tmtn.app`, `ModelRegistry.kt` 기반 안내를 현재 구조에 적용하지 않습니다.

## 콘텐츠와 디자인

- [디자인 기준](DESIGN.md)을 따릅니다. 홈의 역할별 색은 `ui/theme/TmtnHomeColor.kt`, 공통 색·강도는 `TmtnColor.kt`, 글자는 `TmtnType.kt`에 있습니다.
- 홈과 카드 상세는 `ui/cardhome/TmtnMissionCard.kt`의 현재 카드 표현을 사용합니다. 폐기된 장식 프레임이나 전달본 HTML을 제품 화면에 다시 넣지 않습니다.
- 서버 카드 문구·목표·행동은 [콘텐츠 적재 안내](../app/scripts/data/README.md)를 따릅니다. 현재 저장소에 없는 옛 `build_missions_json.py` 명령을 사용하지 않습니다.
- 운동 캐릭터는 응원·일시정지·목표 달성 정지 이미지입니다. 폐기된 걷기 GIF·분리된 팔다리 모션을 복원하지 않습니다.
- 개인 XAI는 서버가 계산합니다. [XAI 표시 계약](../docs/XAI.md)의 동의·입력·실패 상태와 실제 저장된 주간 이력만 표시합니다. 공개 운영 승인이 없는 결과를 release 빌드에서 임의로 켜지 않습니다.

## 검사와 검사용 앱

```bash
./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug --no-configuration-cache --console=plain
```

기기 UI 검사는 `connectedDebugAndroidTest`입니다. 제품 앱과 별개 패키지로 검사해야 할 때만 `challenge-preview.init.gradle`을 지정합니다.

```bash
./gradlew --init-script challenge-preview.init.gradle assembleDebug connectedDebugAndroidTest --no-configuration-cache --console=plain
```

이 설정은 `.challengeqa` 패키지와 검토 진입점을 사용합니다. 화면 검토 진입점의 예시 데이터와 일반 로그인 후 서버 흐름을 구분해야 합니다. **서버 담당자에게 전달할 일반 APK에는 이 init 스크립트를 적용하지 않습니다.**

통합 당시 실제 기기 검사 범위와 기존 lint·타입 오류는 [검증 기록](../docs/INTEGRATION_2026-09-21.md)에 있습니다. 빌드 통과와 실제 서버 배포 완료를 같은 의미로 쓰지 않습니다.
