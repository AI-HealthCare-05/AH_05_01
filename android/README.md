# 틈튼(TMTN) Android

Jetpack Compose 앱. **Android Studio 로 이 `android/` 폴더를 연다.** 저장소 루트를 열면 안 된다.
(루트에는 Python `app/` 이 있어서 Android `app` 모듈과 이름이 겹친다.)

> **처음 넘겨받았다면 [`docs/ANDROID_인수인계.md`](../docs/ANDROID_인수인계.md) 부터 읽으세요.**
> 어디까지 됐고 다음에 뭘 해야 하는지, 코드만 봐서는 안 보이는 함정이 정리돼 있습니다.

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

## 디자인 시스템 (v5, 2026-08-30)

토큰은 `app/src/main/java/kr/tmtn/app/designsystem/` 세 파일에만 있다.
`docs/design-v5/designsystem/` 의 핸드오프 원본과 바이트 단위로 같다.

| 파일 | 무엇 |
|---|---|
| `TmtnColor.kt` | 순백 바탕 + 먹색. 재료 5색·`Shadow`·`OnSurfaceFaint` 포함 |
| `TmtnType.kt` | 7단계 (Display 40 / Headline 32 / Title 24 / BodyLarge 19 / Body 16 / Label·Caption 14) |
| `TmtnDimens.kt` | `TmtnSpace` `TmtnRadius` `TmtnTarget` `TmtnCalendar` `TmtnLayout` |

깨면 안 되는 것 세 가지:

- 색은 `TmtnColor.*`, 글자는 `TmtnText.*` 로만. **하드코딩 금지.**
- **주황 `#FF7A1A` 은 "오늘"에만.** 완료·실천·주 버튼은 먹색 `#16181C` 다.
- **Display 는 화면당 하나만.** 글자 크기는 만성질환 사용자 기준이라 임의로 줄이지 않는다.

자세한 규칙은 저장소 루트 [`CLAUDE.md`](../CLAUDE.md) 에 있다.

## 모델 3개를 넣는 곳

`app/src/main/java/kr/tmtn/app/domain/ml/` 하나만 보면 된다.
자세한 건 [`docs/TMTN_ANDROID_GUIDE.md`](../docs/TMTN_ANDROID_GUIDE.md).

## 카드 문구를 고칠 때

앱 코드를 고치지 않는다. CSV 를 고치고 다시 만든다.

```bash
python android/tools/build_missions_json.py TMTN_오늘의운세_200카드.csv
```
