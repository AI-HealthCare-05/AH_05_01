# 2026-09-16 실기기 검증

재연결 후 별도 QA 앱과 테스트 APK를 설치했다. **기기 테스트 62개 통과, 실패 0**. 기존 com.tmtn.app의 로그인·기록·설치는 변경하지 않았다.

## 환경과 증거

| 항목 | 확인값 |
| --- | --- |
| 기기 | Samsung SM-S916N / Android 16 / API 36 |
| 화면 | 1080 × 2340px, density 450, 기본 폭 384dp |
| 기본 글자 설정 | font_scale 1.0, font_weight_adjustment +300. 사용자 설정 유지 |
| 검증 패키지 | com.tmtn.app.polishqa / 테스트 com.tmtn.app.polishqa.test |
| APK | tmtn-ui-review-2026-09-16-qa.apk |
| SHA256 | F0043F8CC5F94FE3775FECE5493ED17D5C5F1FF4E10DE12ADABC04EE70F01E55 |
| 1차 | 24개 통과 / 23.852초 / device-review-first.log |
| 2차 | 38개 통과 / 42.940초 / device-review-regression.log |

로그·APK·갤러리·PNG는 상위 AH_05_01 프로젝트의 docs/ui-review-2026-09-16에 있다. 갤러리 파일명은 gallery.html이다. 서버 기록 대신 fixture(검증용 데이터)를 실제 앱 컴포넌트에 주입했다. 실제 가입·프로필 저장·기록 삭제 요청을 수행하지 않았다.

1차 클래스: SeptemberReviewUiTest, SheetMotionReviewTest, HomeHierarchyTest, SheetFeedbackUiTest, GoogleEntryUiTest, LaunchUiTest.

2차 클래스: AppFlowPolishTest, JournalIntegrationUiTest, WaistEstimateUiTest.

종료 코드만으로 판정하지 않고 로그의 `OK (24 tests)`, `OK (38 tests)`와 각 테스트 상태를 확인했다. instrumentation의 최종 코드 -1은 이 성공 결과와 함께 반환됐다.

## 확인 결과

| 시나리오 | 결과와 한계 |
| --- | --- |
| 가입 보기 6종 + 처리방침 링크 | 개별 본문, 읽고 돌아와도 체크 보존 통과. 처리방침은 동의 체크와 분리 |
| 필수만 선택 | 선택 알림·위치·분석 없이 다음 버튼 활성화 통과 |
| 설정 → 문서 → 뒤로 | 원래 진입 화면 복귀 통과. 버전 비클릭은 코드 확인 |
| 320dp / 글자 1.8배 | 약관·온보딩·미션·단계 상승 스크롤/버튼 접근 통과. 캡처에서 본문과 버튼 겹침 없음 |
| 2026-09-16 주간면 | 9/14 월~9/20 일, 9/17~20 선택 불가, 9/10 제외 통과. 캡처도 확인 |
| 일요일·월요일·연도 경계, 누락·가입 이전 | JournalWeekTest 단위 검증 통과. 모든 날짜를 기기에서 전수 실행한 것은 아님 |
| 일부 날짜 조회 실패·구서버 | JournalWeekLoadingTest 통과. 운영 서버 장애를 발생시킨 시험은 아님 |
| 시트 첫 프레임·반복 닫기 | 초기 alpha 0, 중복 닫기 콜백 1회 통과 |
| 시트 손잡이 드래그 | 실제 터치 입력 swipeDown 120ms 후 창 닫힘, 콜백 1회 통과 |
| 작은 플링·위로 끌기·진입 중 재잡기 | 연속 수동 체감 미실행. 자동 드래그 1종으로 전체 제스처 품질을 보장하지 않음 |
| 닫히는 중 저장 시작 | 닫기 취소 및 시트 복구 통과. 실제 서버 저장 요청 시험과 구분 |
| 축소 모션 | 드래그 위치를 유지하면서 fade 통과. 종료 후 OS animator_duration_scale 1.0 복원 확인 |
| 홈·카드·A16·유산소·내 댐 | 계층·동작 회귀 통과. 캡처에서 카드 중앙 정렬, 재료·댐 비율, 글자 줄맞춤 확인 |
| 허리둘레 | 표시·실패·재시도·결과 없음·확대 글자 통과. 실제 추정 모델 품질/응답 검증 아님 |
| 런치 | 시스템 전환 대기·완료 1회·축소 모션·레이아웃 자동 검증 통과. FPS/프레임 누락 미측정 |

SeptemberReviewUiTest 캡처는 기기의 굵은 글씨 보정 +300을 반영한다. AppFlowPolishTest/JournalIntegrationUiTest는 앱 내부 FontFamilyResolver만 보정 0으로 구성해 기본 서체를 확인하며 휴대전화 전역 설정을 바꾸지 않는다. 갤러리에 각 조건을 표시한다.

기존 JournalIntegrationUiTest의 임의 7일 fixture는 달력 컴포넌트 회귀용이다. 월요일 변환의 근거는 SeptemberReviewUiTest의 9/14~9/20 캡처와 JournalWeekTest이며, 임의 fixture의 기간을 실제 최신 주간 집계로 제시하지 않는다.

## 남은 통합 검증

- 팀 서버의 동의 저장·철회·문서 버전, 실제 Google OAuth, 실외 센서 정확도 및 백그라운드 복구.
- 실제 사용자 계정으로 가입부터 완료·보상까지 이어지는 전체 여정.
- 반복 제스처 수동 체감, 성능 계측, 음성 읽기, 다른 제조사/OS.
- 약관 보유기간·위탁/국외 이전·시행일·버전 확정. 현재 문서는 운영자 문홍주 / salaam4040@naver.com을 반영한 검토본.

미검증 항목을 통과로 표시하지 않는다. 이번 QA 앱은 원래 앱을 덮어쓰는 업데이트/출시 APK가 아니다.
