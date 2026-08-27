# 틈튼 Android — 모델 넣기 · APK 배포

이 문서는 두 가지만 다룬다. **모델 3개를 어디에 끼우는가**, 그리고 **APK 를 어떻게 뽑는가**.
개발 환경 버전은 [공동 개발 환경·버전 기준](DEVELOPMENT_ENVIRONMENT.md)이 정본이다.

---

## 1. 모델 3개 자리

앱은 모델의 **구현을 모른다.** 인터페이스만 안다.
그래서 모델이 없어도 앱은 지금 그대로 돌아가고, 모델이 생기면 화면을 하나도 안 고쳐도 된다.

```
android/app/src/main/java/kr/tmtn/app/domain/ml/
├── ModelSlot.kt                    ModelInfo · ModelResult (공통 타입)
├── WaistEstimator.kt          ①   허리둘레 추정
├── ActivityRecognizer.kt      ②   센서 기반 행동 인식
├── TmtnIndexScorer.kt         ③   틈튼지수
├── ModelRegistry.kt           ★   ← 갈아끼우는 곳은 여기 한 곳뿐
└── placeholder/                    지금 붙어 있는 임시 구현들
```

### 넣는 순서 (세 모델 모두 동일)

1. 인터페이스를 구현한 클래스를 만든다.
2. `.tflite` / `.onnx` 파일은 `android/app/src/main/assets/models/` 에 둔다.
3. 필요한 라이브러리를 `android/gradle/libs.versions.toml` 에 추가한다.
4. `ModelRegistry.init()` 안에서 placeholder 대신 새 클래스를 넣는다.
5. `info.isPlaceholder = false` 로 둔다. 그래야 화면의 "샘플 값" 안내가 사라진다.

화면 코드는 **한 줄도 고치지 않는다.**

### ① 허리둘레 추정 — `WaistEstimator`

```kotlin
suspend fun estimate(input: WaistInput): ModelResult<WaistEstimate>
```

- 입력: 출생연도 · 성별(선택) · 키 · 몸무게 (`WaistInput.bmi` 로 BMI 도 바로 나온다)
- 출력: `waistCm` 과 신뢰구간 `lowCm`~`highCm`
- **사용자가 직접 잰 값이 있으면 모델을 부르지 않는다.** 덮어쓰지 말 것.
- 이 값은 ③ 틈튼지수의 입력으로 그대로 들어가고, 그때 `waistFromModel = true` 로 표시된다.

### ② 행동 인식 — `ActivityRecognizer`

```kotlin
fun availability(): RecognizerAvailability
fun measure(request: MeasureRequest, resumeFrom: ActivitySnapshot): Flow<ActivitySnapshot>
```

- 1초에 한 번쯤 `ActivitySnapshot` 을 흘려보내면 된다. 화면은 그 값만 그린다.
- 반드시 채워야 하는 값: `activeSeconds` `elapsedSeconds` `restSeconds` `moving`
  (거리 미션이면 `distanceMeters`, 계단 미션이면 `stairs`)
- `moving = false` 인 동안 `activeSeconds` 를 올리지 않는 것이 이 모델의 핵심이다.
  이것 때문에 사용자가 일시정지를 누를 필요가 없다.
- 목표에 닿으면(`snapshot.reachedGoal(request)`) 화면이 알아서 완료 처리한다.
- 쓸 수 없는 기기에서는 `availability()` 가 `Blocked(reasons)` 를 돌려주면 된다.
  화면이 이유 목록을 그대로 보여 주고 "직접 체크" 로 안내한다.

지금 붙어 있는 것: `SensorActivityRecognizer` (걸음 센서 + 기압계를 규칙으로 읽음).
센서가 아예 없는 기기·에뮬레이터에서는 `SimulatedActivityRecognizer` 로 자동 전환된다.

### ③ 틈튼지수 — `TmtnIndexScorer`

```kotlin
suspend fun score(input: TmtnIndexInput): ModelResult<TmtnIndexResult>
```

- 입력: 나이대 · 성별 · 키 · 몸무게 · 허리둘레(모델 ① 값 가능) · 주간 유산소 · 주간 근력 · 최근 7일 활동
- 출력: `score`(0~100) · `band` · `factors`(기여도) · `disclaimer`
- `factors` 를 채워 주면 참고 탭의 "무엇이 점수에 영향을 줬나요" 가 자동으로 채워진다. (SHAP 등)

**반드시 지킬 것**

- 진단이 아니다. `disclaimer` 를 비우지 말 것.
- 점수 하락을 "위험이 낮아졌다" · "건강해졌다" 로 쓰지 않는다. 입력값이 바뀌어 재계산된 것뿐이다.
- 챌린지 완료만으로 점수를 움직이지 않는다. 건강 입력값이 바뀔 때만 재계산한다.
  (`TodayViewModel.recompute()` 는 프로필이 바뀔 때만 호출된다 — 이 규칙을 깨지 말 것)
- 오행 기운·챌린지 이행률과 절대 합산하지 않는다.

### 지금 붙어 있는 임시 구현의 정직한 상태

| 모델 | 지금 | 화면 표시 |
|---|---|---|
| ① 허리둘레 | BMI·나이 선형식 (근거 없음) | "샘플 값이에요" |
| ② 행동 인식 | 걸음 센서 증가분 규칙 | "샘플 측정 중이에요" |
| ③ 틈튼지수 | 규칙 점수 | "샘플 값이에요" |

세 개 모두 `isPlaceholder = true` 다. **이 상태로 외부 배포하면 안 된다.**

---

## 2. APK 뽑기

### 지금 당장 (팀 공유용 debug APK)

```bash
cd android
./gradlew assembleDebug          # Windows: .\gradlew.bat assembleDebug
```

결과: `android/app/build/outputs/apk/debug/app-debug.apk`

Android Studio 에서는 **Build → Build APK(s)** 와 같다.
debug APK 는 서명 걱정 없이 바로 폰에 설치된다. QA·발표 시연은 이걸로 충분하다.

폰에 넣는 법 세 가지 중 아무거나.

- USB 연결 후 `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- APK 파일을 카톡·드라이브로 보내서 폰에서 직접 설치 (설정에서 "출처를 알 수 없는 앱" 허용)
- Android Studio 에서 폰을 연결하고 ▶ Run

### 나중에 (스토어 배포용 서명 AAB)

1. Android Studio → **Build → Generate Signed App Bundle / APK**
2. keystore 를 새로 만든다. **이 파일과 비밀번호를 잃어버리면 앱을 영원히 업데이트할 수 없다.**
3. keystore 는 저장소에 올리지 않는다. (`.gitignore` 에 `*.jks` `*.keystore` 이미 있다)
4. CI 에서 서명하려면 GitHub Secrets 에 base64 로 넣고 workflow 에서 복원한다.
5. `versionCode` 를 올린다. 한 번 올린 값은 재사용할 수 없다.

### 배포 전 점검

- [ ] `./gradlew lint testDebugUnitTest assembleDebug` 가 통과한다
- [ ] `versionCode` 를 올렸다
- [ ] 모델 3개가 placeholder 가 아니다 (`ModelRegistry.anyPlaceholder()` 가 false)
- [ ] 참고 탭의 비진단 고지가 보인다
- [ ] APK · keystore 가 git 에 안 들어갔다

---

## 3. 빌드가 안 될 때

| 증상 | 원인 | 할 일 |
|---|---|---|
| `requires ... compile against version 37 or later` (13건) | Compose BOM 2026.08.00 이 SDK 37 을 요구 | 2026-08-27 수정 완료. `compileSdk = 37`. **`targetSdk` 는 36 그대로 둔다** — 셋은 따로 올린다 |
| `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0` | **AGP 9 부터 Kotlin 이 내장됨** | 2026-08-27 수정 완료. `build.gradle.kts` 두 곳에서 `kotlin.android` 를 뺐다. **다시 넣지 말 것.** Compose 컴파일러 플러그인(`kotlin.plugin.compose`)은 내장이 아니라 계속 필요하다 |
| `Cannot add extension with name 'kotlin'` | 위와 같은 원인 | `kotlin.android` 플러그인이 어딘가 남아 있다 |
| `kotlin-kapt` 관련 오류 | AGP 9 비호환 | `com.android.legacy-kapt` 로 바꾸거나 KSP 로 이전. 지금 프로젝트는 kapt 를 안 쓴다 |
| `Failed to resolve: androidx.core:core-ktx:1.18.0` | 잠정 버전을 넣어 둔 것 | `android/gradle/libs.versions.toml` 에서 Android Studio 가 제안하는 stable 로 바꾸고, `DEVELOPMENT_ENVIRONMENT.md` 에도 같이 적는다 |
| `Failed to resolve: androidx.activity:activity-compose:1.12.0` | 위와 같음 | 위와 같음 |
| `Unsupported class file major version` | JDK 가 17이 아님 | Settings → Build Tools → Gradle → Gradle JDK 를 17 로 |
| `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` | wrapper jar 손상 | Android Studio 가 다시 만들어 준다. 또는 `gradle wrapper --gradle-version 9.3.1` |
| sync 는 되는데 `app` 모듈이 안 보임 | 저장소 루트를 열었다 | `android/` 폴더를 열 것 |
| 첫 sync 가 아주 느림 | Gradle 9.3.1 배포판을 받는 중 | 기다린다. 한 번만 받는다 |

`libs.versions.toml` 의 두 버전은 팀 문서에 고정값이 없어 잠정으로 넣었다.
확정되면 문서와 카탈로그를 **같은 PR 에서** 고친다.
