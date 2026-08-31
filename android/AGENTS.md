# AGENTS.md — `android/` 안드로이드 앱

> 저장소 루트의 `AGENTS.md` 가 먼저입니다. 이 파일은 **안드로이드 작업에만** 더해지는 규칙입니다.
> Android Studio 는 반드시 **`android/` 폴더를 엽니다.** 저장소 루트를 열면 Python `app/` 과 모듈명이 충돌합니다.

## 기술

Kotlin · Jetpack Compose · Material3 · 패키지 `kr.tmtn.app` · 단일 `:app` 모듈

- Retrofit/Ktor 없음 · Hilt/Koin 없음(수동 `AppContainer`) · Room/DataStore 없음(SharedPreferences)
- 다크모드 없음 (`DESIGN.md` 에 정의가 없어서 의도적으로 뺐습니다)
- 디자인 원본: Figma `ByGT2uoinUBxAQOBqAK7sM` · 화면 목록 `docs/design-v5/SCREENS.csv` (108화면)

## 빌드

```bash
cd android
./gradlew assembleDebug     # → app/build/outputs/apk/debug/app-debug.apk
```

## 버전 — `docs/DEVELOPMENT_ENVIRONMENT.md` 「Android 기준선」이 단일 기준

`android/gradle/libs.versions.toml` 한 곳에만 버전을 적고, **그 문서와 반드시 일치시킵니다.**
여기만 고치고 문서를 안 고치면 다음 사람이 다른 버전을 씁니다.

| 항목 | 값 |
| --- | --- |
| Android Studio | `Quail 2 \| 2026.1.2` stable |
| JDK / bytecode target | `17` |
| AGP / Gradle wrapper / Kotlin | `9.1.1` / `9.3.1` / `2.4.10` |
| SDK Build Tools | `37.0.0` |
| **`compileSdk`** | **`37`** |
| `targetSdk` / `minSdk` | `36` / `28` |
| Compose BOM | `2026.08.00` |
| Navigation / Lifecycle | `2.9.8` / `2.11.0` |

> **`compileSdk 37` 과 `targetSdk 36` 이 다른 게 정상입니다.** 오타로 보고 맞추지 마세요.
> `compileSdk` 는 *어떤 API 로 컴파일하나*, `targetSdk` 는 *어떤 런타임 동작에 동의하나*, `minSdk` 는 *어떤 기기에 설치되나* 입니다. 셋은 따로 올립니다.
> Compose BOM `2026.08.00` 이 끌어오는 Compose `1.12.0` 이 컴파일 시 SDK 37 을 요구해 `compileSdk` 만 37 로 올렸고(2026-08-27), `targetSdk` 는 Google Play 신규 앱 기준을 맞추려고 36 으로 둡니다.

**아직 프로젝트에 넣지 않은 라이브러리**(넣게 되면 이 버전으로 고정):
Firebase Android BOM `34.16.0` · Health Connect `1.1.0` · DataStore `1.2.1` · Room `2.8.4` · WorkManager `2.11.2`

`coreKtx = 1.18.0` 과 `activityCompose = 1.12.0` 은 **팀 문서에 고정값이 없는 잠정값**입니다. Gradle sync 가 "Failed to resolve" 라고 하면 Android Studio 가 제안하는 최신 stable 로 바꾸고, **그 값을 `DEVELOPMENT_ENVIRONMENT.md` 에도 적으세요.**

네트워크 클라이언트(Retrofit/Ktor)와 DI(Hilt/Koin)는 **아직 결정이 없습니다.** ADR 로 하나를 고르기 전에 개인 취향으로 추가하지 마세요.

## 커밋할 것 / 안 할 것

**커밋합니다** — `gradlew` · `gradlew.bat` · `gradle/wrapper/gradle-wrapper.properties` · 모든 `build.gradle.kts` · `settings.gradle.kts` · `gradle/libs.versions.toml` · 비밀 없는 `gradle.properties` · `AndroidManifest.xml` · ProGuard/R8 공통 규칙

**커밋하지 않습니다** — `local.properties` · `.gradle/` · 모든 `build/` · `google-services.json` · FCM service account key · `.jks` / `.keystore` / signing password · APK · AAB · APKS

---

# 하지 말 것

## 디자인 토큰 — 어기면 화면이 다시 시끄러워집니다

- **하드코딩 색을 쓰지 마세요.** `Color(0xFF...)` 를 화면 코드에 쓰면 안 됩니다. 반드시 `TmtnColor.*` 를 거칩니다. 현재 하드코딩 색은 **0개**입니다. 이 숫자를 지키세요.
- **하드코딩 글자 크기를 쓰지 마세요.** `TmtnText.*` 만 씁니다. 타입은 **7단계뿐**입니다 — Display 40 / Headline 32 / Title 24 / BodyLarge 19 / Body 16 / Label 14 Bold / Caption 14 Medium.
- **주황 `#FF7A1A` 을 "오늘" 이외에 쓰지 마세요.** 완료·실천·주 버튼은 전부 먹색 `#16181C` 입니다. (주황이 많아 눈이 아프다는 피드백으로 정리한 규칙입니다.)
- **폐기된 초록 팔레트 `#0C3B2E` `#6D9773` 를 쓰지 마세요.** 코드에 남아 있으면 지웁니다.
- **`Display` 를 한 화면에 두 번 쓰지 마세요.**
- **글자를 다시 줄이지 마세요.** v5에서 의도적으로 키웠습니다. 넘치면 여백을 줄이세요.
- **누를 수 있는 것을 48dp 미만으로 만들지 마세요.** `TmtnTarget` 을 씁니다.

토큰 파일: `designsystem/TmtnColor.kt` · `TmtnType.kt` · `TmtnDimens.kt`
간격·모서리는 `TmtnSpace` / `TmtnRadius`.

## 기록 달력 — 색만으로 구분하지 마세요

| 상태 | 표시 |
| --- | --- |
| 실천 | 먹색 꽉 찬 원 + 흰 숫자 |
| 쉼 | 먹색 **실선** 테두리 1.5dp + 바탕 `#EFEAE0` |
| 미완료 | 회색 `#B8B2A6` **점선** 테두리 1.5dp (`dash 2.5 / gap 2.5`) |
| 오늘 | 주황 실선 테두리 2dp |

**실선 = 쉼(내가 고른 것), 점선 = 미완료(빠진 것).** 색만이 아니라 **선 종류로** 구분합니다. 이 대비를 없애지 마세요. 색약 사용자에게 이게 유일한 단서입니다.

주간·월간 달력 모두 **월요일 시작**입니다.

## 데모 표시

코드에 마커가 두 종류 있습니다. **섞지 마세요.**

```bash
grep -rn "\[DEMO" android/app/src/main/java/
```

- **`[DEMO]`** — 서버가 붙으면 **사실과 달라지는** 문장입니다. 연동 즉시 **지워야** 합니다.
  (`ui/auth/LoginScreen.kt` 의 "서버 없이 이 기기 안에서만", `ui/tabs/TabScreens.kt` 의 "데모 데이터 초기화")
- **`[DEMO-MODEL]`** — 모델이 임시 구현이라 뜨는 안내입니다. **코드를 건드리지 마세요.** `domain/ml/ModelSlot.kt` 의 `isPlaceholder` 를 `false` 로 두면 저절로 사라집니다.

**틈튼지수 탭의 "비진단용 참고 정보입니다" 문구는 데모 표시가 아닙니다. 절대 지우지 마세요.**

## 그 밖

- **화면을 새로 만들기 전에 Figma 를 열지 않고 시작하지 마세요.** `docs/design-v5/SCREENS.csv` 에 화면별 링크가 있습니다.
- **일러스트를 기다리며 화면을 비워 두지 마세요.** `TmtnComponents.kt` 의 `ImageSlot(tag, title, desc, height)` 으로 자리를 잡고 파일 이름만 미리 정합니다.
- **`missions.json` 을 직접 고치지 마세요.** CSV 를 고치고 `python android/tools/build_missions_json.py <csv>` 를 다시 돌립니다.
- **`alias(libs.plugins.kotlin.android)` 를 되살리지 마세요.** AGP 9.0 부터 Kotlin 이 내장이라 빌드가 깨집니다. 단 **`org.jetbrains.kotlin.plugin.compose` 는 계속 필요합니다** — 지우면 안 됩니다.
- **최상위 `kotlin { compilerOptions { jvmTarget ... } }` 블록을 넣지 마세요.** `android.compileOptions.targetCompatibility`(17)를 자동으로 따라갑니다.
- **`kotlin-kapt` 를 쓰지 마세요.** AGP 9 비호환입니다. 필요하면 KSP 를 씁니다.

---

# 할 것

## 화면 추가

`ui/nav/Routes.kt` 와 `TmtnApp.kt` **두 곳만** 고치면 됩니다.

## 모델 교체

`domain/ml/ModelRegistry.kt` **한 파일**에서 합니다. 화면 코드는 한 줄도 안 고쳐도 됩니다.

| 모델 | 인터페이스 | 지금 |
| --- | --- | --- |
| 허리둘레 | `WaistEstimator.estimate(WaistInput)` | `PlaceholderWaistEstimator` |
| 행동 인식 | `ActivityRecognizer.measure(MeasureRequest)` | `SensorActivityRecognizer` / 센서 없으면 `SimulatedActivityRecognizer` |
| 틈튼지수 | `TmtnIndexScorer.score(TmtnIndexInput)` | `PlaceholderTmtnIndexScorer` |

세 슬롯은 **각각 따로** 내릴 수 있습니다. 서버 구현으로 바꿀 때는 **서버가 죽었을 때 임시 구현으로 떨어지는 폴백을 반드시 같이 넣으세요.** 실배포에서 서버 장애는 반드시 일어납니다.

`.tflite` 는 `android/app/src/main/assets/models/` 에 둡니다.

## 미션 실행이 두 갈래인 이유

`MissionType.isModelMeasured` 가 분기 기준입니다.

- **자가 수행형** → `SelfMissionScreen`. 앱이 무조건 시간을 셉니다 → **일시정지가 필요**하고, 완료는 사용자가 눌러야 기록됩니다.
- **모델 측정형** → `MissionIntroScreen`(무엇을 읽고 안 읽는지 고지) → `ModelMissionScreen`. 모델이 `moving=false` 인 동안 `activeSeconds` 를 안 올리므로 **일시정지가 불필요**하고, 목표 도달 시 자동 기록됩니다.
- 센서 불가 기기 → `MissionRunViewModel(card, forceManual=true)`

## 화면 높이 예산

390×844 기준 상태바 44 + 앱바 64 + 하단탭 104 를 빼면 **본문 632dp** 입니다. 스크롤 없는 화면은 이 안에 들어와야 합니다.

## 하단 탭

**홈 · 기록 · 틈튼지수 · 댐 · 내 정보** 5개. 틈튼지수가 이 앱의 핵심이라 자기 탭을 가집니다(`E01`). 카드첩은 댐 탭 안(`G06`)입니다.

---

# 확인 방법

빌드가 통과한다고 화면이 맞는 건 아닙니다. **글자가 커져 넘치는 것은 빌드로 안 잡힙니다.** 화면을 고쳤으면 에뮬레이터나 실기기로 직접 보세요.

"완료"라고 적을 때는 무엇으로 확인했는지 함께 적습니다 — 컴파일 통과인지, 실기기 확인인지.
