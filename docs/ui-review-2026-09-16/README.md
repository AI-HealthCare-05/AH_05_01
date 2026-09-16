# 9/16 UI·동의·주간면 검토 결과

**후속 수정:** 같은 날 추가한 첫 댐 소개·가입 재개와 단계 정책 확인은 [FIRST-DAM.md](FIRST-DAM.md)를 따른다. 아래 91개 단위·62개 기기 검증은 먼저 만든 UI 리뷰 APK의 결과이며, 후속 APK 검증 수치와 섞지 않는다.

작업 대상: `deliverables/tmtn-github-final-ui-2026-09-15`의 Android 앱. 기준 HEAD `4745398086218ab8f048dc54239b51d9ef7ddb5f`, 브랜치 `codex/final-ui-2026-09-15`.

사용자가 이미 수정한 37개 파일을 보존하고 필요한 부분을 보완했다. 기존 변경은 상위 AH_05_01 프로젝트의 `docs/ui-review-2026-09-16/before-user-changes.patch`에 백업했다. 백엔드·모델·DB·네트워크 DTO·센서 측정 로직·Gradle 의존성은 수정하지 않았다. 이번 수정은 아직 GitHub에 push하지 않았다.

## 결과와 한계

| 검증 | 결과 |
| --- | --- |
| 앱 / androidTest APK 컴파일 | 성공 |
| JVM 단위 테스트 | **91개 통과**, 실패/오류/건너뜀 0 |
| Android Lint | 오류 0, 경고 109, 정보 26. 경고까지 모두 해소한 상태는 아님 |
| Git whitespace 검사 | 통과 |
| 백엔드·API/DTO·센서 디렉터리 diff | 변경 없음 |
| 실제 기기 테스트 | **62개 통과**, 실패 0. SM-S916N / Android 16, 별도 QA 앱에 설치해 실행 |
| 실제 화면·드래그·확대 글자 | 이번 APK의 캡처를 직접 검토. 손잡이 드래그·320dp/1.8배 글자·월요일 주간면·동의 분리 검증. 연속 수동 제스처 체감과 FPS는 미측정 |
| 동의 API 실제 저장·철회, Google OAuth, 실외 센서 | 이번 검증 범위 아님 |
| 약관 본문 | 운영자/이메일 반영 및 항목 분리 완료. 공개용 확정본은 아님 |

기존 9/15 갤러리를 이번 수정본의 검증 결과로 사용하지 않는다. [기기 QA](DEVICE-QA.md), [모션 검토](motion-review.md), [약관 전달본](../legal/README.md).

9/16 재연결 후 QA 앱과 테스트 APK를 설치했다. 기기 글자 배율은 1.0, 굵은 글씨 보정은 +300이며 변경하지 않았다. 일부 회귀 테스트는 앱 내부에서만 보정을 0으로 지정해 기본 서체를 검증한다. 캡처는 실기기의 실제 Compose 화면에 검증용 데이터를 넣은 결과이며 운영 서버 사용자 기록이 아니다. 기존 com.tmtn.app의 로그인·기록은 건드리지 않았다.

## 발견해서 수정한 부분

| 문제 | 수정 | 코드 근거 |
| --- | --- | --- |
| 시트 측정 직후 최종 위치가 한 프레임 보일 수 있음 | 초기 alpha 0, 진입 위치 설정 뒤 노출. 스크림도 같은 진행도 사용 | `common/TmtnSheetDialog.kt:68` |
| 드래그마다 비동기 snap 작업을 만들어 release spring을 끊을 수 있음 | 손가락 이동을 동기 상태로 반영하고 release에서 인계 | `TmtnSheetDialog.kt:123`, `:137`, `:186` |
| 모션 제한 상태에서도 드래그 닫기는 화면 밖으로 점프 | 현재 위치에서 160ms fade 후 닫기 | `TmtnSheetDialog.kt:146` |
| 닫히는 중 저장이 시작돼도 시트가 사라질 수 있음 | 퇴장 완료 시 canDismiss 재확인, 저장 중이면 복구 | `TmtnSheetDialog.kt:125` |
| 여러 약관·개인정보 ‘보기’가 같은 본문으로 연결됨 | 7개 문서 모델·개별 본문·가입/설정 진입 연결 | `legal/LegalDocument.kt:4`, `onboarding/OnboardingScreensTerms.kt:26` |
| 알림 선택과 개인정보 수집을 혼동할 수 있음 | 서비스 알림 선택·Android 권한·개인정보 수집 구분 | `legal/LegalDocument.kt` |
| 일보 주간면이 목~수 등 이동 7일로 표시됨 | 서비스 날짜가 속한 월~일, 미래 날짜 비활성 | `journal/JournalWeek.kt:11`, `JournalScreen.kt:212` |
| 날짜만 바꾸면 횟수·카드·재료에 전주 내용이 남음 | 같은 기간으로 집계, 날짜 상세로 카드 확인, 전주 재료 재사용 금지 | `JournalState.kt:46`, `JournalWeek.kt:27` |
| 홈 틈새 운동에 예전 외곽선 캐릭터 사용 | 현재 수채화 틈튼이 beaver_wave로 교체 | `cardhome/CardHomeScreen.kt:132` |
| 큰 글자에서 CTA와 화살표가 폭을 경쟁함 | 텍스트에 남은 폭 할당 | `CardHomeScreen.kt:128` |
| 동의 설명과 체크박스 텍스트 시작선 불일치 | 체크 영역 48dp, 설명도 48dp 들여쓰기 | `onboarding/OnboardingComponents.kt:443` |
| 카드 안 설정 행이 클릭 여부에 따라 4dp/20dp로 어긋남 | 동의·개인정보·앱 정보 카드 안쪽 20dp | `profile/ProfileScreens1.kt:85`, `ProfileScreens3/6/7.kt` |
| 고정 버전 1.0.0 / 눌러도 동작 없음 | BuildConfig.VERSION_NAME 표시, 클릭·화살표 제거 | `profile/ProfileScreens7.kt:63` |
| 홈 테스트가 제거한 댐/옛 지수 버튼을 기대함 | 홈 댐 없음·현재 일보 진입 검증으로 갱신 | `androidTest/.../HomeHierarchyTest.kt` |

위 소스 경로는 `android/app/src/main/java/com/tmtn/app/ui/` 기준이다. 줄 번호는 이 검토본 기준이다.

## Typography · Spacing · Layout

Pretendard regular/medium/semibold/bold와 기존 색상 체계를 유지했다. Android 글자는 sp, UI 치수는 dp다.

| 역할 | 크기 / 줄 높이 | 굵기 |
| --- | --- | --- |
| 홈·신문 주요 제목 | 28 / 36sp | Bold |
| 일반 큰 제목 | 32 / 42sp | Bold |
| 섹션·약관 소제목 | 20 / 28sp | SemiBold |
| 미션 이름 | 18 / 26sp | SemiBold |
| 카드 메시지 | 24 / 34sp | Medium |
| 본문·약관 설명 | 16 / 26sp | Regular |
| 보조 설명 | 14 / 20sp | Medium |

제목 Heading, 본문 Paragraph, 버튼 Simple 줄바꿈을 유지했다. 한국어 줄바꿈은 OS별 차이가 있으므로 고정 줄 수를 보장하지 않는다. 큰 글자에서 내용을 잘라 한 줄을 강제하지 않는다.

- 화면 좌우 20dp. 약관 섹션 사이 24dp, 소제목과 본문 사이 8dp.
- 동의 체크 영역 48dp, 행 최소 56dp, 보기 최소 터치 높이 48dp. 체크와 보기는 별도 동작.
- 카드 안 설정 행의 좌우 20dp. 다른 역할의 일반 목록까지 일괄 변경하지 않음.
- 기존 버튼 최소 52dp와 카드/필드 모서리 유지.
- 일보 날짜는 월~일. 큰 글자에서는 가로 스크롤로 숫자 겹침 방지.
- 홈의 허리둘레 독립 행, 홈 댐 요약 제거, 일보 배너 계층 유지. 하단 댐 탭 유지.

## Interaction · Micro-animation · Component behavior

눌림 효과는 즉시 입력에 반응하고 저장 동작을 불필요하게 지연하지 않는다. 공통 시트는 하단 진입/퇴장과 스크림 진행도를 연결하고, 뒤로/바깥 탭/닫기를 같은 상태로 처리한다. 저장 중 닫기와 중복 콜백을 막는다. OS 애니메이션 제한에서는 자동 이동 대신 fade를 사용한다. [모션 수치 및 근거](motion-review.md).

스플래시는 사용자가 바꾼 frame 43→126 구간을 유지한다. 원본 60fps 타임라인 환산 약 1.38초 + 120ms fade다. 실제 기기 FPS를 측정한 결과는 아니다. 목적 화면을 아래에 준비하는 기존 구조를 유지한다.

## 월요일 주간면 데이터 규칙

1. 기존 GET /records/weekly의 end_date를 서비스의 오늘로 사용한다. 기기 날짜로 대체하지 않는다.
2. 해당 날짜의 월~일을 만든다. 서버의 최근 7일에 현재 주 월~오늘이 포함되므로 상태를 사용한다.
3. 미래 FUTURE, 누락된 과거 UNKNOWN, 가입 이전 BEFORE_SIGNUP을 구분한다.
4. 완료한 날만 기존 날짜별 상세 API로 읽는다(최대 7건). 테스트 서비스 날짜와 실제 완료 타임스탬프가 달라도 해당 서비스 날짜에 기록한다. 가짜 완료 시간은 만들지 않는다.
5. 그 주 완료 카드로 재료를 계산한다. 상세 조회 일부 실패 시 부분 합계를 확정하지 않고 재시도를 제공한다.
6. 틈새 운동은 기존 날짜 범위 조회에 월~일을 전달한다. 카드 실천 일수와 합치지 않는다. 404/501 서버에서는 부가 영역을 생략하는 기존 호환 처리를 유지한다.

백엔드 API·스키마 변경은 없다. 일보 조회 시 완료 날짜 상세 GET이 최대 7건 추가된다. 서버 주간 API 자체와 홈·기록 화면의 최근 7일은 그대로다. 경로의 공통 prefix는 기존 Retrofit 설정을 따른다.

## 이미지 검토

beaver_walking.png는 실제로 열어 보니 이전 캐릭터였다. 홈 참조를 기존 투명 RGBA beaver_wave.png로 교체했다. Image 기본 Fit을 사용해 전체 비율을 유지하며 72/84dp로 표시한다.

현재 캐릭터를 기준으로 걷기 포즈 생성·수정을 2회 시도했지만 둘 다 체크무늬 배경이 포함된 RGB 파일이었다. **앱 리소스나 APK에 넣지 않았다.** [시도 기록](asset-review.md).

## 테스트 및 바뀐 파일

추가 JVM 테스트 JournalWeekTest(5), JournalWeekLoadingTest(3)는 각 요일, 월/연도 경계, 미래/가입 이전/누락, 집계, 서비스 날짜, 조회 실패·구서버를 검증한다. 전체 합계 **91개 통과**.

SeptemberReviewUiTest(4), SheetMotionReviewTest(4)를 포함해 기기 테스트 **62개를 실행해 모두 통과**했다. 1차 24개는 동의·월요일·시트·홈·Google 진입·런치를, 2차 38개는 카드·근력 요일·유산소·설정·일보·허리둘레 등의 회귀 시나리오를 검증했다. 실제 API 저장·Google OAuth 성공·실외 센서 정확도를 검증한 결과는 아니다. 실행 목록·한계·로그는 [기기 QA](DEVICE-QA.md)에 기록했다.

- 새 소스: ui/legal/LegalDocument.kt, ui/journal/JournalWeek.kt.
- 보완 소스: ui/common/TmtnSheetDialog.kt, ui/cardhome/CardHomeScreen.kt, ui/journal/JournalState.kt·JournalScreen.kt.
- 가입: OnboardingComponents.kt, OnboardingScreensConsent.kt, OnboardingScreensTerms.kt, OnboardingState.kt.
- 설정: ProfileScreens1.kt·3.kt·6.kt·7.kt, ProfileState.kt, ProfileFlow.kt.
- 테스트: 신규 4개 테스트 클래스와 HomeHierarchyTest.kt.
- 문서: 이 폴더, docs/legal의 8개 MD, docs/기획의 PRD·요구사항·유저플로우·README·개정대조.

실행 로그와 QA APK는 상위 프로젝트 docs/ui-review-2026-09-16에 보관한다. QA APK는 별도 앱 com.tmtn.app.polishqa이며 기존 앱을 덮어쓰는 업데이트 APK가 아니다. 출시 패키지/서명/서버/OAuth 설정을 검증한 배포본으로 취급하지 않는다.
