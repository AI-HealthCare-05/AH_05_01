# 공통 시트·런치 모션 검토

2026-09-16 · emil-design-eng, review-animations의 입력 반응·중단 가능성·축소 모션·타이밍 기준을 현재 Compose에 적용했다.

| Before | After | Why |
| --- | --- | --- |
| 초기 alpha 1, 측정 후 effect 전 최종 위치 노출 가능 | alpha 0, 위치 설정 후 노출; 스크림에도 alpha 반영 | 첫 프레임 점프 방지. TmtnSheetDialog.kt:68, :77, :87 |
| 드래그마다 scope.launch → snapTo | 동기 좌표, release에서 Animatable로 인계 | 대기 중 snap이 spring을 취소하지 않음. :123, :137, :186 |
| 축소 모션 드래그 닫기에서 offset을 화면 아래로 즉시 이동 | 현재 위치에서 160ms fade 후 닫기 | 모션 제한 중 점프 제거. :146 |
| 닫기 시작 때만 저장 중인지 확인 | 완료 직전 재확인, 저장 중이면 복구 | 진행 중 입력/요청 보존. :125 |

## 유지한 수치와 이유

| 대상 | 코드 기준 | 이유 |
| --- | --- | --- |
| 눌림 | 100ms, scale .985, alpha .92 | 작은 접촉 피드백. 키보드/모션 제한 시 scale 생략 |
| 시트 진입 | 320ms, CubicBezier(.32,.72,0,1) | 사용자가 수정한 긴 하단 이동과 급감속 drawer 곡선 유지. 300ms 초과 예외, 실기 체감 확인 필요 |
| 닫기 버튼·뒤로·바깥 탭 | 200ms, CubicBezier(.23,1,.32,1) | 진입보다 빠른 퇴장 후 콜백 1회 |
| 축소 모션 | 160ms opacity | 자동 이동을 줄임. 손가락으로 끈 위치는 반영 |
| 드래그 복귀 | damping 1, stiffness 약 438.65 | 현재 속도에서 복귀 |
| 드래그 퇴장 | damping .85, 동일 stiffness | 플링 속도를 이어 반영. 고정 ms/프레임 아님 |
| 런치 | frame 43→126 / 60 × 1000 ≈ 1383ms, fade 120ms | 불필요한 도입/정지 구간을 줄인 기존 변경 유지 |

ui/theme/TmtnMotion.kt:36, ui/launch/TmtnLaunch.kt:62 기준. 60Hz 환산 시 320ms≈19.2프레임, 200ms≈12프레임, 160ms≈9.6프레임이다. 실측 FPS나 누락률이 아니다.

## Verdict

**Interruptibility & timing:** 첫 프레임 alpha 0, 반복 닫기 콜백 1회, 닫히는 중 저장 시작 시 복구, 실제 손잡이 swipeDown(120ms)로 닫기를 SM-S916N / Android 16에서 자동 검증해 통과했다. 작은 플링·반대 방향 끌기·진입 중 재잡기를 연속 조작하는 수동 체감 검증은 남는다.

**Accessibility:** 축소 모션 드래그 퇴장이 위치를 유지한 채 fade하는 테스트가 통과했다. 회귀 테스트에서는 OS 애니메이션 0 조건도 사용했고 종료 후 원래 1.0으로 복원됐음을 확인했다. 설정 화면을 통한 수동 전환·음성 읽기는 남아 있다.

**Performance:** 이동·투명도는 graphicsLayer에서 처리한다. 매 프레임 패딩·크기를 바꾸지 않는다. 실제 프레임 성능은 미측정이다.

**자동 검증 범위 통과.** 재연결 후 SheetMotionReviewTest 4개와 런치·시트 회귀 테스트가 통과했다. 이전의 기기 미연결 상태는 해소됐다. 전체 기기 테스트는 62개 통과이며 관련 로그는 상위 프로젝트 docs/ui-review-2026-09-16/device-review-first.log에 있다. 이 결과를 실측 FPS, 연속 수동 제스처 체감, 모든 기기에서의 성능 승인으로 확대하지 않는다.
