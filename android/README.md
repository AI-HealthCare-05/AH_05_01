# 틈튼(TMTN) Android

Jetpack Compose 앱. **Android Studio 로 이 `android/` 폴더를 연다.** 저장소 루트를 열면 안 된다.
(루트에는 Python `app/` 이 있어서 Android `app` 모듈과 이름이 겹친다.)

## 5분 만에 APK 만들기

1. Android Studio → **Open** → 이 `android` 폴더 선택
2. 오른쪽 아래에 뜨는 Gradle sync 를 기다린다 (첫 실행은 Gradle 9.3.1 을 받느라 몇 분 걸린다)
3. 상단 메뉴 **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
4. 다 되면 나오는 알림의 **locate** 클릭 →
   `android/app/build/outputs/apk/debug/app-debug.apk`
5. 이 파일을 폰에 옮겨 설치 (설정에서 "출처를 알 수 없는 앱" 허용 필요)

터미널파: `cd android && ./gradlew assembleDebug` (Windows PowerShell 은 `.\gradlew.bat assembleDebug`)

## 지금 동작하는 것

로그인 → 온보딩 → 홈 → 오늘의 카드 3장 → 확정 → 미션 실행 → 완료 → 기록·댐·참고·마이

서버 없이 이 기기 안에서만 돈다. 카드 200장은 `app/src/main/assets/missions.json` 에서 읽는다.

## 미션 화면이 두 개인 이유

| | 자가 수행형 | 모델 측정형 |
|---|---|---|
| 유형 | `SELF_CHECK` `SELF_TIMER` | `MODEL_ACTIVE_TIME` `MODEL_DISTANCE` `MODEL_STAIR_COUNT` |
| 장수 | 167장 | 33장 |
| 화면 | `SelfMissionScreen` | `MissionIntroScreen` → `ModelMissionScreen` |
| 시간을 세는 주체 | 앱이 무조건 센다 | 모델이 "움직였다" 고 한 동안만 센다 |
| 일시정지 | **필요하다** (안 누르면 계속 흐른다) | 없어도 된다 (멈추면 자동으로 안 센다) |
| 완료 | 사용자가 눌러야 기록 | 목표에 닿으면 자동 기록 |
| 추가로 보여 주는 것 | — | 전체 경과 · 자동으로 쉰 시간 · 제외된 구간 · 측정 품질 |

센서를 못 쓰는 기기에서는 모델형 카드도 "직접 체크로 진행하기" 로 내려간다.

## 모델 3개를 넣는 곳

`app/src/main/java/kr/tmtn/app/domain/ml/` 하나만 보면 된다.
자세한 건 [`docs/TMTN_ANDROID_GUIDE.md`](../docs/TMTN_ANDROID_GUIDE.md).

## 카드 문구를 고칠 때

앱 코드를 고치지 않는다. CSV 를 고치고 다시 만든다.

```bash
python android/tools/build_missions_json.py TMTN_오늘의운세_200카드.csv
```
